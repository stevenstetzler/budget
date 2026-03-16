package com.vidalabs.budget.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID


@Dao
interface BudgetDao {
    @Query(
        """
    SELECT COALESCE(SUM(r.amount), 0)
    FROM receipts r
    JOIN categories c ON c.uid = r.categoryUid
    WHERE c.deleted = 0
      AND r.deleted = 0
      AND c.isPositive = 1
    """
    )
    fun observeTotalIncome(): Flow<Double>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudgetItem(item: BudgetItemEntity)

    @Query(
        """
    SELECT
        c.uid AS categoryUid,
        c.name AS name,
        c.isPositive AS isPositive,
        COALESCE(cur.value, prev.value, 0) AS value
    FROM categories c
    LEFT JOIN budgetitems cur
        ON cur.categoryUid = c.uid AND cur.monthKey = :monthKey AND cur.deleted = 0
    LEFT JOIN budgetitems prev
        ON prev.categoryUid = c.uid AND prev.monthKey = :prevMonthKey AND prev.deleted = 0
    WHERE c.deleted = 0
    ORDER BY c.isPositive DESC, c.name COLLATE NOCASE ASC
    """
    )
    fun observeBudgetRowsForMonth(
        monthKey: Int,
        prevMonthKey: Int
    ): Flow<List<BudgetRow>>


    @Query(
        """
    SELECT
        c.uid AS categoryUid,
        c.name AS name,
        c.isPositive AS isPositive,
        COALESCE(b.value, 0) AS budget,
        CASE
            WHEN c.isPositive = 1 THEN COALESCE(SUM(r.amount), 0)
            ELSE COALESCE(SUM(-r.amount), 0)
        END AS actual
    FROM categories c
    LEFT JOIN receipts r
        ON r.categoryUid = c.uid
       AND r.deleted = 0
       AND r.epochDay >= :startEpochDay
       AND r.epochDay < :endEpochDay
    LEFT JOIN budgetitems b
        ON b.categoryUid = c.uid
       AND b.monthKey = :monthKey
       AND b.deleted = 0
    WHERE c.deleted = 0
    GROUP BY c.uid, c.name, c.isPositive, b.value
    ORDER BY c.isPositive DESC, c.name COLLATE NOCASE ASC
    """
    )
    fun observeSummaryBudgetRowsForMonth(
        monthKey: Int,
        startEpochDay: Long,
        endEpochDay: Long
    ): Flow<List<SummaryBudgetRow>>

    @Query(
        """
    SELECT c.name AS name,
           COALESCE(SUM(-r.amount), 0) AS total
    FROM receipts r
    JOIN categories c ON c.uid = r.categoryUid
    WHERE c.deleted = 0
      AND r.deleted = 0
      AND c.isPositive = 0
    GROUP BY r.categoryUid
    HAVING total > 0
    ORDER BY total DESC, c.name COLLATE NOCASE ASC
    """
    )
    fun observeSpendingByCategory(): Flow<List<CategoryTotal>>


    @Query(
        """
    SELECT COALESCE(SUM(r.amount), 0)
    FROM receipts r
    JOIN categories c ON c.uid = r.categoryUid
    WHERE c.deleted = 0
      AND r.deleted = 0
      AND c.isPositive = 1
      AND r.epochDay >= :startEpochDay
      AND r.epochDay < :endEpochDay
    """
    )
    fun observeTotalIncomeForRange(startEpochDay: Long, endEpochDay: Long): Flow<Double>

    @Query(
        """
    SELECT c.name AS name,
           COALESCE(SUM(-r.amount), 0) AS total
    FROM receipts r
    JOIN categories c ON c.uid = r.categoryUid
    WHERE c.deleted = 0
      AND r.deleted = 0
      AND c.isPositive = 0
      AND r.epochDay >= :startEpochDay
      AND r.epochDay < :endEpochDay
    GROUP BY r.categoryUid
    HAVING total > 0
    ORDER BY total DESC, c.name COLLATE NOCASE ASC
    """
    )
    fun observeSpendingByCategoryForRange(
        startEpochDay: Long,
        endEpochDay: Long
    ): Flow<List<CategoryTotal>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long


