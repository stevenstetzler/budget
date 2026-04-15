package com.vidalabs.budget.repo

import com.vidalabs.budget.data.BudgetDao
import com.vidalabs.budget.data.ReceiptEntity
import kotlinx.coroutines.flow.Flow
import com.vidalabs.budget.data.CategoryEntity
import com.vidalabs.budget.data.CategoryTotal
import com.vidalabs.budget.data.SummaryTotals
import com.vidalabs.budget.data.BudgetItemEntity
import com.vidalabs.budget.data.BudgetRow
import com.vidalabs.budget.data.RecurrenceEntity
import com.vidalabs.budget.data.TransactionRow
import com.vidalabs.budget.data.ValidityLookupEntity
import java.time.LocalDate
import java.util.UUID

/** Number of months ahead to pre-populate validity_lookup. */
private const val VALIDITY_LOOKAHEAD_MONTHS = 12L

class BudgetRepository(private val dao: BudgetDao) {

    fun observeCategories(): Flow<List<CategoryEntity>> = dao.observeCategories()

    fun observeCategoriesByRecentUsage(startEpochDay: Long, endEpochDay: Long): Flow<List<CategoryEntity>> =
        dao.observeCategoriesByRecentUsage(startEpochDay, endEpochDay)

    fun observeSummaryTotals(): Flow<SummaryTotals> = dao.observeSummaryTotals()

    fun observeTotalIncomeForRange(startEpochDay: Long, endEpochDay: Long) =
        dao.observeTotalIncomeForRange(startEpochDay, endEpochDay)

    fun observeSpendingByCategoryForRange(startEpochDay: Long, endEpochDay: Long) =
        dao.observeSpendingByCategoryForRange(startEpochDay, endEpochDay)

    fun observeBudgetRowsForMonth(monthKey: Int): Flow<List<BudgetRow>> =
        dao.observeBudgetRowsForMonth(monthKey)

    suspend fun setBudgetValue(categoryUid: String, monthKey: Int, value: Double): BudgetItemEntity {
        val now = System.currentTimeMillis()
        val item = BudgetItemEntity(
            categoryUid = categoryUid,
            monthKey = monthKey,
            value = value.coerceAtLeast(0.0),
            updatedAt = now,
            deleted = false
        )
        dao.upsertBudgetItem(item)
        return item
    }

    fun observeSummaryBudgetRowsForMonth(monthKey: Int, startEpochDay: Long, endEpochDay: Long) =
        dao.observeSummaryBudgetRowsForMonth(monthKey, startEpochDay, endEpochDay)

    suspend fun createCategory(name: String, isPositive: Boolean): CategoryEntity {
        val now = System.currentTimeMillis()
        val trimmed = name.trim()
        val existing = dao.getCategoryByName(trimmed)
        if (existing != null) return existing

        val c = CategoryEntity(
            uid = UUID.randomUUID().toString(),
            name = trimmed,
            isPositive = isPositive,
            updatedAt = now,
            deleted = false
        )
        dao.insertCategory(c) // IGNORE race
        return dao.getCategoryByName(trimmed)!!
    }

    suspend fun addReceipt(epochDay: Long, amountPositive: Double, description: String?, categoryName: String): ReceiptEntity {
        val now = System.currentTimeMillis()
        val cat = dao.getOrCreateCategory(categoryName, isPositiveIfCreate = false)

        val signed = if (cat.isPositive) amountPositive else -amountPositive

        val r = ReceiptEntity(
            uid = UUID.randomUUID().toString(),
            epochDay = epochDay,
            amount = signed,
            description = description,
            categoryUid = cat.uid,
            updatedAt = now,
            deleted = false
        )
        dao.upsertReceipt(r)
        return r
    }

    suspend fun getReceiptByUid(uid: String): ReceiptEntity? = dao.getReceiptByUid(uid)

