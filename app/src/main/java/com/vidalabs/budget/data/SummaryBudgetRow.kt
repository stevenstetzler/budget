package com.vidalabs.budget.data

data class SummaryBudgetRow(
    val categoryUid: String,
    val name: String,
    val isPositive: Boolean,
    val actual: Double,  // positive magnitude
    val budget: Double   // positive
)

