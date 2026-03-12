package com.vidalabs.budget.sync

import android.content.Context
import android.provider.Settings
import com.vidalabs.budget.data.AppDatabase
import com.vidalabs.budget.data.OutboxEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

class SyncManager(
    private val context: Context,
    private val db: AppDatabase
) {
    private val prefs = SyncPrefs(context)
    private val statusPrefs = SyncStatusPrefs(context)
    private val seqStore = SeqStore(context)

    private val deviceId: String by lazy {
        "android-" + Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
    val dao = db.syncDao();

    private fun storeOrNull(): SafEventStore? {
        val uri = prefs.getFolderUri() ?: return null
        return SafEventStore(context, uri)
    }

    private suspend fun nextSeq(): Long = seqStore.next(deviceId)

    /**
     * Build an event, then either write it to the sync folder or enqueue it to the outbox.
     */
    suspend fun push(build: (eventId: String, deviceId: String, seq: Long, ts: Long) -> SyncEvent) =
        withContext(Dispatchers.IO) {
            val ts = System.currentTimeMillis()
            val seq = nextSeq()
            val eventId = "$deviceId:$seq"

            val ev = build(eventId, deviceId, seq, ts)
            val json = SyncJson.encodeToString(ev)

            val monthDir = monthDirFromTs(ts)
            val filename = filenameFor(deviceId, ts, seq)

            val store = storeOrNull()
            if (store == null) {
                // Folder not set: queue
                db.syncDao().enqueueOutbox(
                    OutboxEventEntity(
                        eventId = eventId,
                        monthDir = monthDir,
                        filename = filename,
                        json = json,
                        ts = ts
                    )
                )
                statusPrefs.setLastError("Sync folder not set (queued)")
                return@withContext
            }

            try {
                store.writeEventFile(monthDir, filename, json)
                statusPrefs.setLastPushMs(System.currentTimeMillis())
                statusPrefs.setLastError(null)
            } catch (t: Throwable) {
                // Folder set but write failed: queue anyway
                db.syncDao().enqueueOutbox(
                    OutboxEventEntity(
                        eventId = eventId,
                        monthDir = monthDir,
                        filename = filename,
                        json = json,
                        ts = ts
                    )
                )
                statusPrefs.setLastError("Push failed (queued): ${t.message ?: t::class.simpleName}")
            }
        }

    /**
     * Flush queued outbox events to the folder (if set).
     */
    suspend fun flushOutbox(): Int = withContext(Dispatchers.IO) {
        val store = storeOrNull() ?: return@withContext 0
        val dao = db.syncDao()

        val items = dao.listOutbox()
        var flushed = 0

        for (e in items) {
            try {
                store.writeEventFile(e.monthDir, e.filename, e.json)
                dao.deleteOutboxById(e.id)
                flushed += 1
            } catch (t: Throwable) {
                statusPrefs.setLastError("Flush failed: ${t.message ?: t::class.simpleName}")
                break // stop; likely folder became unavailable
            }
        }

        if (flushed > 0) statusPrefs.setLastPushMs(System.currentTimeMillis())
        flushed
    }

    /**
     * Pull remote events into local DB (no-op if folder not set).
     */
    suspend fun pull() = withContext(Dispatchers.IO) {
        val store = storeOrNull() ?: return@withContext
        val engine = SyncEngine(
            syncDao = db.syncDao(),
            store = store,
            deviceId = deviceId,
            nextSeq = { nextSeq() }
        )
        engine.pull()
        statusPrefs.setLastPullMs(System.currentTimeMillis())
        statusPrefs.setLastError(null)
    }

    /**
     * Full sync: flush outbox first, then pull.
     */
    suspend fun syncNow() = withContext(Dispatchers.IO) {
        flushOutbox()
        pull()
        statusPrefs.setLastSyncMs(System.currentTimeMillis())
    }

    fun readStatus(): SyncStatus {
        val dao = db.syncDao()
        // can't call suspend here; so omit outbox count in this function, or compute in UI via a Flow.
        return SyncStatus(
            folderSet = prefs.getFolderUri() != null,
            lastPushMs = statusPrefs.getLastPushMs(),
            lastPullMs = statusPrefs.getLastPullMs(),
            lastSyncMs = statusPrefs.getLastSyncMs(),
            lastError = statusPrefs.getLastError()
        )
    }
}

data class SyncStatus(
    val folderSet: Boolean,
    val lastPushMs: Long,
    val lastPullMs: Long,
    val lastSyncMs: Long,
    val lastError: String?
)
