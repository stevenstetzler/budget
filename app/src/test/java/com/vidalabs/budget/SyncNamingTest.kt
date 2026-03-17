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

    // The format string uses %02d, so single-digit months must be zero-padded.
    // Without the pad, files for March would sort after December lexicographically.
    @Test
    fun `monthDirFromTs zero-pads single-digit months`() {
        val ts = Instant.parse("2026-03-01T00:00:00Z").toEpochMilli()
        assertEquals("2026-03", monthDirFromTs(ts))
    }

    // Events are filed by UTC month. If the implementation used the system
    // time-zone instead of UTC, a timestamp at Dec 31 23:30 UTC could be
    // filed under January in UTC+1, corrupting event ordering across devices.
    @Test
    fun `monthDirFromTs uses UTC not local time`() {
        val ts = Instant.parse("2025-12-31T23:30:00Z").toEpochMilli()
        assertEquals("2025-12", monthDirFromTs(ts))
    }

    // ── filenameFor ──────────────────────────────────────────────────────────

    // Pins the complete filename contract: deviceId prefix, ISO timestamp with
    // colons replaced by hyphens, 6-digit zero-padded sequence, .json extension.
    // Any change to this format breaks reading previously written event files.
    @Test
    fun `filenameFor produces expected filename format`() {
        val ts = Instant.parse("2026-01-15T10:30:00Z").toEpochMilli()
        val result = filenameFor("device-1", ts, 1L)
        assertEquals("device-1_2026-01-15T10-30-00Z_000001.json", result)
    }

    // Sequence numbers are zero-padded to 6 digits so filenames sort correctly
    // lexicographically. Without padding, seq 2 would sort after seq 10.
    @Test
    fun `filenameFor pads sequence number to 6 digits`() {
        val ts = Instant.parse("2026-06-01T00:00:00Z").toEpochMilli()
        val result = filenameFor("dev", ts, 42L)
        assertTrue(result.endsWith("_000042.json"))
    }

    // ISO 8601 timestamps contain colons (e.g. "10:30:00") which are illegal in
    // filenames on Windows. This verifies the sanitization step is applied.
    @Test
    fun `filenameFor replaces colons in ISO timestamp with hyphens`() {
        val ts = Instant.parse("2026-03-17T12:45:30Z").toEpochMilli()
        val result = filenameFor("myDevice", ts, 5L)
        assertFalse("Filename should not contain colons: $result", result.contains(':'))
    }
}
