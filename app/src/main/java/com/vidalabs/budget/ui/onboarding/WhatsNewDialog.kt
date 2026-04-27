package com.vidalabs.budget.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Dialog shown when the app is launched after an update.
 * Displays all changelog entries for versions newer than [lastSeenVersionName].
 */
@Composable
fun WhatsNewDialog(
    currentVersionName: String,
    lastSeenVersionName: String,
    onDismiss: () -> Unit
) {
    // CHANGELOG is sorted newest-first; show all entries that appear before lastSeenVersionName.
    // If lastSeenVersionName is not found (e.g. very old or unknown version), show only the
    // current version's entry to avoid overwhelming the user with the full history.
    val lastSeenIndex = CHANGELOG.indexOfFirst { it.versionName == lastSeenVersionName }
    val currentIndex = CHANGELOG.indexOfFirst { it.versionName == currentVersionName }
    val newEntries = when {
        lastSeenIndex != -1 -> CHANGELOG.take(lastSeenIndex)
        currentIndex != -1 -> listOf(CHANGELOG[currentIndex])
        else -> emptyList()
    }

    AlertDialog(
        onDismissRequest = { /* require explicit tap on button */ },
        title = {
            Text(
                text = "What's New",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (newEntries.isEmpty()) {
                    Text(
                        text = "Bug fixes and performance improvements.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    newEntries.forEach { entry ->
                        VersionSection(entry)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

@Composable
private fun VersionSection(entry: ChangelogEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Version ${entry.versionName}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        entry.changes.forEach { change ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp, top = 1.dp)
                )
                Text(
                    text = change,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
