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
 * Amounts are emitted in the user-facing signed convention:
 * positive means "in the natural direction of the category"
 * (positive expense = cash outflow, positive income = cash inflow).
 * Negative values indicate a reversal (expense refund or income loss).
 * This is the inverse of the DB-signed value for spending categories.
 */
internal fun buildTransactionCsv(transactions: List<TransactionRow>): String {
    val sb = StringBuilder()
    sb.appendLine("date,category,amount,description,isPositive")
    for (t in transactions) {
        val date = LocalDate.ofEpochDay(t.epochDay).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val category = escapeCsvField(t.categoryName)
        val amount = if (t.isPositive) t.amount else -t.amount
        val description = t.description?.let { escapeCsvField(it) } ?: ""
        sb.appendLine("$date,$category,$amount,$description,${t.isPositive}")
    }
    return sb.toString()
}

/**
 * Builds a pretty-printed JSON string from [transactions].
 * Amounts are emitted in the user-facing signed convention (see [buildTransactionCsv]).
 */
internal fun buildTransactionJson(transactions: List<TransactionRow>): String {
    val records = transactions.map { t ->
        JsonImportRecord(
            date = LocalDate.ofEpochDay(t.epochDay).format(DateTimeFormatter.ISO_LOCAL_DATE),
            category = t.categoryName,
            isPositive = t.isPositive,
            amount = if (t.isPositive) t.amount else -t.amount,
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
