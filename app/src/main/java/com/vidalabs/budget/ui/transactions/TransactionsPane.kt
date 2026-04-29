package com.vidalabs.budget.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidalabs.budget.ui.BudgetViewModel
import com.vidalabs.budget.ui.ReceiptsSearchRange
import com.vidalabs.budget.data.TransactionRow
import com.vidalabs.budget.ui.components.MoneyText
import com.vidalabs.budget.ui.components.formatMonthYear
import com.vidalabs.budget.ui.components.MonthDayYearPickerDialogWheel
import com.vidalabs.budget.data.RecurrenceEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Human-readable labels for recurrence frequencies. */
private val FREQUENCY_OPTIONS = listOf(
    "DAILY" to "Daily",
    "WEEKLY" to "Weekly",
    "BI_WEEKLY" to "Bi-weekly",
    "MONTHLY" to "Monthly"
)

@Composable
fun TransactionsPane(vm: BudgetViewModel, modifier: Modifier = Modifier) {
    val searchRange by vm.receiptsSearchRange.collectAsState()
    val transactions by vm.allTransactionsForRange.collectAsState()

    var showRangeMenu by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<TransactionRow?>(null) }

    if (selectedTransaction != null) {
        EditTransactionDialog(
            transaction = selectedTransaction!!,
            vm = vm,
            onDismiss = { selectedTransaction = null }
        )
    }

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item(contentType = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Receipts", style = MaterialTheme.typography.headlineMedium)
                Box {
                    TextButton(onClick = { showRangeMenu = true }) {
                        Text(searchRange.label)
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select receipts search range"
                        )
                    }
                    DropdownMenu(
                        expanded = showRangeMenu,
                        onDismissRequest = { showRangeMenu = false }
                    ) {
                        ReceiptsSearchRange.entries.forEach { range ->
                            DropdownMenuItem(
                                text = { Text(range.label) },
                                onClick = {
                                    vm.setReceiptsSearchRange(range)
                                    showRangeMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Transactions list
        if (transactions.isEmpty()) {
            item(contentType = "empty") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No transactions for this search.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            items(
                items = transactions,
                key = { it.uid },
                contentType = { "transaction" }
            ) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    onClick = { selectedTransaction = transaction }
                )
            }
        }
    }
}

@Composable
private fun TransactionCard(transaction: TransactionRow, onClick: () -> Unit) {
    val date = remember(transaction.epochDay) {
        LocalDate.ofEpochDay(transaction.epochDay).toString()
    }
    val isRecurring = transaction.recurrenceId != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        transaction.categoryName,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (transaction.isPositive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Text(
                            if (transaction.isPositive) "Income" else "Expense",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (isRecurring) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Recurring",
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    "Recurring",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!transaction.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        transaction.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            MoneyText(
                amount = transaction.amount,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
private fun EditTransactionDialog(
    transaction: TransactionRow,
    vm: BudgetViewModel,
    onDismiss: () -> Unit
) {
    val categories by vm.categories.collectAsState()
    val recurrence by vm.recurrenceForReceipt.collectAsState()
    val recurrenceActiveForMonth by vm.recurrenceActiveForMonth.collectAsState()
    val selectedMonth by vm.selectedMonth.collectAsState()
    val dateFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }

    // Load recurrence when dialog opens
    LaunchedEffect(transaction.uid) {
        vm.loadRecurrenceForReceipt(transaction.uid)
    }

    // When recurrence changes, load isActive for the current month
    LaunchedEffect(recurrence?.id) {
        val id = recurrence?.id
        if (id != null) {
            vm.loadRecurrenceActiveForMonth(id)
        }
    }

    val transactionDate = remember(transaction.epochDay) {
        LocalDate.ofEpochDay(transaction.epochDay)
    }

    val absoluteAmount = remember(transaction.amount) {
        kotlin.math.abs(transaction.amount)
    }

    var date by remember { mutableStateOf(transactionDate) }
    var categoryName by remember { mutableStateOf(transaction.categoryName) }
    var amountText by remember { mutableStateOf(String.format(Locale.US, "%.2f", absoluteAmount)) }
    var description by remember { mutableStateOf(transaction.description ?: "") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Recurrence editing state
    var showRecurrenceSection by remember { mutableStateOf(transaction.recurrenceId != null) }
    var recurrenceFrequency by remember { mutableStateOf("MONTHLY") }
    var recurrenceEndDate by remember { mutableStateOf<LocalDate?>(null) }
    var recurrenceFreqExpanded by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showRemoveRecurrenceDialog by remember { mutableStateOf(false) }

    // Sync recurrence fields when recurrence is loaded
    LaunchedEffect(recurrence) {
        val rec = recurrence
        if (rec != null) {
            recurrenceFrequency = rec.frequency
            recurrenceEndDate = rec.endDate?.let { LocalDate.ofEpochDay(it) }
            // Default the date field to the recurrence's actual start date, not the
            // virtual occurrence date (vl.targetMonth). Without this, pressing Save
            // from a future occurrence month would silently move the receipt's epochDay
            // and the recurrence's startDate to the occurrence month.
            date = LocalDate.ofEpochDay(rec.startDate)
        }
    }

    val focusManager = LocalFocusManager.current
    val amountFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }

    fun saveTransaction() {
        val amount = amountText.trim().toDoubleOrNull()
        if (amount == null || amount < 0) return
        if (categoryName.isBlank()) return

        // Save/update recurrence
        if (showRecurrenceSection) {
            // Recurring: update receipt + upsert recurrence in one go.
            vm.updateReceipt(
                uid = transaction.uid,
                epochDay = date.toEpochDay(),
                amountPositive = amount,
                description = description.takeIf { it.isNotBlank() },
                categoryName = categoryName
            )
            val dayOfPeriod = when (recurrenceFrequency) {
                "MONTHLY" -> date.dayOfMonth
                "WEEKLY", "BI_WEEKLY" -> date.dayOfWeek.value  // 1=Mon … 7=Sun
                else -> 1
            }
            vm.upsertRecurrence(
                receiptId = transaction.uid,
                frequency = recurrenceFrequency,
                startDate = date.toEpochDay(),
                endDate = recurrenceEndDate?.toEpochDay(),
                dayOfPeriod = dayOfPeriod,
                existingId = recurrence?.id
            )
            onDismiss()
        } else if (recurrence != null) {
            // User toggled off recurrence — prompt before removing.
            // Do NOT call updateReceipt here: doing so with the occurrence date
            // (date == vl.targetMonth) would move the receipt to the wrong month.
            showRemoveRecurrenceDialog = true
        } else {
            // Plain (non-recurring) receipt edit.
            vm.updateReceipt(
                uid = transaction.uid,
                epochDay = date.toEpochDay(),
                amountPositive = amount,
                description = description.takeIf { it.isNotBlank() },
                categoryName = categoryName
            )
            onDismiss()
        }
    }

    if (showDatePicker) {
        MonthDayYearPickerDialogWheel(
            initial = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { newDate ->
                date = newDate
                showDatePicker = false
            }
        )
    }

    if (showEndDatePicker) {
        MonthDayYearPickerDialogWheel(
            initial = recurrenceEndDate ?: LocalDate.now(),
            onDismiss = { showEndDatePicker = false },
            onConfirm = { newDate ->
                recurrenceEndDate = newDate
                showEndDatePicker = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Transaction") },
            text = { Text("Are you sure you want to delete this transaction? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteReceipt(transaction.uid)
                        showDeleteConfirm = false
                        onDismiss()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRemoveRecurrenceDialog) {
        val rec = recurrence
        AlertDialog(
            onDismissRequest = { showRemoveRecurrenceDialog = false },
            title = { Text("Stop Recurring?") },
            text = {
                Text(
                    "Set the end date to ${formatMonthYear(selectedMonth)} so the receipt " +
                    "remains in all previous months, or keep only the original instance?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (rec != null) {
                            val amt = amountText.trim().toDoubleOrNull()
                            if (amt != null) {
                                // Update receipt back to its original start date so it
                                // doesn't get stuck at the occurrence/targetMonth date.
                                vm.updateReceipt(
                                    uid = transaction.uid,
                                    epochDay = rec.startDate,
                                    amountPositive = amt,
                                    description = description.takeIf { it.isNotBlank() },
                                    categoryName = categoryName
                                )
                            }
                            val endDate = selectedMonth.atEndOfMonth().toEpochDay()
                            vm.upsertRecurrence(
                                receiptId = transaction.uid,
                                frequency = rec.frequency,
                                startDate = rec.startDate,
                                endDate = endDate,
                                dayOfPeriod = rec.dayOfPeriod,
                                existingId = rec.id
                            )
                        }
                        showRemoveRecurrenceDialog = false
                        onDismiss()
                    }
                ) {
                    Text("Set end date to ${formatMonthYear(selectedMonth)}")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (rec != null) {
                            // Pass all receipt fields so removeRecurrence can update
                            // the receipt atomically (restoring the original startDate
                            // and user-edited fields) without a separate updateReceipt
                            // call that could race with the recurrence removal.
                            val amt = amountText.trim().toDoubleOrNull()
                            vm.removeRecurrence(
                                recurrenceId = rec.id,
                                receiptEpochDay = rec.startDate,
                                receiptAmountPositive = amt,
                                receiptDescription = description.takeIf { it.isNotBlank() },
                                receiptCategoryName = categoryName,
                            )
                        }
                        showRemoveRecurrenceDialog = false
                        onDismiss()
                    }
                ) {
                    Text("Keep only original")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = {
            vm.clearRecurrenceForReceipt()
            onDismiss()
        },
        title = {
            Text("Edit Transaction", style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date picker trigger
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = date.format(dateFmt),
                        onValueChange = {},
                        label = { Text("Date") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true }
                    )
                }

                // Category dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { categoryExpanded = !categoryExpanded }
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    categoryName = cat.name
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Amount input
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(amountFocusRequester)
                )

                // Description input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(descriptionFocusRequester)
                )

                // ── Recurrence section ──────────────────────────────────────
                HorizontalDivider()

                // Toggle recurring on/off
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Recurring", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = showRecurrenceSection,
                        onCheckedChange = { showRecurrenceSection = it }
                    )
                }

                if (showRecurrenceSection) {
                    // isActive checkbox for this month (only shown when already recurring)
                    if (recurrence != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Active in ${formatMonthYear(selectedMonth)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Checkbox(
                                checked = recurrenceActiveForMonth,
                                onCheckedChange = { checked ->
                                    vm.setRecurrenceActiveForMonth(recurrence!!.id, checked)
                                }
                            )
                        }
                    }

                    // Frequency picker
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = FREQUENCY_OPTIONS.find { it.first == recurrenceFrequency }?.second
                                ?: recurrenceFrequency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Frequency") },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { recurrenceFreqExpanded = true }
                        )
                        DropdownMenu(
                            expanded = recurrenceFreqExpanded,
                            onDismissRequest = { recurrenceFreqExpanded = false }
                        ) {
                            FREQUENCY_OPTIONS.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        recurrenceFrequency = key
                                        recurrenceFreqExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // End date (optional)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = recurrenceEndDate?.format(dateFmt) ?: "Ongoing",
                            onValueChange = {},
                            label = { Text("End Date (optional)") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showEndDatePicker = true }
                        )
                    }
                    if (recurrenceEndDate != null) {
                        TextButton(
                            onClick = { recurrenceEndDate = null },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Clear end date")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: Delete
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }

                // Right side: Cancel and Save
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        vm.clearRecurrenceForReceipt()
                        onDismiss()
                    }) {
                        Text("Cancel")
                    }
                    Button(onClick = { saveTransaction() }) {
                        Text("Save")
                    }
                }
            }
        }
    )
}
