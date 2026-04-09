package com.vidalabs.budget.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Add recurrenceId column to receipts (nullable)
        db.execSQL(
            "ALTER TABLE receipts ADD COLUMN recurrenceId TEXT DEFAULT NULL"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_receipts_recurrenceId ON receipts(recurrenceId)"
        )

        // 2. Create recurrence table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS recurrence (
                id TEXT NOT NULL PRIMARY KEY,
                receiptId TEXT NOT NULL,
                frequency TEXT NOT NULL,
                startDate INTEGER NOT NULL,
                endDate INTEGER,
                dayOfPeriod INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_recurrence_receiptId ON recurrence(receiptId)"
        )

        // 3. Create validity_lookup table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS validity_lookup (
                id TEXT NOT NULL PRIMARY KEY,
                recurrenceId TEXT NOT NULL,
                targetMonth INTEGER NOT NULL,
                isActive INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_validity_lookup_recurrenceId ON validity_lookup(recurrenceId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_validity_lookup_targetMonth ON validity_lookup(targetMonth)"
        )
    }
}

@Database(
    entities = [
        CategoryEntity::class,
        ReceiptEntity::class,
        BudgetItemEntity::class,
        AppliedEventEntity::class,
        OutboxEventEntity::class,
        RecurrenceEntity::class,
        ValidityLookupEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): BudgetDao
    abstract fun syncDao(): SyncDao
}

