package com.vidalabs.budget.sync

import android.content.Context

class SeqStore(context: Context) {
    private val prefs = context.getSharedPreferences("sync_seq", Context.MODE_PRIVATE)

    suspend fun next(deviceId: String): Long {
        // per-device counter; deviceId included in key
        val key = "seq_$deviceId"
        val cur = prefs.getLong(key, 0L)
        val nxt = cur + 1L
        prefs.edit().putLong(key, nxt).apply()
        return nxt
    }
}
