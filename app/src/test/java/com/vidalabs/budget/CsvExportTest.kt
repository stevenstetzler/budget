package com.vidalabs.budget

import com.vidalabs.budget.data.TransactionRow
import com.vidalabs.budget.ui.buildTransactionCsv
import com.vidalabs.budget.ui.buildTransactionJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

/**
 * Tests that CSV/JSON export preserves the sign (+/-) of transaction amounts.
 *
 * TransactionRow.amount is stored as a signed value in the database
 * (negative for expenses, positive for income). The exported CSV/JSON should
 * reflect this sign so that users can distinguish income from expenses
 * without relying solely on the isPositive boolean column.
 */
class CsvExportTest {

    // Parses the amount field from a CSV data line.
    // All tests use descriptions without commas so a simple split is safe here.
    private fun parseAmountFromCsvLine(csvLine: String): Double {
        return csvLine.trim().split(",")[2].toDouble()
    }

    private val epochDay2024 = LocalDate.of(2024, 1, 1).toEpochDay()

    // Expense amounts are stored as negative in the DB. Exporting abs() would
    // hide the polarity; the signed value must be preserved in the CSV.
    @Test
    fun `expense transaction exports with negative amount`() {
        val expense = TransactionRow(
            uid = "uid1",
            epochDay = epochDay2024,
            amount = -50.0,   // stored as negative in DB
            description = null,
            categoryName = "grocery",
            isPositive = false
        )
        val csv = buildTransactionCsv(listOf(expense))
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)  // header + 1 data row
        val exportedAmount = parseAmountFromCsvLine(lines[1])
        assertTrue("Expense amount should be negative in export", exportedAmount < 0)
        assertEquals(-50.0, exportedAmount, 0.001)
    }

    // Income amounts are stored as positive in the DB. The positive value must
    // be preserved in the exported CSV.
    @Test
    fun `income transaction exports with positive amount`() {
        val income = TransactionRow(
            uid = "uid2",
            epochDay = epochDay2024,
            amount = 100.0,   // stored as positive in DB
            description = null,
            categoryName = "income",
            isPositive = true
        )
        val csv = buildTransactionCsv(listOf(income))
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        val exportedAmount = parseAmountFromCsvLine(lines[1])
        assertTrue("Income amount should be positive in export", exportedAmount > 0)
        assertEquals(100.0, exportedAmount, 0.001)
    }

    // The isPositive column must still be present so that import can correctly
    // route amounts to the right category and recompute the signed value.
    @Test
    fun `exported CSV includes isPositive column`() {
        val expense = TransactionRow(
            uid = "uid3",
            epochDay = epochDay2024,
            amount = -30.0,
            description = null,
            categoryName = "restaurant",
            isPositive = false
        )
        val csv = buildTransactionCsv(listOf(expense))
        val headerCols = csv.trim().lines()[0].split(",")
        assertTrue("CSV header must include isPositive column",
            headerCols.any { it.trim() == "isPositive" })
        // The data row should contain "false" for an expense
        assertTrue("Data row should contain isPositive=false for expense",
            csv.trim().lines()[1].endsWith(",false"))
    }

    // Re-importing a signed amount using abs() should round-trip correctly.
    // This validates the backward-compatible import strategy.
    @Test
    fun `abs of exported expense amount equals the original magnitude`() {
        val originalMagnitude = 75.0
        val expense = TransactionRow(
            uid = "uid4",
            epochDay = epochDay2024,
            amount = -originalMagnitude,
            description = null,
            categoryName = "grocery",
            isPositive = false
        )
        val csv = buildTransactionCsv(listOf(expense))
        val exportedAmount = parseAmountFromCsvLine(csv.trim().lines()[1])
        // Import uses abs() on the amount before passing to importTransaction
        val importedAsPositive = abs(exportedAmount)
        assertEquals(originalMagnitude, importedAsPositive, 0.001)
    }

    // JSON export must also preserve the sign so that the amount field is
    // informative without requiring callers to cross-reference isPositive.
    @Test
    fun `expense transaction JSON exports with negative amount`() {
        val expense = TransactionRow(
            uid = "uid5",
            epochDay = epochDay2024,
            amount = -42.5,
            description = null,
            categoryName = "grocery",
            isPositive = false
        )
        val json = buildTransactionJson(listOf(expense))
        // Verify the raw JSON string contains the signed amount
        assertTrue("JSON export should contain negative amount", json.contains("-42.5"))
        assertFalse("JSON export should not contain unsigned amount only", json.contains("\"amount\": 42.5"))
    }

    // JSON export of income must remain positive.
    @Test
    fun `income transaction JSON exports with positive amount`() {
        val income = TransactionRow(
            uid = "uid6",
            epochDay = epochDay2024,
            amount = 200.0,
            description = null,
            categoryName = "income",
            isPositive = true
        )
        val json = buildTransactionJson(listOf(income))
        assertTrue("JSON export should contain positive amount", json.contains("200.0"))
        assertTrue("JSON export should reflect isPositive=true", json.contains("\"isPositive\": true"))
    }
}