    suspend fun updateReceipt(
        uid: String,
        epochDay: Long,
        amountPositive: Double,
        description: String?,
        categoryName: String
    ): ReceiptEntity {
        val now = System.currentTimeMillis()
        val cat = dao.getOrCreateCategory(categoryName, isPositiveIfCreate = false)

        val signed = if (cat.isPositive) amountPositive else -amountPositive

        // Preserve recurrenceId from existing receipt
        val existing = dao.getReceiptByUid(uid)
        val r = ReceiptEntity(
            uid = uid,
            epochDay = epochDay,
            amount = signed,
            description = description,
            categoryUid = cat.uid,
            updatedAt = now,
            deleted = false,
            recurrenceId = existing?.recurrenceId
        )
        dao.upsertReceipt(r)
        return r
    }

    suspend fun deleteReceipt(uid: String) {
        val now = System.currentTimeMillis()
        dao.deleteReceipt(uid, now)
    }

    /**
     * Imports a single transaction. If [categoryName] does not exist, a new category is created
     * using [isPositiveCategory] to determine whether it represents income or expense.
     * If the category already exists, its existing [isPositive] value is preserved.
     */
    suspend fun importTransaction(
        epochDay: Long,
        amountPositive: Double,
        description: String?,
        categoryName: String,
        isPositiveCategory: Boolean
    ): ReceiptEntity {
        val now = System.currentTimeMillis()
        val cat = dao.getOrCreateCategory(categoryName, isPositiveIfCreate = isPositiveCategory)
        val signed = if (cat.isPositive) amountPositive else -amountPositive
        val r = ReceiptEntity(
            uid = UUID.randomUUID().toString(),
            epochDay = epochDay,
            amount = signed,
            description = description,
            categoryUid = cat.uid,
            updatedAt = now,
            deleted = false
        )
        dao.upsertReceipt(r)
        return r
    }

    // Summary flows (updated below in DAO)
    fun observeTotalIncome(): Flow<Double> = dao.observeTotalIncome()
    fun observeSpendingByCategory(): Flow<List<CategoryTotal>> = dao.observeSpendingByCategory()

    fun observeReceiptsForCategoryInRange(categoryUid: String, startEpochDay: Long, endEpochDay: Long) =
        dao.observeReceiptsForCategoryInRange(categoryUid, startEpochDay, endEpochDay)

    fun observeAllReceiptsInRange(startEpochDay: Long, endEpochDay: Long) =
        dao.observeAllReceiptsInRange(startEpochDay, endEpochDay)

    suspend fun getAllTransactions(): List<TransactionRow> = dao.getAllTransactions()

    // -------------------------------------------------------------------------
    // Recurrence
    // -------------------------------------------------------------------------

    /**
     * Create or update a recurrence for a receipt. Automatically populates
     * validity_lookup for the next [VALIDITY_LOOKAHEAD_MONTHS] months.
     * When updating an existing recurrence, stale validity_lookup rows that no
     * longer fall within the active range are pruned first.
     */
    suspend fun upsertRecurrence(
        receiptId: String,
        frequency: String,
        startDate: Long,
        endDate: Long?,
        dayOfPeriod: Int,
        existingId: String? = null
    ): RecurrenceEntity {
        // Resolve the recurrence id: use the explicitly-supplied id if present,
        // else look up an existing recurrence for this receipt (so we never create
        // a second row for the same receipt), and only generate a new UUID as a
        // last resort.
        val id = existingId
            ?: dao.getRecurrenceForReceipt(receiptId)?.id
            ?: UUID.randomUUID().toString()
        val rec = RecurrenceEntity(
            id = id,
            receiptId = receiptId,
            frequency = frequency,
            startDate = startDate,
            endDate = endDate,
            dayOfPeriod = dayOfPeriod
        )
        dao.upsertRecurrence(rec)

        // Link receipt → recurrence
        val receipt = dao.getReceiptByUid(receiptId)
        if (receipt != null) {
            dao.upsertReceipt(receipt.copy(recurrenceId = id))
        }

        // When updating an existing recurrence, prune validity_lookup rows that
        // are no longer valid (e.g. outside new startDate/endDate or frequency change)
        if (existingId != null) {
            pruneValidityLookupForRecurrence(rec)
        }

        // Populate any missing validity_lookup rows
        populateValidityLookupForRecurrence(rec)
        return rec
    }

