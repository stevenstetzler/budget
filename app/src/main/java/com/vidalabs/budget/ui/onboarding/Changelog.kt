package com.vidalabs.budget.ui.onboarding

data class ChangelogEntry(
    val versionName: String,
    val changes: List<String>
)

/**
 * Full changelog for the app. Add a new entry for every release.
 * Keep this list in descending order (newest first).
 */
val CHANGELOG: List<ChangelogEntry> = listOf(
    ChangelogEntry(
        versionName = "0.0.1",
        changes = listOf(
            "Add \"New category\" option to Category dropdown in EntryPane",
            "Support importing transactions from JSON and CSV",
            "Add JSON and CSV export to preferences pane",
            "Order entry pane categories by recent usage",
            "Fix date selector initial value to reflect selected month",
            "Add Python FastAPI web app for budget database access",
            "Fix budget fallback to use last manual entry instead of only previous month",
            "Add front-end web app + Flask REST API backend",
            "Add HTTP endpoint sync",
            "Add Android unit tests and GitHub Actions CI workflow",
            "Add root-level README",
            "Add pytest test suites for FastAPI and Flask backends",
            "Add CSV import/export to preferences pane",
            "Fix CSV/JSON export losing transaction polarity",
            "Remove default category seeding on first install",
            "Add export_to_excel.py: CSV/JSON → legacy Excel format converter",
            "Add automated Android release workflow: signed AAB/APK → GitHub Releases + Google Play Internal Testing"
        )
    )
)
