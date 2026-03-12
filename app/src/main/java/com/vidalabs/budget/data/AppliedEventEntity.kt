package com.vidalabs.budget.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "applied_events")
data class AppliedEventEntity(
    @PrimaryKey val eventId: String,
    val ts: Long
)
