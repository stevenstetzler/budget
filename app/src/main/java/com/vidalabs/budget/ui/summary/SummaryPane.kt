package com.vidalabs.budget.ui.summary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vidalabs.budget.ui.BudgetViewModel
import com.vidalabs.budget.data.SummaryBudgetRow
import com.vidalabs.budget.data.ReceiptRow
import com.vidalabs.budget.ui.components.MoneyText
import com.vidalabs.budget.ui.components.formatMonthYear
import com.vidalabs.budget.ui.theme.ErrorContainer
import com.vidalabs.budget.ui.theme.SuccessContainer
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.vidalabs.budget.ui.components.MonthYearPickerDialogWheel
import java.time.YearMonth

@Composable
fun SummaryPane(vm: BudgetViewModel, modifier: Modifier = Modifier) {
    val ym by vm.selectedMonth.collectAsState()
    val rows by vm.summaryRowsForMonth.collectAsState()
    val cashFlow by vm.cashFlowForMonth.collectAsState()
    val budgetedCashFlow by vm.budgetedCashFlowForMonth.collectAsState()
    val expandedUid by vm.expandedSummaryCategoryUid.collectAsState()
    val expandedReceipts by vm.receiptsForExpandedCategory.collectAsState()

    var showMonthPicker by remember { mutableStateOf(false) }
    if (showMonthPicker) {
        MonthYearPickerDialogWheel(
            initial = YearMonth.now(),
            onDismiss = { showMonthPicker = false },
            onConfirm = {
                vm.setSelectedMonth(it)
                showMonthPicker = false
            }
        )
    }

    // --- OPTIMIZATION (1): Cache expensive list operations ---
    val (incomeMain, spendMain, incomeOther, spendOther) = remember(rows) {
        val income = rows.filter { it.isPositive }
        val spending = rows.filter { !it.isPositive }

        fun mainRows(rs: List<SummaryBudgetRow>) =
            rs.filter { it.budget > 0.0 || it.actual > 0.0 }
                .sortedByDescending { it.actual }

        fun otherRows(rs: List<SummaryBudgetRow>) =
            rs.filter { it.budget == 0.0 && it.actual == 0.0 }
                .sortedBy { it.name.lowercase() }

        Quadruple(
            mainRows(income),
            mainRows(spending),
            otherRows(income),
            otherRows(spending)
        )
    }

    var incomeOtherExpanded by remember { mutableStateOf(false) }
    var spendOtherExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item(contentType = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Summary", style = MaterialTheme.typography.headlineMedium)
                    TextButton(onClick = { showMonthPicker = true }) {
                        Text(formatMonthYear(ym))
                    }
                }
            }
        }

        // Cash Flow
        item(contentType = "cashflow") {
            val colors = when {
                cashFlow > 0.0 -> CardDefaults.cardColors(
                    containerColor = SuccessContainer,
                    contentColor = androidx.compose.ui.graphics.Color.Black
                )
                cashFlow < 0.0 -> CardDefaults.cardColors(
                    containerColor = ErrorContainer,
                    contentColor = androidx.compose.ui.graphics.Color.Black
                )
                else -> CardDefaults.cardColors()
            }

            Card(modifier = Modifier.fillMaxWidth(), colors = colors) {
                Column(Modifier.padding(16.dp)) {
                    Text("Cash Flow", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoneyText(
                            amount = cashFlow,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text("/", style = MaterialTheme.typography.headlineSmall)
                        MoneyText(
                            amount = budgetedCashFlow,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    Text("Actual / Budgeted", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Income
        item(contentType = "section-header") {
            Text("Income", style = MaterialTheme.typography.titleMedium)
        }

        // --- OPTIMIZATION (2): contentType + Stable Lambdas ---
        items(
            items = incomeMain,
            key = { it.categoryUid },
            contentType = { "category-main" } // Helps node reuse
        ) { r ->
            val isExpanded = (expandedUid == r.categoryUid)
            // Stable lambda prevents recomposition of this item when OTHER items are clicked
            val onToggle = remember(r.categoryUid, vm) {
                { vm.toggleExpandedSummaryCategory(r.categoryUid) }
            }

            SummaryCategoryCard(
                row = r,
                expanded = isExpanded,
                receipts = if (isExpanded) expandedReceipts else emptyList(),
                onToggle = onToggle
            )
        }

        item(contentType = "other-header") {
            OtherCategoriesHeader(
                expanded = incomeOtherExpanded,
                count = incomeOther.size,
                onToggle = { incomeOtherExpanded = !incomeOtherExpanded }
            )
        }

        if (incomeOtherExpanded) {
            items(
                items = incomeOther,
                key = { it.categoryUid }, // Simplified key (assuming UIDs are unique globally)
                contentType = { "category-other" }
            ) {
                SummaryOtherCategoryCard(it)
            }
        }

        // Spending
        item(contentType = "section-header") {
            Text("Spending", style = MaterialTheme.typography.titleMedium)
        }

        items(
            items = spendMain,
            key = { it.categoryUid },
            contentType = { "category-main" }
        ) { r ->
            val isExpanded = (expandedUid == r.categoryUid)
            val onToggle = remember(r.categoryUid, vm) {
                { vm.toggleExpandedSummaryCategory(r.categoryUid) }
            }

            SummaryCategoryCard(
                row = r,
                expanded = isExpanded,
                receipts = if (isExpanded) expandedReceipts else emptyList(),
                onToggle = onToggle
            )
        }

        item(contentType = "other-header") {
            OtherCategoriesHeader(
                expanded = spendOtherExpanded,
                count = spendOther.size,
                onToggle = { spendOtherExpanded = !spendOtherExpanded }
            )
        }

        if (spendOtherExpanded) {
            items(
                items = spendOther,
                key = { it.categoryUid },
                contentType = { "category-other" }
            ) {
                SummaryOtherCategoryCard(it)
            }
        }
    }
}

// Simple helper class to hold the 4 lists
private data class Quadruple<A, B, C, D>(
    val first: A, val second: B, val third: C, val fourth: D
)

@Composable
private fun SummaryCategoryCard(
    row: SummaryBudgetRow,
    expanded: Boolean,
    receipts: List<ReceiptRow>,
    onToggle: () -> Unit
) {
    val overBudget =
        if (row.budget <= 0) false
        else if (row.isPositive) row.actual < row.budget
        else row.actual > row.budget

    val underBudget =
        if (row.budget <= 0) false
        else if (row.isPositive) row.actual > row.budget
        else row.actual < row.budget

    val colors = when {
        overBudget -> CardDefaults.cardColors(
            containerColor = ErrorContainer,
            contentColor = androidx.compose.ui.graphics.Color.Black
        )
        underBudget -> CardDefaults.cardColors(
            containerColor = SuccessContainer,
            contentColor = androidx.compose.ui.graphics.Color.Black
        )
        else -> CardDefaults.cardColors()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = colors
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(row.name, style = MaterialTheme.typography.labelLarge)
                Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.bodySmall)
            }

            // amount line
            if (row.budget > 0.0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoneyText(amount = row.actual, style = MaterialTheme.typography.headlineSmall)
                    Text("/", style = MaterialTheme.typography.headlineSmall)
                    MoneyText(amount = row.budget, style = MaterialTheme.typography.headlineSmall)
                }
            } else {
                MoneyText(amount = row.actual, style = MaterialTheme.typography.headlineSmall)
            }

            // expanded receipts list (bounded height + scrollable)
            if (expanded) {
                if (receipts.isEmpty()) {
                    Text("No transactions in this month.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            receipts.forEach { rec ->
                                ReceiptRowItem(rec)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptRowItem(r: ReceiptRow) {
    val date = remember(r.epochDay) { LocalDate.ofEpochDay(r.epochDay).toString() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(date, style = MaterialTheme.typography.bodySmall)
            Text(
                r.description ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        MoneyText(amount = r.amount, style = MaterialTheme.typography.bodyLarge)
    }
}


@Composable
private fun OtherCategoriesHeader(expanded: Boolean, count: Int, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Other categories ($count)", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onToggle) {
                Text(if (expanded) "Hide" else "Show")
            }
        }
    }
}

@Composable
private fun SummaryOtherCategoryCard(r: SummaryBudgetRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(r.name, style = MaterialTheme.typography.labelLarge)
            Text("—", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
