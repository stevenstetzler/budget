package com.vidalabs.budget.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class SyncEvent {
    abstract val eventId: String        // deviceId:seq
    abstract val deviceId: String
    abstract val seq: Long
    abstract val ts: Long               // epoch millis

    @Serializable
    @SerialName("UpsertCategory")
    data class UpsertCategory(
        override val eventId: String,
        override val deviceId: String,
        override val seq: Long,
        override val ts: Long,
        val uid: String,
        val name: String,
        val isPositive: Boolean,
        val updatedAt: Long,
        val deleted: Boolean = false
    ) : SyncEvent()

    @Serializable
    @SerialName("UpsertReceipt")
    data class UpsertReceipt(
        override val eventId: String,
        override val deviceId: String,
        override val seq: Long,
        override val ts: Long,
        val uid: String,
        val epochDay: Long,
        val amount: Double,             // signed
        val description: String? = null,
        val categoryUid: String,
        val updatedAt: Long,
        val deleted: Boolean = false
    ) : SyncEvent()

    @Serializable
    @SerialName("UpsertBudgetItem")
    data class UpsertBudgetItem(
        override val eventId: String,
        override val deviceId: String,
        override val seq: Long,
        override val ts: Long,
        val categoryUid: String,
        val monthKey: Int,              // YYYYMM
        val value: Double,              // positive
        val updatedAt: Long,
        val deleted: Boolean = false
    ) : SyncEvent()
}


sealed class EventType(val type: String) {
    object UpsertCategory : EventType("UpsertCategory")
    object UpsertReceipt : EventType("UpsertReceipt")
    object UpsertBudgetItem : EventType("UpsertBudgetItem")
}

data class BaseEvent(
    val eventId: String,
    val deviceId: String,
    val seq: Long,
    val ts: Long,
    val type: String,
    val payload: Map<String, Any?> // V1: simple; we’ll parse by type
)

interface RemoteEventStore {
    suspend fun listEventFiles(): List<String> // returns paths/ids
    suspend fun readEventFile(id: String): String
    suspend fun writeEventFile(id: String, content: String)
}

class FileEventStore(private val rootDir: java.io.File) : RemoteEventStore {
    private val eventsDir = java.io.File(rootDir, "events")

    override suspend fun listEventFiles(): List<String> {
        if (!eventsDir.exists()) return emptyList()
        return eventsDir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() == "json" }
            .map { it.absolutePath }
            .sorted()
            .toList()
    }

    override suspend fun readEventFile(id: String): String =
        java.io.File(id).readText()

    override suspend fun writeEventFile(id: String, content: String) {
        val f = java.io.File(id)
        f.parentFile?.mkdirs()
        f.writeText(content)
    }

    private fun shouldApply(currentUpdatedAt: Long?, incomingUpdatedAt: Long, incomingDeviceId: String): Boolean {
        if (currentUpdatedAt == null) return true
        if (incomingUpdatedAt > currentUpdatedAt) return true
        // tie-breaker: if equal timestamps, you can apply only if deviceId “wins”
        return false
    }
}
