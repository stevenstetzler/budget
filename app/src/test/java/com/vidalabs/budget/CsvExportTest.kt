package com.vidalabs.budget

import com.vidalabs.budget.data.TransactionRow
import com.vidalabs.budget.ui.buildTransactionCsv
import com.vidalabs.budget.ui.buildTransactionJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
/**
 * Tests that CSV/JSON export preserves the sign (+/-) of transaction amounts.
 *
 * The CSV/JSON `amount` column uses the user-facing signed convention:
 *   - positive = natural direction of the category (expense = cash outflow, income = cash inflow)
 *   - negative = reversal (expense refund or income loss)
 *
 * Import round-trip formula: `amountPositive = csvAmount`
 * (The exported amount IS the amountPositive value; importTransaction then applies the
 * category sign internally via `if (isPositive) amountPositive else -amountPositive`.)
 */
class CsvExportTest {

    // Parses the amount field from a CSV data line.
    // All tests use descriptions without commas so a simple split is safe here.
    private fun parseAmountFromCsvLine(csvLine: String): Double {
        return csvLine.trim().split(",")[2].toDouble()
    }

    private val epochDay2024 = LocalDate.of(2024, 1, 1).toEpochDay()

    // Normal expense: DB stores -50.0 (negative = cash outflow). The export uses
    // the user-facing convention where a positive number means "I spent money",
    // so it should export as +50.0 (positive magnitude for a cash outflow).
    @Test
    fun `expense transaction exports with positive amount`() {
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
        assertTrue("Expense amount should be positive in export (user-facing convention)", exportedAmount > 0)
        assertEquals(50.0, exportedAmount, 0.001)
    }

    // Income amounts are stored as positive in the DB and represent positive cash flow.
    // The export should preserve the positive value.
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

    // An income reversal/loss: DB stores -200.0. Export should show -200.0 (negative
    // income = cash outflow). isPositive=true so the sign is preserved as-is.
    @Test
    fun `negative income (reversal) exports with negative amount`() {
        val negativeIncome = TransactionRow(
            uid = "uid_neg_income",
            epochDay = epochDay2024,
            amount = -200.0,  // income reversal stored negative in DB
            description = null,
            categoryName = "income",
            isPositive = true
        )
        val csv = buildTransactionCsv(listOf(negativeIncome))
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        val exportedAmount = parseAmountFromCsvLine(lines[1])
        assertTrue("Income reversal should export as negative", exportedAmount < 0)
        assertEquals(-200.0, exportedAmount, 0.001)
    }

    // An expense refund: DB stores +10.0. Export should show -10.0 (negative expense =
    // cash inflow / refund). isPositive=false so the sign is flipped on export.
    @Test
    fun `expense refund (positive DB) exports with negative amount`() {
        val refund = TransactionRow(
            uid = "uid_refund",
            epochDay = epochDay2024,
            amount = 10.0,   // refund stored positive in DB
            description = null,
            categoryName = "grocery",
            isPositive = false
        )
        val csv = buildTransactionCsv(listOf(refund))
        val lines = csv.trim().lines()
        assertEquals(2, lines.size)
        val exportedAmount = parseAmountFromCsvLine(lines[1])
        assertTrue("Expense refund should export as negative", exportedAmount < 0)
        assertEquals(-10.0, exportedAmount, 0.001)
    }

    // The isPositive column must still be present so that import can correctly
    // route amounts to the right category.
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

    // The import formula is amountPositive = csvAmount (no transformation).
    // importTransaction then applies the category sign:
    //   stored = if (isPositive) amountPositive else -amountPositive
    // For a normal expense (DB = -75.0 → exported as +75.0):
    //   amountPositive = 75.0 → stored = -75.0 ✓
    @Test
    fun `import formula round-trips a normal expense correctly`() {
        val originalDbAmount = -75.0
        val expense = TransactionRow(
            uid = "uid4",
            epochDay = epochDay2024,
            amount = originalDbAmount,
            description = null,
            categoryName = "grocery",
            isPositive = false
        )
        val csv = buildTransactionCsv(listOf(expense))
        val exportedAmount = parseAmountFromCsvLine(csv.trim().lines()[1])
        // Import formula: amountPositive = csvAmount
        val amountPositive = exportedAmount
        // importTransaction stores: if (isPositive) amountPositive else -amountPositive
        val storedAmount = if (expense.isPositive) amountPositive else -amountPositive
        assertEquals(originalDbAmount, storedAmount, 0.001)
    }

    // For a negative income entry (DB = -200.0 → exported as -200.0):
    //   amountPositive = -200.0 → stored = if (true) -200.0 = -200.0 ✓
    @Test
    fun `import formula round-trips negative income correctly`() {
        val originalDbAmount = -200.0
        val negativeIncome = TransactionRow(
            uid = "uid7",
            epochDay = epochDay2024,
            amount = originalDbAmount,
            description = null,
            categoryName = "income",
            isPositive = true
        )
        val csv = buildTransactionCsv(listOf(negativeIncome))
        val exportedAmount = parseAmountFromCsvLine(csv.trim().lines()[1])
        assertEquals(-200.0, exportedAmount, 0.001)
        val amountPositive = exportedAmount
        val storedAmount = if (negativeIncome.isPositive) amountPositive else -amountPositive
        assertEquals(originalDbAmount, storedAmount, 0.001)
    }

    // JSON export must also preserve the user-facing convention.
    // A normal expense (DB = -42.5) should export as +42.5 in JSON.
    @Test
    fun `expense transaction JSON exports with positive amount`() {
        val expense = TransactionRow(
            uid = "uid5",
            epochDay = epochDay2024,
            amount = -42.5,
            description = null,
            categoryName = "grocery",
            isPositive = false
        )
        val json = buildTransactionJson(listOf(expense))
        assertTrue("JSON export should contain positive amount for expense", json.contains("42.5"))
        assertFalse("JSON export should not contain negative amount for normal expense", json.contains("-42.5"))
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
