package com.vidalabs.budget

import com.vidalabs.budget.data.DEFAULT_CATEGORIES
import com.vidalabs.budget.ui.toMonthKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class BudgetLogicTest {

    // ── YearMonth.toMonthKey() ───────────────────────────────────────────────

    // Anchors the integer encoding contract used as a database primary key component.
    // Any change to the YYYYMM format would silently corrupt all budget queries.
    @Test
    fun `toMonthKey encodes January 2026 as 202601`() {
        assertEquals(202601, YearMonth.of(2026, 1).toMonthKey())
    }

    // Month keys are used for ordering and range queries. A formula mistake
    // (e.g. month * year) could produce non-monotonic values within a year.
    @Test
    fun `toMonthKey consecutive months produce increasing keys`() {
        val jan = YearMonth.of(2026, 1).toMonthKey()
        val feb = YearMonth.of(2026, 2).toMonthKey()
        val dec = YearMonth.of(2026, 12).toMonthKey()
        assertTrue(jan < feb)
        assertTrue(feb < dec)
    }

    // December (month 12) followed by January (month 1) is the trickiest boundary:
    // an incorrect formula could produce Dec 2025 > Jan 2026 and break month navigation.
    @Test
    fun `toMonthKey December to January year boundary increases key`() {
        val dec2025 = YearMonth.of(2025, 12).toMonthKey()
        val jan2026 = YearMonth.of(2026, 1).toMonthKey()
        assertTrue(jan2026 > dec2025)
    }

    // ── DEFAULT_CATEGORIES ───────────────────────────────────────────────────

    // "income" is referenced by the UI and export logic; if its isPositive flag
    // were accidentally flipped, all income transactions would be sign-reversed.
    @Test
    fun `DEFAULT_CATEGORIES contains income as a positive category`() {
        val income = DEFAULT_CATEGORIES.find { it.name == "income" }
        assertTrue("Expected 'income' category to exist", income != null)
        assertTrue("Expected 'income' to be isPositive=true", income!!.isPositive)
    }

    // "grocery" is a concrete expense category; verifies the isPositive=false
    // convention for expense categories is correctly set in the defaults.
    @Test
    fun `DEFAULT_CATEGORIES contains grocery as a negative (expense) category`() {
        val grocery = DEFAULT_CATEGORIES.find { it.name == "grocery" }
        assertTrue("Expected 'grocery' category to exist", grocery != null)
        assertFalse("Expected 'grocery' to be isPositive=false", grocery!!.isPositive)
    }

    // Category creation does a name-based lookup; duplicates in the default list
    // would cause silent data-integrity issues at first app launch.
    @Test
    fun `DEFAULT_CATEGORIES has no duplicate names`() {
        val names = DEFAULT_CATEGORIES.map { it.name }
        assertEquals("Duplicate category names found", names.size, names.toSet().size)
    }
}
