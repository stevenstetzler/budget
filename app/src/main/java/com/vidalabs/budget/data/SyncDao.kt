package com.vidalabs.budget.data

import androidx.room.*

@Dao
interface SyncDao {
    @Query("SELECT COUNT(*) FROM applied_events WHERE eventId = :eventId")
    suspend fun hasEvent(eventId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markApplied(e: AppliedEventEntity)

    // LWW helpers
    @Query("SELECT updatedAt FROM categories WHERE uid = :uid")
    suspend fun getCategoryUpdatedAt(uid: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(e: CategoryEntity)

    @Query("SELECT updatedAt FROM receipts WHERE uid = :uid")
    suspend fun getReceiptUpdatedAt(uid: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReceipt(e: ReceiptEntity)

    @Query("SELECT updatedAt FROM budgetitems WHERE categoryUid = :categoryUid AND monthKey = :monthKey")
    suspend fun getBudgetItemUpdatedAt(categoryUid: String, monthKey: Int): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudgetItem(e: BudgetItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueOutbox(e: OutboxEventEntity): Long

    @Query("SELECT * FROM outbox_events ORDER BY id ASC")
    suspend fun listOutbox(): List<OutboxEventEntity>

    @Query("DELETE FROM outbox_events WHERE id = :id")
    suspend fun deleteOutboxById(id: Long)

    @Query("SELECT COUNT(*) FROM outbox_events")
    suspend fun outboxCount(): Int

    @Query("SELECT COUNT(*) FROM outbox_events")
    fun observeOutboxCount(): kotlinx.coroutines.flow.Flow<Int>
}
