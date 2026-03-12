package com.vidalabs.budget.ui.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vidalabs.budget.ui.components.MonthYearPickerDialogWheel
import com.vidalabs.budget.ui.components.formatMonthYear
import com.vidalabs.budget.ui.BudgetViewModel
import java.time.YearMonth
import kotlin.math.abs
import java.util.Locale

@Composable
fun BudgetPane(vm: BudgetViewModel, modifier: Modifier = Modifier) {
    val ym by vm.selectedMonth.collectAsState()
    val rows by vm.budgetRowsForMonth.collectAsState()

    var showMonthPicker by remember { mutableStateOf(false) }
    if (showMonthPicker) {
        MonthYearPickerDialogWheel(
            initial = YearMonth.now(),
            onDismiss = { showMonthPicker = false },
            onConfirm = { chosen ->
                vm.setSelectedMonth(chosen)
                showMonthPicker = false
            }
        )
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Budget", style = MaterialTheme.typography.headlineMedium)

            TextButton(onClick = { showMonthPicker = true }) {
                Text(formatMonthYear(ym))
            }
        }

        if (rows.isEmpty()) {
            Text("No categories yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(rows, key = { it.categoryUid }) { r ->
                    // --- OPTIMIZATION: Stabilize Lambda ---
                    // By remembering this lambda, we prevent BudgetRowCard from recomposing
                    // unnecessarily when other parts of the UI update.
                    val onCommitStable = remember(r.categoryUid, vm) {
                        { v: Double -> vm.setBudgetValue(r.categoryUid, v) }
                    }

                    BudgetRowCard(
                        name = r.name,
                        isPositive = r.isPositive,
                        initialValue = r.value,
                        onCommit = onCommitStable
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetRowCard(
    name: String,
    isPositive: Boolean,
    initialValue: Double,
    onCommit: (Double) -> Unit
) {
    // --- OPTIMIZATION: Optimize String Formatting ---
    // String.format is slow. We only compute the string when initialValue changes.
    // We use a simple remember block to initialize the text.
    val initialText = remember(initialValue) {
        if (initialValue == 0.0) "" else "%.2f".format(Locale.US, initialValue)
    }

    var text by remember { mutableStateOf(initialText) }

    // Re-sync text if data changes externally (but respect active typing logic via threshold)
    LaunchedEffect(initialValue) {
        val current = text.toDoubleOrNull() ?: 0.0
        // Only update the text field if the value in the DB is significantly different
        // from what is currently typed. This prevents cursor jumping while typing "10."
        if (abs(current - initialValue) > 0.001) {
            text = if (initialValue == 0.0) "" else "%.2f".format(Locale.US, initialValue)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.labelLarge)
                Text(
                    if (isPositive) "Income category" else "Spending category",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText
                    commitBudget(newText, onCommit)
                },
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(140.dp)
            )
        }
    }
}

private fun commitBudget(text: String, onCommit: (Double) -> Unit) {
    val t = text.trim()
    if (t.isEmpty()) {
        onCommit(0.0)
        return
    }
    val v = t.toDoubleOrNull()
    if (v != null && v >= 0.0) {
        onCommit(v)
    }
    // If invalid (e.g. user typed "10.."), we simply don't commit, keeping the UI state as is.
}
