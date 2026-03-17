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

    @Test
    fun `toMonthKey encodes January 2026 as 202601`() {
        assertEquals(202601, YearMonth.of(2026, 1).toMonthKey())
    }

    @Test
    fun `toMonthKey encodes December 2025 as 202512`() {
        assertEquals(202512, YearMonth.of(2025, 12).toMonthKey())
    }

    @Test
    fun `toMonthKey encodes March 2026 as 202603`() {
        assertEquals(202603, YearMonth.of(2026, 3).toMonthKey())
    }

    @Test
    fun `toMonthKey result has 6 digits for modern years`() {
        val key = YearMonth.of(2026, 7).toMonthKey()
        assertEquals(6, key.toString().length)
    }

    @Test
    fun `toMonthKey first two digits are year divided by 100`() {
        val ym = YearMonth.of(2030, 6)
        val key = ym.toMonthKey()
        assertEquals(ym.year, key / 100)
    }

    @Test
    fun `toMonthKey last two digits are month value`() {
        val ym = YearMonth.of(2026, 9)
        val key = ym.toMonthKey()
        assertEquals(ym.monthValue, key % 100)
    }

    @Test
    fun `toMonthKey consecutive months produce increasing keys`() {
        val jan = YearMonth.of(2026, 1).toMonthKey()
        val feb = YearMonth.of(2026, 2).toMonthKey()
        val dec = YearMonth.of(2026, 12).toMonthKey()
        assertTrue(jan < feb)
        assertTrue(feb < dec)
    }

    @Test
    fun `toMonthKey December to January year boundary increases key`() {
        val dec2025 = YearMonth.of(2025, 12).toMonthKey()
        val jan2026 = YearMonth.of(2026, 1).toMonthKey()
        assertTrue(jan2026 > dec2025)
    }

    // ── DEFAULT_CATEGORIES ───────────────────────────────────────────────────

    @Test
    fun `DEFAULT_CATEGORIES is not empty`() {
        assertTrue(DEFAULT_CATEGORIES.isNotEmpty())
    }

    @Test
    fun `DEFAULT_CATEGORIES contains income as a positive category`() {
        val income = DEFAULT_CATEGORIES.find { it.name == "income" }
        assertTrue("Expected 'income' category to exist", income != null)
        assertTrue("Expected 'income' to be isPositive=true", income!!.isPositive)
    }

    @Test
    fun `DEFAULT_CATEGORIES contains grocery as a negative (expense) category`() {
        val grocery = DEFAULT_CATEGORIES.find { it.name == "grocery" }
        assertTrue("Expected 'grocery' category to exist", grocery != null)
        assertFalse("Expected 'grocery' to be isPositive=false", grocery!!.isPositive)
    }

    @Test
    fun `DEFAULT_CATEGORIES has no duplicate names`() {
        val names = DEFAULT_CATEGORIES.map { it.name }
        assertEquals("Duplicate category names found", names.size, names.toSet().size)
    }

    @Test
    fun `DEFAULT_CATEGORIES contains at least one positive and one negative category`() {
        val hasPositive = DEFAULT_CATEGORIES.any { it.isPositive }
        val hasNegative = DEFAULT_CATEGORIES.any { !it.isPositive }
        assertTrue("Expected at least one income category", hasPositive)
        assertTrue("Expected at least one expense category", hasNegative)
    }
}
