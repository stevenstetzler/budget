package com.vidalabs.budget.ui.prefs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidalabs.budget.ui.BudgetViewModel
import com.vidalabs.budget.ui.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ImportBar(
    vm: BudgetViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importResult by vm.importResult.collectAsState()
    var unsupportedFileError by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val fileName = uri.lastPathSegment ?: ""
            val isJson = mimeType.contains("json", ignoreCase = true) ||
                fileName.endsWith(".json", ignoreCase = true)
            val isCsv = mimeType.contains("csv", ignoreCase = true) ||
                mimeType.contains("comma-separated", ignoreCase = true) ||
                fileName.endsWith(".csv", ignoreCase = true)

            if (!isJson && !isCsv) {
                withContext(Dispatchers.Main) {
                    unsupportedFileError = "Unsupported file type. Please select a .json or .csv file."
                }
                return@launch
            }

            withContext(Dispatchers.Main) { unsupportedFileError = null }
            val content = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@launch
            vm.importTransactions(content, isJson)
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = {
            unsupportedFileError = null
            filePicker.launch(
                arrayOf(
                    "application/json",
                    "text/csv",
                    "text/comma-separated-values",
                    "*/*"
                )
            )
        }) {
            Text("Import file")
        }

        unsupportedFileError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        importResult?.let { result ->
            ImportStatusLine(result = result, onDismiss = vm::dismissImportResult)
        }
    }
}

@Composable
private fun ImportStatusLine(result: ImportResult, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Imported ${result.imported} transaction(s)" +
                    if (result.errors.isNotEmpty()) " • ${result.errors.size} failed" else "",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                Text("Dismiss", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (result.errors.isNotEmpty()) {
            Text(
                text = result.errors.take(3).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
