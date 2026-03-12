package com.vidalabs.budget.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
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
import com.vidalabs.budget.data.TransactionRow
import com.vidalabs.budget.ui.components.MoneyText
import com.vidalabs.budget.ui.components.formatMonthYear
import com.vidalabs.budget.ui.components.MonthYearPickerDialogWheel
import com.vidalabs.budget.ui.components.MonthDayYearPickerDialogWheel
import com.vidalabs.budget.data.CategoryEntity
import com.vidalabs.budget.ui.theme.SuccessContainer
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun TransactionsPane(vm: BudgetViewModel, modifier: Modifier = Modifier) {
    val ym by vm.selectedMonth.collectAsState()
    val transactions by vm.allTransactionsForMonth.collectAsState()

    var showMonthPicker by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<TransactionRow?>(null) }

    if (showMonthPicker) {
        MonthYearPickerDialogWheel(
            initial = ym,
            onDismiss = { showMonthPicker = false },
            onConfirm = {
                vm.setSelectedMonth(it)
                showMonthPicker = false
            }
        )
    }

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
                TextButton(onClick = { showMonthPicker = true }) {
                    Text(formatMonthYear(ym))
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
                            "No transactions in this month.",
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
    val dateFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    
    val transactionDate = remember(transaction.epochDay) {
        LocalDate.ofEpochDay(transaction.epochDay)
    }
    
    // Calculate the absolute amount (positive value)
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
    
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val amountFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }
    
    fun saveTransaction() {
        val amount = amountText.trim().toDoubleOrNull()
        if (amount == null || amount < 0) {
            return
        }
        
        if (categoryName.isBlank()) {
            return
        }

        vm.updateReceipt(
            uid = transaction.uid,
            epochDay = date.toEpochDay(),
            amountPositive = amount,
            description = description.takeIf { it.isNotBlank() },
            categoryName = categoryName
        )
        onDismiss()
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

    AlertDialog(
        onDismissRequest = onDismiss,
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
                        onDone = { 
                            focusManager.clearFocus()
                        }
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
                        onDone = { 
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(descriptionFocusRequester)
                )
            }
        },
        confirmButton = {
            // We put all buttons in this slot to control the layout fully
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
                    TextButton(onClick = onDismiss) {
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
