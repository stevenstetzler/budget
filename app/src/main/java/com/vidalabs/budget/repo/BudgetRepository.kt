package com.vidalabs.budget.repo

import com.vidalabs.budget.data.BudgetDao
import com.vidalabs.budget.data.DEFAULT_CATEGORIES
import com.vidalabs.budget.data.ReceiptEntity
import kotlinx.coroutines.flow.Flow
import com.vidalabs.budget.data.CategoryEntity
import com.vidalabs.budget.data.CategoryTotal
import com.vidalabs.budget.data.SummaryTotals
import com.vidalabs.budget.data.BudgetItemEntity
import com.vidalabs.budget.data.BudgetRow
import com.vidalabs.budget.data.TransactionRow
import java.util.UUID

class BudgetRepository(private val dao: BudgetDao) {

    fun observeCategories(): Flow<List<CategoryEntity>> = dao.observeCategories()

    fun observeCategoriesByRecentUsage(startEpochDay: Long, endEpochDay: Long): Flow<List<CategoryEntity>> =
        dao.observeCategoriesByRecentUsage(startEpochDay, endEpochDay)

    fun observeSummaryTotals(): Flow<SummaryTotals> = dao.observeSummaryTotals()

    fun observeTotalIncomeForRange(startEpochDay: Long, endEpochDay: Long) =
        dao.observeTotalIncomeForRange(startEpochDay, endEpochDay)

    fun observeSpendingByCategoryForRange(startEpochDay: Long, endEpochDay: Long) =
        dao.observeSpendingByCategoryForRange(startEpochDay, endEpochDay)

    fun observeBudgetRowsForMonth(monthKey: Int, prevMonthKey: Int): Flow<List<BudgetRow>> =
        dao.observeBudgetRowsForMonth(monthKey, prevMonthKey)

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

    suspend fun ensureDefaults() {
        dao.ensureDefaultCategories(DEFAULT_CATEGORIES)
    }

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

        val r = ReceiptEntity(
            uid = uid,
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

}
