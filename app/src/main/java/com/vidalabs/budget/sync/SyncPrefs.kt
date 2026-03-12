package com.vidalabs.budget.sync

import android.content.Context
import android.net.Uri

class SyncPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    fun setFolderUri(uri: Uri) {
        prefs.edit().putString("sync_folder_uri", uri.toString()).apply()
    }

    fun getFolderUri(): Uri? =
        prefs.getString("sync_folder_uri", null)?.let(Uri::parse)
}
