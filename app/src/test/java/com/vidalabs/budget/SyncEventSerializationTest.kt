package com.vidalabs.budget

import com.vidalabs.budget.sync.SyncEvent
import com.vidalabs.budget.sync.SyncJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEventSerializationTest {

    // ── UpsertCategory ──────────────────────────────────────────────────────

    // Verifies the full serialization contract: field names, types, and the
    // sealed-class discriminator all survive a JSON round-trip via SyncJson.
    @Test
    fun `UpsertCategory round-trips through JSON`() {
        val event = SyncEvent.UpsertCategory(
            eventId = "device-1:1",
            deviceId = "device-1",
            seq = 1L,
            ts = 1_700_000_000_000L,
            uid = "cat-uid-123",
            name = "groceries",
            isPositive = false,
            updatedAt = 1_700_000_000_000L,
            deleted = false
        )

        val json = SyncJson.encodeToString(event)
        val decoded = SyncJson.decodeFromString<SyncEvent>(json)

        assertTrue(decoded is SyncEvent.UpsertCategory)
        val decoded2 = decoded as SyncEvent.UpsertCategory
        assertEquals(event.eventId, decoded2.eventId)
        assertEquals(event.deviceId, decoded2.deviceId)
        assertEquals(event.seq, decoded2.seq)
        assertEquals(event.ts, decoded2.ts)
        assertEquals(event.uid, decoded2.uid)
        assertEquals(event.name, decoded2.name)
        assertEquals(event.isPositive, decoded2.isPositive)
        assertEquals(event.updatedAt, decoded2.updatedAt)
        assertEquals(event.deleted, decoded2.deleted)
    }

    // `deleted` defaults to false; this verifies the non-default value is
    // explicitly encoded rather than silently omitted by the serializer.
    @Test
    fun `UpsertCategory deleted flag round-trips correctly`() {
        val event = SyncEvent.UpsertCategory(
            eventId = "dev:3",
            deviceId = "dev",
            seq = 3L,
            ts = 2_000L,
            uid = "old-cat",
            name = "obsolete",
            isPositive = false,
            updatedAt = 2_000L,
            deleted = true
        )

        val decoded = SyncJson.decodeFromString<SyncEvent>(SyncJson.encodeToString(event))
                as SyncEvent.UpsertCategory
        assertTrue(decoded.deleted)
    }

    // ── UpsertReceipt ───────────────────────────────────────────────────────

    // Verifies the receipt-specific fields (epochDay, signed amount, categoryUid)
    // survive serialization without loss or sign-flip.
    @Test
    fun `UpsertReceipt round-trips through JSON`() {
        val event = SyncEvent.UpsertReceipt(
            eventId = "device-1:10",
            deviceId = "device-1",
            seq = 10L,
            ts = 1_700_100_000_000L,
            uid = "receipt-abc",
            epochDay = 19_500L,
            amount = -45.50,
            description = "Weekly shop",
            categoryUid = "cat-uid-123",
            updatedAt = 1_700_100_000_000L,
            deleted = false
        )

        val json = SyncJson.encodeToString(event)
        val decoded = SyncJson.decodeFromString<SyncEvent>(json) as SyncEvent.UpsertReceipt

        assertEquals(event.uid, decoded.uid)
        assertEquals(event.epochDay, decoded.epochDay)
        assertEquals(event.amount, decoded.amount, 0.0001)
        assertEquals(event.description, decoded.description)
        assertEquals(event.categoryUid, decoded.categoryUid)
        assertFalse(decoded.deleted)
    }

    // `description` is nullable; serializers can silently drop null fields or
    // fail to restore them, so this explicitly verifies null survives the round-trip.
    @Test
    fun `UpsertReceipt with null description round-trips correctly`() {
        val event = SyncEvent.UpsertReceipt(
            eventId = "dev:11",
            deviceId = "dev",
            seq = 11L,
            ts = 1_000L,
            uid = "r-no-desc",
            epochDay = 1L,
            amount = -10.0,
            description = null,
            categoryUid = "cat-1",
            updatedAt = 1_000L,
            deleted = false
        )

        val decoded = SyncJson.decodeFromString<SyncEvent>(SyncJson.encodeToString(event))
                as SyncEvent.UpsertReceipt
        assertNull(decoded.description)
    }

    // ── UpsertBudgetItem ────────────────────────────────────────────────────

    // Verifies the budget-item-specific fields (monthKey integer, budget value)
    // round-trip correctly through JSON.
    @Test
    fun `UpsertBudgetItem round-trips through JSON`() {
        val event = SyncEvent.UpsertBudgetItem(
            eventId = "device-1:20",
            deviceId = "device-1",
            seq = 20L,
            ts = 1_700_200_000_000L,
            categoryUid = "cat-uid-123",
            monthKey = 202601,
            value = 300.0,
            updatedAt = 1_700_200_000_000L,
            deleted = false
        )

        val json = SyncJson.encodeToString(event)
        val decoded = SyncJson.decodeFromString<SyncEvent>(json) as SyncEvent.UpsertBudgetItem

        assertEquals(event.categoryUid, decoded.categoryUid)
        assertEquals(event.monthKey, decoded.monthKey)
        assertEquals(event.value, decoded.value, 0.0001)
        assertEquals(event.updatedAt, decoded.updatedAt)
        assertFalse(decoded.deleted)
    }

    // ── Type discriminator ──────────────────────────────────────────────────

    // SyncJson is configured with classDiscriminator = "type". This verifies
    // that config is in effect: a wrong or missing discriminator key would make
    // all polymorphic decodes fail at runtime.
    @Test
    fun `JSON type discriminator is present in encoded output`() {
        val category = SyncEvent.UpsertCategory(
            eventId = "d:1", deviceId = "d", seq = 1L, ts = 1L,
            uid = "u", name = "n", isPositive = false, updatedAt = 1L
        )
        val receipt = SyncEvent.UpsertReceipt(
            eventId = "d:2", deviceId = "d", seq = 2L, ts = 1L,
            uid = "r", epochDay = 1L, amount = 1.0, categoryUid = "c", updatedAt = 1L
        )
        val budget = SyncEvent.UpsertBudgetItem(
            eventId = "d:3", deviceId = "d", seq = 3L, ts = 1L,
            categoryUid = "c", monthKey = 202601, value = 100.0, updatedAt = 1L
        )

        assertTrue(SyncJson.encodeToString(category).contains("\"type\":\"UpsertCategory\""))
        assertTrue(SyncJson.encodeToString(receipt).contains("\"type\":\"UpsertReceipt\""))
        assertTrue(SyncJson.encodeToString(budget).contains("\"type\":\"UpsertBudgetItem\""))
    }
}
