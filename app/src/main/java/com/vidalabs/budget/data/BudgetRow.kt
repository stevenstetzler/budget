package com.vidalabs.budget.data

data class BudgetRow(
    val categoryUid: String,
    val name: String,
    val isPositive: Boolean,
    val value: Double // defaulted: current month value, else prev month, else 0
)