    /**
     * Remove the recurrence (and its validity_lookup entries) from a receipt.
     *
     * Optionally update the receipt's fields at the same time. When [receiptEpochDay],
     * [receiptAmountPositive], and [receiptCategoryName] are all non-null the receipt
     * is updated atomically with the removal, moving it back to its original start date
     * and preserving any user edits. [receiptDescription] is intentionally excluded from
     * that guard because it is genuinely optional on a receipt (null means no description).
     *
     * Operation order: validity_lookup is deleted BEFORE recurrenceId is cleared on
     * the receipt. This prevents a transient state where the receipt would appear in
     * both the regular branch (recurrenceId = null) and the recurring branch
     * (validity_lookup still present), which causes a duplicate-key crash in the
     * observed LazyColumn.
     */
    suspend fun removeRecurrence(
        recurrenceId: String,
        receiptEpochDay: Long? = null,
        receiptAmountPositive: Double? = null,
        receiptDescription: String? = null,
        receiptCategoryName: String? = null,
    ) {
        val rec = dao.getRecurrenceById(recurrenceId) ?: return
        // 1. Delete validity_lookup FIRST — receipt still has recurrenceId set here,
        //    so it won't appear in either UNION branch and there are no duplicates.
        dao.deleteValidityLookupForRecurrence(recurrenceId)
        // 2. Update receipt fields (if provided) and clear recurrenceId atomically.
        val existing = dao.getReceiptByUid(rec.receiptId)
        if (existing != null) {
            val now = System.currentTimeMillis()
            val updatedReceipt = if (
                receiptEpochDay != null &&
                receiptAmountPositive != null &&
                receiptCategoryName != null
            ) {
                val cat = dao.getOrCreateCategory(receiptCategoryName, isPositiveIfCreate = false)
                val signed = if (cat.isPositive) receiptAmountPositive else -receiptAmountPositive
                existing.copy(
                    epochDay = receiptEpochDay,
                    amount = signed,
                    description = receiptDescription,
                    categoryUid = cat.uid,
                    recurrenceId = null,
                    updatedAt = now,
                )
            } else {
                existing.copy(recurrenceId = null, updatedAt = now)
            }
            dao.upsertReceipt(updatedReceipt)
        }
        // 3. Delete the recurrence row itself.
        dao.deleteRecurrence(recurrenceId)
    }

    suspend fun getRecurrenceForReceipt(receiptId: String): RecurrenceEntity? =
        dao.getRecurrenceForReceipt(receiptId)

    /**
     * Toggle whether a recurring receipt appears in a specific month.
     */
    suspend fun setRecurrenceActiveForMonth(
        recurrenceId: String,
        targetMonth: Long,
        isActive: Boolean
    ) {
        // If no entry exists yet, create it first
        val existing = dao.getValidityLookupEntry(recurrenceId, targetMonth)
        if (existing == null) {
            dao.insertValidityLookupIfAbsent(
                ValidityLookupEntity(
                    id = UUID.randomUUID().toString(),
                    recurrenceId = recurrenceId,
                    targetMonth = targetMonth,
                    isActive = isActive
                )
            )
        } else {
            dao.setValidityLookupActive(recurrenceId, targetMonth, isActive)
        }
    }

    /**
     * Returns whether the recurrence is active in the given month.
     * Defaults to true if no entry exists (not yet in lookahead range).
     */
    suspend fun getValidityLookupIsActive(recurrenceId: String, targetMonth: Long): Boolean {
        return dao.getValidityLookupEntry(recurrenceId, targetMonth)?.isActive ?: true
    }

