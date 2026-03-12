package com.vidalabs.budget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidalabs.budget.data.CategoryEntity
import com.vidalabs.budget.data.SummaryTotals
import com.vidalabs.budget.repo.BudgetRepository
import com.vidalabs.budget.data.BudgetRow
import com.vidalabs.budget.data.SummaryBudgetRow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.vidalabs.budget.data.CategoryTotal
import com.vidalabs.budget.sync.SyncEvent
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class EntryUiState(
    val date: LocalDate = LocalDate.now(),
    val category: String = "grocery",
    val amountText: String = "",
    val description: String = "",
    val error: String? = null
)

fun YearMonth.toMonthKey(): Int = this.year * 100 + this.monthValue

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(
    private val repo: BudgetRepository,
    private val sync: com.vidalabs.budget.sync.SyncManager
) : ViewModel() {
    init {
        viewModelScope.launch {
            repo.ensureDefaults()
        }
    }

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    fun setSelectedMonth(m: YearMonth) {
        _selectedMonth.value = m
    }

    fun createCategory(name: String, isPositive: Boolean) {
        viewModelScope.launch {
            val cat = repo.createCategory(name, isPositive)
            _entry.value = _entry.value.copy(category = cat.name, error = null)

            // ✅ push event
            sync.push { eventId, deviceId, seq, ts ->
                SyncEvent.UpsertCategory(
                    eventId = eventId,
                    deviceId = deviceId,
                    seq = seq,
                    ts = ts,
                    uid = cat.uid,
                    name = cat.name,
                    isPositive = cat.isPositive,
                    updatedAt = cat.updatedAt,
                    deleted = cat.deleted
                )
            }
        }
    }


    private val _entry = MutableStateFlow(EntryUiState())
    val entry: StateFlow<EntryUiState> = _entry.asStateFlow()

    val summaryRowsForMonth: StateFlow<List<SummaryBudgetRow>> =
        selectedMonth
            .flatMapLatest { ym ->
                val monthKey = ym.toMonthKey()
                val start = ym.atDay(1).toEpochDay()
                val end = ym.plusMonths(1).atDay(1).toEpochDay()
                repo.observeSummaryBudgetRowsForMonth(monthKey, start, end)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cashFlowForMonth: StateFlow<Double> =
        summaryRowsForMonth
            .map { rows ->
                val income = rows.filter { it.isPositive }.sumOf { it.actual }
                val expenses = rows.filter { !it.isPositive }.sumOf { it.actual }
                income - expenses
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val budgetedCashFlowForMonth: StateFlow<Double> =
        summaryRowsForMonth
            .map { rows ->
                val budgetedIncome = rows.filter { it.isPositive }.sumOf { it.budget }
                val budgetedExpenses = rows.filter { !it.isPositive }.sumOf { it.budget }
                budgetedIncome - budgetedExpenses
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                0.0
            )


    val categories: StateFlow<List<CategoryEntity>> =
        repo.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val summary: StateFlow<SummaryTotals> =
        repo.observeSummaryTotals()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryTotals(0.0, 0.0))

    val totalIncome: StateFlow<Double> =
        repo.observeTotalIncome()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val spendingByCategory: StateFlow<List<CategoryTotal>> =
        repo.observeSpendingByCategory()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    val totalIncomeForMonth: StateFlow<Double> =
        selectedMonth
            .flatMapLatest { ym ->
                val start = ym.atDay(1).toEpochDay()
                val end = ym.plusMonths(1).atDay(1).toEpochDay()
                repo.observeTotalIncomeForRange(start, end)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val spendingByCategoryForMonth: StateFlow<List<com.vidalabs.budget.data.CategoryTotal>> =
        selectedMonth
            .flatMapLatest { ym ->
                val start = ym.atDay(1).toEpochDay()
                val end = ym.plusMonths(1).atDay(1).toEpochDay()
                repo.observeSpendingByCategoryForRange(start, end)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val budgetRowsForMonth: StateFlow<List<BudgetRow>> =
        selectedMonth
            .flatMapLatest { ym ->
                val monthKey = ym.toMonthKey()
                val prevMonthKey = ym.minusMonths(1).toMonthKey()
                repo.observeBudgetRowsForMonth(monthKey, prevMonthKey)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setBudgetValue(categoryUid: String, valuePositive: Double) {
        val v = valuePositive.coerceAtLeast(0.0)
        viewModelScope.launch {
            val item = repo.setBudgetValue(categoryUid, _selectedMonth.value.toMonthKey(), v)

            // ✅ push event
            sync.push { eventId, deviceId, seq, ts ->
                SyncEvent.UpsertBudgetItem(
                    eventId = eventId,
                    deviceId = deviceId,
                    seq = seq,
                    ts = ts,
                    categoryUid = item.categoryUid,
                    monthKey = item.monthKey,
                    value = item.value,
                    updatedAt = item.updatedAt,
                    deleted = item.deleted
                )
            }

        }
    }



    fun setDate(d: LocalDate) { _entry.value = _entry.value.copy(date = d) }
    fun setCategory(c: String) { _entry.value = _entry.value.copy(category = c) }
    fun setAmountText(t: String) { _entry.value = _entry.value.copy(amountText = t) }
    fun setDescription(t: String) { _entry.value = _entry.value.copy(description = t) }

    fun add() {
        val s = _entry.value

        val amount = parseAmount(s.amountText)
        if (amount == null ) {
            _entry.value = s.copy(error = "Enter a number (e.g. 12.34).")
            return
        }

        if (s.category.isBlank()) {
            _entry.value = s.copy(error = "Pick a category.")
            return
        }

        viewModelScope.launch {
            val receipt = repo.addReceipt(
                epochDay = s.date.toEpochDay(),
                amountPositive = amount,
                description = s.description.trim().takeIf { it.isNotEmpty() },
                categoryName = s.category
            )

            // ✅ push event
            sync.push { eventId, deviceId, seq, ts ->
                SyncEvent.UpsertReceipt(
                    eventId = eventId,
                    deviceId = deviceId,
                    seq = seq,
                    ts = ts,
                    uid = receipt.uid,
                    epochDay = receipt.epochDay,
                    amount = receipt.amount,
                    description = receipt.description,
                    categoryUid = receipt.categoryUid,
                    updatedAt = receipt.updatedAt,
                    deleted = receipt.deleted
                )
            }

            _entry.value = s.copy(amountText = "", description = "", error = null)
        }

    }

//    val outboxCount = repo.observeOutboxCount()
//        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _expandedSummaryCategoryUid = MutableStateFlow<String?>(null)
    val expandedSummaryCategoryUid: StateFlow<String?> = _expandedSummaryCategoryUid.asStateFlow()

    fun toggleExpandedSummaryCategory(uid: String) {
        _expandedSummaryCategoryUid.value =
            if (_expandedSummaryCategoryUid.value == uid) null else uid
    }

    val receiptsForExpandedCategory: StateFlow<List<com.vidalabs.budget.data.ReceiptRow>> =
        combine(selectedMonth, expandedSummaryCategoryUid) { ym, catUid ->
            ym to catUid
        }
            .flatMapLatest { (ym, catUid) ->
                if (catUid == null) flowOf(emptyList())
                else {
                    val start = ym.atDay(1).toEpochDay()
                    val end = ym.plusMonths(1).atDay(1).toEpochDay()
                    repo.observeReceiptsForCategoryInRange(catUid, start, end)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTransactionsForMonth: StateFlow<List<com.vidalabs.budget.data.TransactionRow>> =
        selectedMonth
            .flatMapLatest { ym ->
                val start = ym.atDay(1).toEpochDay()
                val end = ym.plusMonths(1).atDay(1).toEpochDay()
                repo.observeAllReceiptsInRange(start, end)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateReceipt(
        uid: String,
        epochDay: Long,
        amountPositive: Double,
        description: String?,
        categoryName: String
    ) {
        viewModelScope.launch {
            val receipt = repo.updateReceipt(
                uid = uid,
                epochDay = epochDay,
                amountPositive = amountPositive,
                description = description,
                categoryName = categoryName
            )

            // ✅ push event
            sync.push { eventId, deviceId, seq, ts ->
                SyncEvent.UpsertReceipt(
                    eventId = eventId,
                    deviceId = deviceId,
                    seq = seq,
                    ts = ts,
                    uid = receipt.uid,
                    epochDay = receipt.epochDay,
                    amount = receipt.amount,
                    description = receipt.description,
                    categoryUid = receipt.categoryUid,
                    updatedAt = receipt.updatedAt,
                    deleted = receipt.deleted
                )
            }
        }
    }

    fun deleteReceipt(uid: String) {
        viewModelScope.launch {
            val receipt = repo.getReceiptByUid(uid)
            if (receipt != null) {
                repo.deleteReceipt(uid)

                // ✅ push event
                sync.push { eventId, deviceId, seq, ts ->
                    SyncEvent.UpsertReceipt(
                        eventId = eventId,
                        deviceId = deviceId,
                        seq = seq,
                        ts = ts,
                        uid = receipt.uid,
                        epochDay = receipt.epochDay,
                        amount = receipt.amount,
                        description = receipt.description,
                        categoryUid = receipt.categoryUid,
                        updatedAt = System.currentTimeMillis(),
                        deleted = true
                    )
                }
            }
        }
    }

    // Accepts: "12", "12.3", "12.34", "-5", "-5.00"
    private fun parseAmount(input: String): Double? {
        val t = input.trim()
        if (t.isEmpty()) return null
        if (!Regex("""^-?\d+(\.\d{0,2})?$""").matches(t)) return null
        return t.toDoubleOrNull()
    }
}
