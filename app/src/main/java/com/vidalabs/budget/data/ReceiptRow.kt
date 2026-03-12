package com.vidalabs.budget.data

data class ReceiptRow(
    val uid: String,
    val epochDay: Long,
    val amount: Double,        // signed
    val description: String?
)
