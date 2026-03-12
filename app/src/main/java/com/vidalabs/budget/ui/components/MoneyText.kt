package com.vidalabs.budget.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import kotlin.math.abs

@Composable
fun MoneyText(
    amount: Double,
    style: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val formatted = "$" + String.format("%,.2f", abs(amount))
    val sign = if (amount < 0) "−" else ""

    Text(
        text = sign + formatted,
        style = style
    )
}
