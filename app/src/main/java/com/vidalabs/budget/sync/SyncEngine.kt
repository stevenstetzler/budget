package com.vidalabs.budget.sync

import com.vidalabs.budget.data.*
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class SyncEngine(
    private val syncDao: SyncDao,
    private val store: SafEventStore,
    private val deviceId: String,
    private val nextSeq: suspend () -> Long, // local counter
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun pull() {
        val files = store.listEventFiles()
        for (f in files) {
            val json = store.readText(f)
            val ev = SyncJson.decodeFromString<SyncEvent>(json)

            if (syncDao.hasEvent(ev.eventId) > 0) continue

            applyEvent(ev)
            syncDao.markApplied(AppliedEventEntity(ev.eventId, ev.ts))
        }
    }

    suspend fun emit(event: SyncEvent) {
        val ym = monthDirFromTs(event.ts)       // folder partition by event time
        val filename = filenameFor(event)
        val json = SyncJson.encodeToString(event)
        store.writeEventFile(ym, filename, json)
    }

    private suspend fun applyEvent(ev: SyncEvent) {
        when (ev) {
            is SyncEvent.UpsertCategory -> {
                val current = syncDao.getCategoryUpdatedAt(ev.uid)
                if (shouldApply(current, ev.updatedAt)) {
                    syncDao.upsertCategory(
                        CategoryEntity(
                            uid = ev.uid,
                            name = ev.name,
                            isPositive = ev.isPositive,
                            updatedAt = ev.updatedAt,
                            deleted = ev.deleted
                        )
                    )
                }
            }
            is SyncEvent.UpsertReceipt -> {
                val current = syncDao.getReceiptUpdatedAt(ev.uid)
                if (shouldApply(current, ev.updatedAt)) {
                    syncDao.upsertReceipt(
                        ReceiptEntity(
                            uid = ev.uid,
                            epochDay = ev.epochDay,
                            amount = ev.amount,
                            description = ev.description,
                            categoryUid = ev.categoryUid,
                            updatedAt = ev.updatedAt,
                            deleted = ev.deleted
                        )
                    )
                }
            }
            is SyncEvent.UpsertBudgetItem -> {
                val current = syncDao.getBudgetItemUpdatedAt(ev.categoryUid, ev.monthKey)
                if (shouldApply(current, ev.updatedAt)) {
                    syncDao.upsertBudgetItem(
                        BudgetItemEntity(
                            categoryUid = ev.categoryUid,
                            monthKey = ev.monthKey,
                            value = ev.value,
                            updatedAt = ev.updatedAt,
                            deleted = ev.deleted
                        )
                    )
                }
            }
        }
    }

    private fun shouldApply(currentUpdatedAt: Long?, incomingUpdatedAt: Long): Boolean {
        if (currentUpdatedAt == null) return true
        return incomingUpdatedAt > currentUpdatedAt
    }

    private fun filenameFor(ev: SyncEvent): String {
        // deviceId_UTC_000001.json
        val tsStr = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(ev.ts))
            .replace(":", "-")
        return "${ev.deviceId}_${tsStr}_${ev.seq.toString().padStart(6, '0')}.json"
    }

    private fun monthDirFromTs(ts: Long): String {
        val z = Instant.ofEpochMilli(ts).atZone(ZoneOffset.UTC)
        return "%04d-%02d".format(z.year, z.monthValue)
    }

    companion object {
        suspend fun buildEventId(deviceId: String, seq: Long): String = "$deviceId:$seq"
    }
}
