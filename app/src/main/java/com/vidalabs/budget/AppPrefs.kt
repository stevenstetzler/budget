package com.vidalabs.budget

import android.content.Context

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun getLastSeenVersionCode(): Int = prefs.getInt("last_seen_version_code", 0)

    fun setLastSeenVersionCode(versionCode: Int) =
        prefs.edit().putInt("last_seen_version_code", versionCode).apply()
}
