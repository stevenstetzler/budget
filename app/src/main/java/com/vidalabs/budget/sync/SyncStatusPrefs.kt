package com.vidalabs.budget.sync

import android.content.Context

class SyncStatusPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sync_status", Context.MODE_PRIVATE)

    fun setLastPushMs(v: Long) = prefs.edit().putLong("last_push_ms", v).apply()
    fun setLastPullMs(v: Long) = prefs.edit().putLong("last_pull_ms", v).apply()
    fun setLastSyncMs(v: Long) = prefs.edit().putLong("last_sync_ms", v).apply()
    fun setLastError(v: String?) = prefs.edit().putString("last_error", v).apply()

    fun getLastPushMs(): Long = prefs.getLong("last_push_ms", 0L)
    fun getLastPullMs(): Long = prefs.getLong("last_pull_ms", 0L)
    fun getLastSyncMs(): Long = prefs.getLong("last_sync_ms", 0L)
    fun getLastError(): String? = prefs.getString("last_error", null)
}
