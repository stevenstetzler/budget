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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import com.vidalabs.budget.data.CategoryTotal
import com.vidalabs.budget.sync.SyncEvent
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.vidalabs.budget.data.TransactionRow

data class EntryUiState(
    val date: LocalDate = LocalDate.now(),
    val category: String = "",
    val amountText: String = "",
    val description: String = "",
    val error: String? = null
)

data class ImportResult(
    val imported: Int,
    val errors: List<String>
)

data class ExportResult(
    val exported: Int,
    val error: String? = null
)

private data class ImportRecord(
    val date: LocalDate,
    val category: String,
    val isPositive: Boolean,
    val amount: Double,
    val description: String?
)

fun YearMonth.toMonthKey(): Int = this.year * 100 + this.monthValue

enum class ReceiptsSearchRange(val label: String, val totalMonthsIncludingCurrent: Int?) {
    CURRENT_MONTH("Current month", 1),
    LAST_3_MONTHS("Last 3 months", 3),
    LAST_YEAR("Last year", 12),
    ALL_TIME("All time", null)
}

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(
    private val repo: BudgetRepository,
    private val sync: com.vidalabs.budget.sync.SyncManager
) : ViewModel() {
    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    val selectedMonth: StateFlow<YearMonth> = _selectedMonth.asStateFlow()

    fun setSelectedMonth(m: YearMonth) {
        _selectedMonth.value = m
    }

    private val _receiptsSearchRange = MutableStateFlow(ReceiptsSearchRange.CURRENT_MONTH)
    val receiptsSearchRange: StateFlow<ReceiptsSearchRange> = _receiptsSearchRange.asStateFlow()

    fun setReceiptsSearchRange(range: ReceiptsSearchRange) {
        _receiptsSearchRange.value = range
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


    val categories: StateFlow<List<CategoryEntity>> = flow {
        val today = LocalDate.now()
        val startEpochDay = today.minusDays(30).toEpochDay()
        val endEpochDay = today.plusDays(1).toEpochDay()
        emitAll(repo.observeCategoriesByRecentUsage(startEpochDay, endEpochDay))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                repo.observeBudgetRowsForMonth(monthKey)
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

    val allTransactionsForRange: StateFlow<List<com.vidalabs.budget.data.TransactionRow>> =
        receiptsSearchRange
            .flatMapLatest { range ->
                val currentMonth = YearMonth.now()
                when (range) {
                    ReceiptsSearchRange.ALL_TIME -> repo.observeAllTransactions()
                    ReceiptsSearchRange.CURRENT_MONTH -> {
                        val start = currentMonth.atDay(1).toEpochDay()
                        val end = currentMonth.plusMonths(1).atDay(1).toEpochDay()
                        repo.observeAllReceiptsInRange(start, end)
                    }
                    else -> {
                        val months = range.totalMonthsIncludingCurrent ?: 1
                        val startMonth = currentMonth.minusMonths((months - 1).toLong())
                        val start = startMonth.atDay(1).toEpochDay()
                        repo.observeAllReceiptsInRange(start, Long.MAX_VALUE)
                    }
                }
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

    // --- Import ---

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    fun dismissImportResult() {
        _importResult.value = null
    }

    fun importTransactions(content: String, isJson: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runImport(content, isJson)
            withContext(Dispatchers.Main) {
                _importResult.value = result
            }
        }
    }

    private suspend fun runImport(content: String, isJson: Boolean): ImportResult {
        val (records, parseErrors) = try {
            if (isJson) parseImportJson(content) else parseImportCsv(content)
        } catch (e: Exception) {
            return ImportResult(0, listOf("Parse error: ${e.message}"))
        }

        var imported = 0
        val errors = mutableListOf<String>()
        errors.addAll(parseErrors)

        for ((i, record) in records.withIndex()) {
            try {
                val receipt = repo.importTransaction(
                    epochDay = record.date.toEpochDay(),
                    amountPositive = record.amount,
                    description = record.description,
                    categoryName = record.category,
                    isPositiveCategory = record.isPositive
                )
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
                imported++
            } catch (e: Exception) {
                errors.add("Row ${i + 1}: ${e.message}")
            }
        }

        return ImportResult(imported, errors)
    }

    private val importJson = Json { ignoreUnknownKeys = true }

    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,           // yyyy-MM-dd
        DateTimeFormatter.ofPattern("M/d/yyyy"),    // 3/1/2026 or 12/31/2026
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),  // 03/01/2026
        DateTimeFormatter.ofPattern("M-d-yyyy"),    // 3-1-2026
    )

    // Datetime formats tried when no date-only format matches; time part is discarded.
    private val dateTimeFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,                          // yyyy-MM-dd'T'HH:mm:ss[.SSS]
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSS"),      // HH:MM:SS.sssss
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),        // HH:MM:SS.sss
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),            // HH:MM:SS
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),               // HH:MM
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH"),                  // HH
        DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss.SSSSS"),
        DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("M/d/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("M/d/yyyy HH"),
    )

    private fun parseDate(value: String): LocalDate {
        val trimmed = value.trim()
        for (fmt in dateFormats) {
            try {
                return LocalDate.parse(trimmed, fmt)
            } catch (_: DateTimeParseException) { }
        }
        for (fmt in dateTimeFormats) {
            try {
                return LocalDateTime.parse(trimmed, fmt).toLocalDate()
            } catch (_: DateTimeParseException) { }
        }
        throw IllegalArgumentException("Unrecognized date format: '$trimmed'. Expected formats: yyyy-MM-dd, M/d/yyyy, or a datetime variant")
    }

    private fun parseImportJson(content: String): Pair<List<ImportRecord>, List<String>> {
        val trimmed = content.trim()
        val rawRecords = if (trimmed.startsWith("[")) {
            importJson.decodeFromString<List<JsonImportRecord>>(trimmed)
        } else {
            listOf(importJson.decodeFromString<JsonImportRecord>(trimmed))
        }
        val records = mutableListOf<ImportRecord>()
        val errors = mutableListOf<String>()
        rawRecords.forEachIndexed { i, r ->
            try {
                records.add(
                    ImportRecord(
                        date = parseDate(r.date),
                        category = r.category.trim(),
                        isPositive = r.isPositive,
                        amount = r.amount,
                        description = r.description?.trim()?.takeIf { it.isNotEmpty() }
                    )
                )
            } catch (e: Exception) {
                errors.add("JSON record ${i + 1}: ${e.message}")
            }
        }
        return records to errors
    }

    private fun parseImportCsv(content: String): Pair<List<ImportRecord>, List<String>> {
        val lines = content.trim().lines()
        if (lines.isEmpty()) return emptyList<ImportRecord>() to emptyList()

        val header = splitCsvLine(lines[0]).map { it.lowercase() }
        val dateIdx = header.indexOf("date")
        val categoryIdx = header.indexOf("category")
        val amountIdx = header.indexOf("amount")
        val descriptionIdx = header.indexOf("description")
        val isPositiveIdx = header.indexOf("ispositive")

        if (dateIdx < 0 || categoryIdx < 0 || amountIdx < 0 || isPositiveIdx < 0) {
            throw IllegalArgumentException("CSV missing required columns: date, category, amount, isPositive")
        }

        val records = mutableListOf<ImportRecord>()
        val errors = mutableListOf<String>()
        lines.drop(1).filter { it.isNotBlank() }.forEachIndexed { i, line ->
            val parts = splitCsvLine(line)
            try {
                val date = parseDate(parts.getOrElse(dateIdx) { "" })
                val category = parts.getOrElse(categoryIdx) { "" }
                    .takeIf { it.isNotBlank() } ?: throw IllegalArgumentException("empty category")
                val isPositive = parts.getOrElse(isPositiveIdx) { "" }
                    .trim().lowercase() == "true"
                val amountText = parts.getOrElse(amountIdx) { "" }
                val csvAmount = parseAmount(amountText)
                    ?: throw IllegalArgumentException("invalid amount: '$amountText'")
                val amount = csvAmount
                val description = if (descriptionIdx >= 0) {
                    parts.getOrNull(descriptionIdx)?.takeIf { it.isNotBlank() }
                } else null
                records.add(
                    ImportRecord(
                        date = date,
                        category = category,
                        isPositive = isPositive,
                        amount = amount,
                        description = description
                    )
                )
            } catch (e: Exception) {
                errors.add("CSV row ${i + 2}: ${e.message}")
            }
        }
        return records to errors
    }

    /** Splits a single CSV line respecting double-quoted fields. */
    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(c)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    // --- Export ---

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    fun dismissExportResult() {
        _exportResult.value = null
    }

    fun setExportResult(result: ExportResult) {
        _exportResult.value = result
    }

    suspend fun buildExportContent(isJson: Boolean): Pair<String, Int> = withContext(Dispatchers.IO) {
        val transactions = repo.getAllTransactions()
        val content = if (isJson) buildTransactionJson(transactions) else buildTransactionCsv(transactions)
        content to transactions.size
    }
}