    /**
     * On startup: ensure validity_lookup is populated 12 months ahead
     * for all existing recurrences.
     */
    suspend fun populateValidityLookup() {
        val allRecurrences = dao.getAllRecurrences()
        for (rec in allRecurrences) {
            populateValidityLookupForRecurrence(rec)
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Remove validity_lookup rows for [rec] that are no longer valid.
     *
     * This covers months that fall:
     * - Before the recurrence's startDate.
     * - After the recurrence's endDate (if one is set).
     * - In months where the recurrence no longer has an occurrence (e.g. after a
     *   frequency change).
     *
     * User-set isActive=false overrides for months that ARE still active are left
     * untouched.
     */
    private suspend fun pruneValidityLookupForRecurrence(rec: RecurrenceEntity) {
        val entries = dao.getValidityLookupForRecurrence(rec.id)
        for (entry in entries) {
            val targetDate = LocalDate.ofEpochDay(entry.targetMonth)
            if (!isRecurrenceActiveInMonth(rec, targetDate.year, targetDate.monthValue)) {
                dao.deleteValidityLookupById(entry.id)
            }
        }
    }

    private suspend fun populateValidityLookupForRecurrence(rec: RecurrenceEntity) {
        val today = LocalDate.now()
        val endLookahead = today.plusMonths(VALIDITY_LOOKAHEAD_MONTHS)

        val startLocal = LocalDate.ofEpochDay(rec.startDate)
        var year = startLocal.year
        var month = startLocal.monthValue

        val endYear = endLookahead.year
        val endMonth = endLookahead.monthValue

        while (year < endYear || (year == endYear && month <= endMonth)) {
            if (isRecurrenceActiveInMonth(rec, year, month)) {
                val targetEpochDay = LocalDate.of(year, month, 1).toEpochDay()
                dao.insertValidityLookupIfAbsent(
                    ValidityLookupEntity(
                        id = UUID.randomUUID().toString(),
                        recurrenceId = rec.id,
                        targetMonth = targetEpochDay,
                        isActive = true
                    )
                )
            }
            // Advance month
            month++
            if (month > 12) {
                month = 1
                year++
            }
        }
    }

    private fun isRecurrenceActiveInMonth(rec: RecurrenceEntity, year: Int, month: Int): Boolean {
        val firstDay = LocalDate.of(year, month, 1)
        val nextMonthFirstDay = if (month == 12) LocalDate.of(year + 1, 1, 1)
                                else LocalDate.of(year, month + 1, 1)

        val start = LocalDate.ofEpochDay(rec.startDate)
        val end = rec.endDate?.let { LocalDate.ofEpochDay(it) }

        // Recurrence must have started before end of month
        if (start >= nextMonthFirstDay) return false
        // Recurrence must not have ended before start of month
        if (end != null && end < firstDay) return false

        return when (rec.frequency) {
            "MONTHLY" -> true
            "DAILY" -> true
            "WEEKLY" -> hasOccurrenceInMonth(start, 7, firstDay, nextMonthFirstDay)
            "BI_WEEKLY" -> hasOccurrenceInMonth(start, 14, firstDay, nextMonthFirstDay)
            else -> false
        }
    }

    private fun hasOccurrenceInMonth(
        start: LocalDate,
        intervalDays: Long,
        monthStart: LocalDate,
        monthEndExclusive: LocalDate
    ): Boolean {
        if (start >= monthEndExclusive) return false
        if (start >= monthStart) return true
        val delta = java.time.temporal.ChronoUnit.DAYS.between(start, monthStart)
        val remainder = (delta % intervalDays).toInt()
        val nextOcc = if (remainder == 0) monthStart
                      else monthStart.plusDays((intervalDays - remainder))
        return nextOcc < monthEndExclusive
    }
}
