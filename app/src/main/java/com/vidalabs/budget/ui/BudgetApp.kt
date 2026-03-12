package com.vidalabs.budget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.vidalabs.budget.ui.budget.BudgetPane
import com.vidalabs.budget.ui.entry.EntryPane
import com.vidalabs.budget.ui.summary.SummaryPane
import com.vidalabs.budget.ui.transactions.TransactionsPane
import com.vidalabs.budget.sync.SyncManager
import com.vidalabs.budget.ui.prefs.PreferencesPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetApp(vm: BudgetViewModel, sync: SyncManager, modifier: Modifier = Modifier) {
    var tab by remember { mutableStateOf(0) } // 0=Entry, 1=Summary, 2=Budget, 3=Transactions, 4=Preferences

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // This is the key: keeps content out of status/nav bars (wifi/battery etc.)
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                TopAppBar(
                    title = { Text("Budget") },
                    actions = {
                        IconButton(onClick = { tab = 4 }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    }
                )
                TabRow(selectedTabIndex = tab.coerceIn(0, 3)) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Entry") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Summary") })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Budget") })
                    Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Receipts") })
                }
            }
        }
    ) { innerPadding ->
        // Use the scaffold insets so panes never slide under system UI
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            when (tab) {
                0 -> EntryPane(vm = vm, modifier = Modifier.fillMaxSize())
                1 -> SummaryPane(vm = vm, modifier = Modifier.fillMaxSize())
                2 -> BudgetPane(vm = vm, modifier = Modifier.fillMaxSize())
                3 -> TransactionsPane(vm = vm, modifier = Modifier.fillMaxSize())
                4 -> PreferencesPane(vm = vm, sync = sync, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
