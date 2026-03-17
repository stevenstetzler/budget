package com.vidalabs.budget

import com.vidalabs.budget.sync.filenameFor
import com.vidalabs.budget.sync.monthDirFromTs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SyncNamingTest {

    // ── monthDirFromTs ───────────────────────────────────────────────────────

    @Test
    fun `monthDirFromTs returns correct YYYY-MM format for January`() {
        // 2026-01-15 00:00:00 UTC
        val ts = Instant.parse("2026-01-15T00:00:00Z").toEpochMilli()
        assertEquals("2026-01", monthDirFromTs(ts))
    }

    @Test
    fun `monthDirFromTs returns correct YYYY-MM format for December`() {
        // 2025-12-31 23:59:59 UTC
        val ts = Instant.parse("2025-12-31T23:59:59Z").toEpochMilli()
        assertEquals("2025-12", monthDirFromTs(ts))
    }

    @Test
    fun `monthDirFromTs zero-pads single-digit months`() {
        // 2026-03-01 UTC
        val ts = Instant.parse("2026-03-01T00:00:00Z").toEpochMilli()
        val result = monthDirFromTs(ts)
        assertEquals("2026-03", result)
    }

    @Test
    fun `monthDirFromTs uses UTC not local time`() {
        // A timestamp that is Dec 31 in UTC but might be Jan 1 in UTC+1 has its UTC date used.
        // 2025-12-31 23:30:00 UTC  →  2025-12
        val ts = Instant.parse("2025-12-31T23:30:00Z").toEpochMilli()
        assertEquals("2025-12", monthDirFromTs(ts))
    }

    // ── filenameFor ──────────────────────────────────────────────────────────

    @Test
    fun `filenameFor produces expected filename format`() {
        val ts = Instant.parse("2026-01-15T10:30:00Z").toEpochMilli()
        val result = filenameFor("device-1", ts, 1L)
        // Expected: device-1_2026-01-15T10-30-00Z_000001.json
        assertEquals("device-1_2026-01-15T10-30-00Z_000001.json", result)
    }

    @Test
    fun `filenameFor pads sequence number to 6 digits`() {
        val ts = Instant.parse("2026-06-01T00:00:00Z").toEpochMilli()
        val result = filenameFor("dev", ts, 42L)
        assertTrue(result.endsWith("_000042.json"))
    }

    @Test
    fun `filenameFor handles maximum sequence number without truncation`() {
        val ts = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        val result = filenameFor("dev", ts, 999_999L)
        assertTrue(result.endsWith("_999999.json"))
    }

    @Test
    fun `filenameFor replaces colons in ISO timestamp with hyphens`() {
        val ts = Instant.parse("2026-03-17T12:45:30Z").toEpochMilli()
        val result = filenameFor("myDevice", ts, 5L)
        assertFalse("Filename should not contain colons: $result", result.contains(':'))
    }

    @Test
    fun `filenameFor starts with the deviceId`() {
        val ts = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        val result = filenameFor("phone-abc", ts, 1L)
        assertTrue(result.startsWith("phone-abc_"))
    }

    @Test
    fun `filenameFor ends with json extension`() {
        val ts = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        val result = filenameFor("dev", ts, 1L)
        assertTrue(result.endsWith(".json"))
    }
}
