package com.vidalabs.budget.ui.onboarding

data class ChangelogEntry(
    val versionName: String,
    val versionCode: Int,
    val changes: List<String>
)

/**
 * Full changelog for the app. Add a new entry for every release.
 * Keep this list in descending order (newest first).
 */
val CHANGELOG: List<ChangelogEntry> = listOf(
    ChangelogEntry(
        versionName = "1.0",
        versionCode = 1,
        changes = listOf(
            "Track expenses and income with customizable categories",
            "Monthly budget planning and spending summaries",
            "Sync across devices via a local folder or self-hosted server",
            "Import and export data as CSV"
        )
    )
)
