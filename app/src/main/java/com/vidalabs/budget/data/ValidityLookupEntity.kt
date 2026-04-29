package com.vidalabs.budget.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "validity_lookup",
    indices = [
        Index("recurrenceId"),
        Index("targetMonth"),
        Index(value = ["recurrenceId", "targetMonth"], unique = true)
    ]
)
data class ValidityLookupEntity(
    @PrimaryKey val id: String,            // UUID
    val recurrenceId: String,              // FK to recurrence.id
    val targetMonth: Long,                 // epochDay of first day of month
    val isActive: Boolean = true
)
