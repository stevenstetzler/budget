package com.vidalabs.budget.ui.prefs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vidalabs.budget.ui.BudgetViewModel
import com.vidalabs.budget.ui.ExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportBar(
    vm: BudgetViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportResult by vm.exportResult.collectAsState()

    var pendingContent by remember { mutableStateOf("") }
    var pendingCount by remember { mutableStateOf(0) }

    val jsonSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = pendingContent
        val count = pendingCount
        scope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) { vm.setExportResult(ExportResult(count)) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { vm.setExportResult(ExportResult(0, e.message)) }
            }
        }
    }

    val csvSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = pendingContent
        val count = pendingCount
        scope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) { vm.setExportResult(ExportResult(count)) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { vm.setExportResult(ExportResult(0, e.message)) }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                scope.launch {
                    try {
                        val (content, count) = vm.buildExportContent(isJson = true)
                        pendingContent = content
                        pendingCount = count
                        jsonSaver.launch("budget_export.json")
                    } catch (e: Exception) {
                        vm.setExportResult(ExportResult(0, e.message))
                    }
                }
            }) {
                Text("Export as JSON")
            }

            OutlinedButton(onClick = {
                scope.launch {
                    try {
                        val (content, count) = vm.buildExportContent(isJson = false)
                        pendingContent = content
                        pendingCount = count
                        csvSaver.launch("budget_export.csv")
                    } catch (e: Exception) {
                        vm.setExportResult(ExportResult(0, e.message))
                    }
                }
            }) {
                Text("Export as CSV")
            }
        }

        exportResult?.let { result ->
            ExportStatusLine(result = result, onDismiss = vm::dismissExportResult)
        }
    }
}

@Composable
private fun ExportStatusLine(result: ExportResult, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (result.error != null) {
            Text(
                text = "Export failed: ${result.error}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                text = "Exported ${result.exported} transaction(s)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
        TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
            Text("Dismiss", style = MaterialTheme.typography.bodySmall)
        }
    }
}
