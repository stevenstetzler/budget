package com.vidalabs.budget.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        ReceiptEntity::class,
        BudgetItemEntity::class,
        AppliedEventEntity::class,
        OutboxEventEntity::class
    ],
    version = 11, // bump (any higher number is fine)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): BudgetDao
    abstract fun syncDao(): SyncDao
}

