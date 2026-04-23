package com.vidalabs.budget.data

data class TransactionRow(
    val uid: String,
    val epochDay: Long,
    val amount: Double,        // signed
    val description: String?,
    val categoryName: String,
    val isPositive: Boolean,
    val recurrenceId: String? = null
)
