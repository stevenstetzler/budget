package com.vidalabs.budget.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbox_events",
    indices = [Index(value = ["eventId"], unique = true)]
)
data class OutboxEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val eventId: String,      // deviceId:seq (unique)
    val monthDir: String,     // e.g. "2026-01"
    val filename: String,     // e.g. pixel7_2026-01-04T..._000001.json
    val json: String,         // serialized SyncEvent
    val ts: Long              // event timestamp
)