    @Query("SELECT * FROM categories WHERE name = :name AND deleted = 0 LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Transaction
    suspend fun ensureDefaultCategories(defaults: List<com.vidalabs.budget.data.DefaultCategory>) {
        if (categoryCount() > 0) return

        val now = System.currentTimeMillis()
        defaults.forEach { d ->
            insertCategory(
                CategoryEntity(
                    uid = UUID.randomUUID().toString(),
                    name = d.name.trim(),
                    isPositive = d.isPositive,
                    updatedAt = now,
                    deleted = false
                )
            )
        }
    }


    // --- Categories
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY isPositive DESC, name COLLATE NOCASE ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query(
        """
    SELECT c.uid, c.name, c.isPositive, c.updatedAt, c.deleted
    FROM categories c
    LEFT JOIN receipts r
        ON r.categoryUid = c.uid
       AND r.deleted = 0
       AND r.epochDay >= :startEpochDay
       AND r.epochDay < :endEpochDay
    WHERE c.deleted = 0
    GROUP BY c.uid, c.name, c.isPositive, c.updatedAt, c.deleted
    ORDER BY COUNT(r.uid) DESC, c.name COLLATE NOCASE ASC
    """
    )
    fun observeCategoriesByRecentUsage(
        startEpochDay: Long,
        endEpochDay: Long
    ): Flow<List<CategoryEntity>>

    // --- Receipts
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(receipt: ReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReceipt(r: ReceiptEntity)

    @Query("SELECT * FROM receipts WHERE uid = :uid LIMIT 1")
    suspend fun getReceiptByUid(uid: String): ReceiptEntity?

    @Query("UPDATE receipts SET deleted = 1, updatedAt = :updatedAt WHERE uid = :uid")
    suspend fun deleteReceipt(uid: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(c: CategoryEntity)


    @Query(
        """
    SELECT
        COALESCE(SUM(CASE WHEN c.isPositive = 1 THEN r.amount ELSE 0 END), 0) AS income,
        COALESCE(SUM(CASE WHEN c.isPositive = 0 THEN -r.amount ELSE 0 END), 0) AS spending
    FROM receipts r
    JOIN categories c ON c.uid = r.categoryUid
    WHERE c.deleted = 0
      AND r.deleted = 0
    """
    )
    fun observeSummaryTotals(): Flow<SummaryTotals>

    /**
     * Helper: ensures category exists (by name) and returns its id.
     */
    @Transaction
    suspend fun getOrCreateCategory(name: String, isPositiveIfCreate: Boolean): CategoryEntity {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())

        val existing = getCategoryByName(trimmed)
        if (existing != null) return existing

        val now = System.currentTimeMillis()
        val created = CategoryEntity(
            uid = UUID.randomUUID().toString(),
            name = trimmed,
            isPositive = isPositiveIfCreate,
            updatedAt = now,
            deleted = false
        )

        // IGNORE if another thread/device inserted same name concurrently
        insertCategory(created)

        // Fetch the canonical row (either ours or the one that won the race)
        return getCategoryByName(trimmed)!!
    }

    @Query(
        """
    SELECT
        r.uid AS uid,
        r.epochDay AS epochDay,
        r.amount AS amount,
        r.description AS description
    FROM receipts r
    WHERE r.deleted = 0
      AND r.categoryUid = :categoryUid
      AND r.epochDay >= :startEpochDay
      AND r.epochDay < :endEpochDay
    ORDER BY r.epochDay DESC, r.updatedAt DESC
    """
    )
    fun observeReceiptsForCategoryInRange(
        categoryUid: String,
        startEpochDay: Long,
        endEpochDay: Long
    ): Flow<List<ReceiptRow>>

    @Query(
        """
    SELECT
        r.uid AS uid,
        r.epochDay AS epochDay,
        r.amount AS amount,
        r.description AS description,
        c.name AS categoryName,
        c.isPositive AS isPositive
    FROM receipts r
    JOIN categories c ON c.uid = r.categoryUid
    WHERE r.deleted = 0
      AND c.deleted = 0
      AND r.epochDay >= :startEpochDay
      AND r.epochDay < :endEpochDay
    ORDER BY r.epochDay DESC, r.updatedAt DESC
    """
    )
    fun observeAllReceiptsInRange(
        startEpochDay: Long,
        endEpochDay: Long
    ): Flow<List<TransactionRow>>

    @Query(
        """
    SELECT
        r.uid AS uid,
        r.epochDay AS epochDay,
        r.amount AS amount,
        r.description AS description,
        c.name AS categoryName,
        c.isPositive AS isPositive
    FROM receipts r
    JOIN categories c ON c.uid = r.categoryUid
    WHERE r.deleted = 0
      AND c.deleted = 0
    ORDER BY r.epochDay DESC, r.updatedAt DESC
    """
    )
    suspend fun getAllTransactions(): List<TransactionRow>

}
