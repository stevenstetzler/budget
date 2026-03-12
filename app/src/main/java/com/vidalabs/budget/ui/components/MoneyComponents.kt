package com.vidalabs.budget.ui.components

import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

fun formatMoney(x: Double): String = "$" + String.format("%,.2f", x)

fun formatMonthYear(ym: YearMonth): String {
    val month = ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return "$month ${ym.year}"
}
