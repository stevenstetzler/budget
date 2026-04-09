package com.vidalabs.budget.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "receipts",
    indices = [
        Index(value = ["uid"], unique = true),
        Index("epochDay"),
        Index("categoryUid"),
        Index("recurrenceId")
    ]
)
data class ReceiptEntity(
    @PrimaryKey val uid: String,    // UUID
    val epochDay: Long,
    val amount: Double,             // signed (income +, spending -)
    val description: String?,
    val categoryUid: String,         // FK-by-uid (not enforced by Room unless you add FK)
    val updatedAt: Long,
    val deleted: Boolean = false,
    val recurrenceId: String? = null // FK to recurrence.id; null = not recurring
)
