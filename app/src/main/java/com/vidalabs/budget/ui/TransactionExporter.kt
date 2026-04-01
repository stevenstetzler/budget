package com.vidalabs.budget.ui

import com.vidalabs.budget.data.TransactionRow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Shared serialization record used for both JSON import and JSON export.
 * Amounts are stored signed: negative for expenses, positive for income.
 */
@Serializable
internal data class JsonImportRecord(
    val date: String,
    val category: String,
    val isPositive: Boolean,
    val amount: Double,
    val description: String? = null
)

private val prettyJson = Json { prettyPrint = true }

/**
 * Builds a CSV string from [transactions].
 * Amounts are emitted signed (negative for expenses, positive for income)
 * so that polarity is visible directly in the amount column.
 */
internal fun buildTransactionCsv(transactions: List<TransactionRow>): String {
    val sb = StringBuilder()
    sb.appendLine("date,category,amount,description,isPositive")
    for (t in transactions) {
        val date = LocalDate.ofEpochDay(t.epochDay).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val category = escapeCsvField(t.categoryName)
        val amount = t.amount
        val description = t.description?.let { escapeCsvField(it) } ?: ""
        sb.appendLine("$date,$category,$amount,$description,${t.isPositive}")
    }
    return sb.toString()
}

/**
 * Builds a pretty-printed JSON string from [transactions].
 * Amounts are emitted signed (negative for expenses, positive for income).
 */
internal fun buildTransactionJson(transactions: List<TransactionRow>): String {
    val records = transactions.map { t ->
        JsonImportRecord(
            date = LocalDate.ofEpochDay(t.epochDay).format(DateTimeFormatter.ISO_LOCAL_DATE),
            category = t.categoryName,
            isPositive = t.isPositive,
            amount = t.amount,
            description = t.description
        )
    }
    return prettyJson.encodeToString(records)
}

/** Escapes a single CSV field per RFC 4180 (quotes, commas, newlines). */
internal fun escapeCsvField(field: String): String {
    return if (field.contains(',') || field.contains('"') || field.contains('\n')) {
        "\"${field.replace("\"", "\"\"")}\""
    } else {
        field
    }
}
