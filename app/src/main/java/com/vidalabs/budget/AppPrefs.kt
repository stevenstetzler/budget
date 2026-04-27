package com.vidalabs.budget

import android.content.Context

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun getLastSeenVersionName(): String = prefs.getString("last_seen_version_name", "") ?: ""

    fun setLastSeenVersionName(versionName: String) =
        prefs.edit().putString("last_seen_version_name", versionName).apply()
}
