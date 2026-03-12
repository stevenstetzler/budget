package com.vidalabs.budget.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vidalabs.budget.ui.BudgetViewModel
import com.vidalabs.budget.ui.components.MonthDayYearPickerDialogWheel
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryPane(vm: BudgetViewModel, modifier: Modifier = Modifier) {
    val state by vm.entry.collectAsState()
    val categories by vm.categories.collectAsState()

    val dateFmt = remember { DateTimeFormatter.ISO_LOCAL_DATE }
    var categoryExpanded by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showAmountDialog by remember { mutableStateOf(false) }
    var showDescriptionDialog by remember { mutableStateOf(false) }

    // --- ADDED: Snackbar State ---
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (showDatePicker) {
        MonthDayYearPickerDialogWheel(
            initial = state.date,
            onDismiss = { showDatePicker = false },
            onConfirm = { newDate ->
                vm.setDate(newDate)
                showDatePicker = false
            }
        )
    }

    if (showAmountDialog) {
        var tempAmount by remember { mutableStateOf(state.amountText) }
        val focusRequester = remember { FocusRequester() }

        AlertDialog(
            onDismissRequest = { showAmountDialog = false },
            title = { Text("Enter Amount") },
            text = {
                OutlinedTextField(
                    value = tempAmount,
                    onValueChange = { tempAmount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setAmountText(tempAmount)
                    showAmountDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showAmountDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDescriptionDialog) {
        var tempDesc by remember { mutableStateOf(state.description) }
        val focusRequester = remember { FocusRequester() }

        AlertDialog(
            onDismissRequest = { showDescriptionDialog = false },
            title = { Text("Enter Description") },
            text = {
                OutlinedTextField(
                    value = tempDesc,
                    onValueChange = { tempDesc = it },
                    label = { Text("Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setDescription(tempDesc)
                    showDescriptionDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDescriptionDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Wrap content in a Scaffold or Box to overlay the Snackbar
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("New Entry", style = MaterialTheme.typography.headlineMedium)

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }

            // Date
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.date.format(dateFmt),
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

            // Category
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = state.category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = true
                        )
                )

                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = {
                                vm.setCategory(cat.name)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            // Amount
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = {},
                    label = { Text("Amount") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showAmountDialog = true }
                )
            }

            // Description
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = {},
                    label = { Text("Description") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDescriptionDialog = true }
                )
            }

            Button(
                onClick = {
                    // Perform validation check before showing success
                    val isValid = state.amountText.isNotEmpty() && state.category.isNotEmpty()

                    vm.add()

                    if (isValid) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Transaction added successfully")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add Transaction")
            }
        }
    }
}
