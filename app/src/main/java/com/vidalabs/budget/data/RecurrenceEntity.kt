package com.vidalabs.budget.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurrence",
    indices = [
        Index("receiptId")
    ]
)
data class RecurrenceEntity(
    @PrimaryKey val id: String,           // UUID
    val receiptId: String,                // FK to receipts.uid
    val frequency: String,                // DAILY, WEEKLY, BI_WEEKLY, MONTHLY
    val startDate: Long,                  // epochDay of first occurrence
    val endDate: Long?,                   // epochDay of last occurrence; null = ongoing
    val dayOfPeriod: Int                  // day within the period (e.g., day of month for MONTHLY)
)
