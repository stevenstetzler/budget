package com.vidalabs.budget.ui.prefs

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidalabs.budget.sync.SyncManager
import com.vidalabs.budget.sync.SyncPrefs
import com.vidalabs.budget.sync.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SyncBar(
    sync: SyncManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }

    var folderUri by remember { mutableStateOf(prefs.getFolderUri()) }
    var status by remember { mutableStateOf(sync.readStatus()) }
    var endpointInput by remember { mutableStateOf(prefs.getEndpointUrl().orEmpty()) }

    // ✅ queued comes from SyncDao Flow<Int>
    val queuedCount by sync.dao.observeOutboxCount().collectAsState(initial = 0)

    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            prefs.setFolderUri(uri)
            folderUri = uri
            status = sync.readStatus()
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Folder sync row
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(null) }) {
                Text(if (folderUri == null) "Select sync folder" else "Change sync folder")
            }

            Button(
                enabled = folderUri != null,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        runCatching { sync.syncNow() }
                        val newStatus = sync.readStatus()
                        withContext(Dispatchers.Main) { status = newStatus }
                    }
                }
            ) { Text("Sync now") }
        }

        // Endpoint sync row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = endpointInput,
                onValueChange = { endpointInput = it },
                modifier = Modifier.weight(1f),
                label = { Text("Sync endpoint URL") },
                placeholder = { Text("https://my.website.com/budget") },
                singleLine = true
            )

            Button(
                enabled = endpointInput.isNotBlank(),
                onClick = {
                    val url = endpointInput.trim()
                    prefs.setEndpointUrl(url)
                    scope.launch(Dispatchers.IO) {
                        runCatching { sync.syncWithEndpoint() }
                        val newStatus = sync.readStatus()
                        withContext(Dispatchers.Main) { status = newStatus }
                    }
                }
            ) { Text("Sync") }
        }

        SyncStatusLine(status = status, queuedCount = queuedCount)
    }
}

@Composable
private fun SyncStatusLine(status: SyncStatus, queuedCount: Int) {
    val fmt = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    }
    fun ts(ms: Long): String = if (ms <= 0L) "—" else fmt.format(Instant.ofEpochMilli(ms))

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = buildString {
                append("Folder: ")
                append(if (status.folderSet) "Set" else "Not set")
                if (status.endpointUrl != null) append(" • Endpoint: ${status.endpointUrl}")
                append(" • Queued: $queuedCount")
                append(" • Last push: ${ts(status.lastPushMs)}")
                append(" • Last pull: ${ts(status.lastPullMs)}")
            },
            style = MaterialTheme.typography.bodySmall
        )

        val err = status.lastError?.trim().orEmpty()
        if (err.isNotBlank()) {
            Text(
                text = "Last error: $err",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
