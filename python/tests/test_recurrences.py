"""Tests for the recurring expenses feature.

Verifies:
1. Creating a recurrence via POST /recurrences/ populates validity_lookup.
2. GET /recurrences/ returns the created recurrence.
3. GET /validity-lookup/recurrence/{id} returns entries.
4. PATCH /validity-lookup/{id} toggles isActive.
5. GET /receipts/?targetMonth=... includes recurring receipts.
6. GET /receipts/?targetMonth=... excludes inactive recurring receipts.
7. Deleting a recurrence removes validity_lookup entries and clears receipt.recurrenceId.
"""

import uuid
from datetime import date, timedelta

import pytest

CATEGORY_UID = "test-cat-recur-001"
RECEIPT_UID = "test-receipt-recur-001"

_EPOCH = date(1970, 1, 1)


def _epoch_day(d: date) -> int:
    return (d - _EPOCH).days


def _first_of_next_month(d: date) -> date:
    """Return the first day of the month following *d*."""
    if d.month == 12:
        return date(d.year + 1, 1, 1)
    return date(d.year, d.month + 1, 1)


# Use months relative to today so validity_lookup is always within the
# 12-month lookahead window, regardless of when the tests are executed.
_today = date.today()
# MONTH_START: first day of next month  (always within lookahead)
_MONTH_START_DATE = _first_of_next_month(_today)
# MONTH_NEXT: first day of the month after MONTH_START
_MONTH_NEXT_DATE = _first_of_next_month(_MONTH_START_DATE)

MONTH_START = _epoch_day(_MONTH_START_DATE)
MONTH_NEXT = _epoch_day(_MONTH_NEXT_DATE)

# Receipt falls in MONTH_START (day 15, or last day if month has < 15 days)
import calendar as _calendar
_receipt_day = min(15, _calendar.monthrange(_MONTH_START_DATE.year, _MONTH_START_DATE.month)[1])
RECEIPT_EPOCH_DAY = _epoch_day(date(_MONTH_START_DATE.year, _MONTH_START_DATE.month, _receipt_day))


@pytest.fixture(autouse=True)
def seed_db(client):
    """Insert a category and a receipt that will be used across tests."""
    client.post(
        "/categories/",
        json={
            "uid": CATEGORY_UID,
            "name": "subscriptions",
            "isPositive": False,
            "updatedAt": 2_000_000,
            "deleted": False,
        },
    )
    client.post(
        "/receipts/",
        json={
            "uid": RECEIPT_UID,
            "epochDay": RECEIPT_EPOCH_DAY,
            "amount": -9.99,
            "description": "Netflix",
            "categoryUid": CATEGORY_UID,
            "updatedAt": 2_000_001,
            "deleted": False,
        },
    )


# ---------------------------------------------------------------------------
# Recurrence CRUD
# ---------------------------------------------------------------------------


def test_post_recurrence_creates_validity_lookup(client):
    """POST /recurrences/ creates a recurrence and pre-populates validity_lookup."""
    rec_id = str(uuid.uuid4())
    payload = {
        "id": rec_id,
        "receiptId": RECEIPT_UID,
        "frequency": "MONTHLY",
        "startDate": MONTH_START,
        "endDate": None,
        "dayOfPeriod": 15,
    }
    resp = client.post("/recurrences/", json=payload)
    assert resp.status_code == 200
    data = resp.json()
    assert data["id"] == rec_id
    assert data["frequency"] == "MONTHLY"

    # validity_lookup should have been pre-populated
    vl_resp = client.get(f"/validity-lookup/recurrence/{rec_id}")
    assert vl_resp.status_code == 200
    entries = vl_resp.json()
    assert len(entries) >= 1
    # MONTH_START should be active
    start_entries = [e for e in entries if e["targetMonth"] == MONTH_START]
    assert len(start_entries) == 1
    assert start_entries[0]["isActive"] is True

    # Clean up
    client.delete(f"/recurrences/{rec_id}")


def test_get_recurrences(client):
    """GET /recurrences/ returns created recurrences."""
    rec_id = str(uuid.uuid4())
    client.post(
        "/recurrences/",
        json={
            "id": rec_id,
            "receiptId": RECEIPT_UID,
            "frequency": "MONTHLY",
            "startDate": MONTH_START,
            "endDate": None,
            "dayOfPeriod": 1,
        },
    )
    resp = client.get("/recurrences/")
    assert resp.status_code == 200
    ids = [r["id"] for r in resp.json()]
    assert rec_id in ids

    client.delete(f"/recurrences/{rec_id}")


# ---------------------------------------------------------------------------
# validity_lookup toggle
# ---------------------------------------------------------------------------


def test_toggle_validity_lookup_inactive(client):
    """PATCH /validity-lookup/{id} can set isActive=False for a month."""
    rec_id = str(uuid.uuid4())
    client.post(
        "/recurrences/",
        json={
            "id": rec_id,
            "receiptId": RECEIPT_UID,
            "frequency": "MONTHLY",
            "startDate": MONTH_START,
            "endDate": None,
            "dayOfPeriod": 15,
        },
    )

    # Find the validity_lookup entry for MONTH_START
    vl_entries = client.get(f"/validity-lookup/recurrence/{rec_id}").json()
    start_entry = next(e for e in vl_entries if e["targetMonth"] == MONTH_START)

    # Toggle off
    patch_resp = client.patch(
        f"/validity-lookup/{start_entry['id']}", json={"isActive": False}
    )
    assert patch_resp.status_code == 200
    assert patch_resp.json()["isActive"] is False

    # Toggle back on
    patch_resp2 = client.patch(
        f"/validity-lookup/{start_entry['id']}", json={"isActive": True}
    )
    assert patch_resp2.status_code == 200
    assert patch_resp2.json()["isActive"] is True

    client.delete(f"/recurrences/{rec_id}")


# ---------------------------------------------------------------------------
# Receipts with targetMonth query
# ---------------------------------------------------------------------------


def test_receipts_for_target_month_includes_recurring(client):
    """GET /receipts/?targetMonth=... includes active recurring receipts."""
    rec_id = str(uuid.uuid4())
    # Create monthly recurrence starting at MONTH_START
    client.post(
        "/recurrences/",
        json={
            "id": rec_id,
            "receiptId": RECEIPT_UID,
            "frequency": "MONTHLY",
            "startDate": MONTH_START,
            "endDate": None,
            "dayOfPeriod": 15,
        },
    )

    # MONTH_NEXT has no regular receipt but the recurrence should include it
    resp = client.get(f"/receipts/?targetMonth={MONTH_NEXT}")
    assert resp.status_code == 200
    result = resp.json()
    uids = [r["uid"] for r in result]
    assert RECEIPT_UID in uids
    # Recurring receipt should have occurrenceEpochDay set to the target month
    receipt_data = next(r for r in result if r["uid"] == RECEIPT_UID)
    assert receipt_data["occurrenceEpochDay"] == MONTH_NEXT

    client.delete(f"/recurrences/{rec_id}")


def test_receipts_for_target_month_excludes_inactive_recurring(client):
    """GET /receipts/?targetMonth=... excludes inactive recurring receipts."""
    rec_id = str(uuid.uuid4())
    client.post(
        "/recurrences/",
        json={
            "id": rec_id,
            "receiptId": RECEIPT_UID,
            "frequency": "MONTHLY",
            "startDate": MONTH_START,
            "endDate": None,
            "dayOfPeriod": 15,
        },
    )

    # Disable the MONTH_NEXT entry
    vl_entries = client.get(f"/validity-lookup/recurrence/{rec_id}").json()
    next_entry = next(
        (e for e in vl_entries if e["targetMonth"] == MONTH_NEXT), None
    )
    # The entry MUST exist for this test to be meaningful
    assert next_entry is not None, (
        f"Expected a validity_lookup entry for MONTH_NEXT ({MONTH_NEXT}); "
        f"found entries: {[e['targetMonth'] for e in vl_entries]}"
    )
    client.patch(f"/validity-lookup/{next_entry['id']}", json={"isActive": False})

    # MONTH_NEXT should NOT include the recurring receipt now
    resp = client.get(f"/receipts/?targetMonth={MONTH_NEXT}")
    assert resp.status_code == 200
    uids = [r["uid"] for r in resp.json()]
    assert RECEIPT_UID not in uids

    client.delete(f"/recurrences/{rec_id}")


# ---------------------------------------------------------------------------
# Delete recurrence
# ---------------------------------------------------------------------------


def test_delete_recurrence_clears_validity_lookup(client):
    """DELETE /recurrences/{id} removes validity_lookup entries."""
    rec_id = str(uuid.uuid4())
    client.post(
        "/recurrences/",
        json={
            "id": rec_id,
            "receiptId": RECEIPT_UID,
            "frequency": "MONTHLY",
            "startDate": MONTH_START,
            "endDate": None,
            "dayOfPeriod": 1,
        },
    )
    # Ensure entries exist
    vl_resp = client.get(f"/validity-lookup/recurrence/{rec_id}")
    assert len(vl_resp.json()) > 0

    # Delete
    del_resp = client.delete(f"/recurrences/{rec_id}")
    assert del_resp.status_code == 200

    # Entries should be gone
    vl_resp2 = client.get(f"/validity-lookup/recurrence/{rec_id}")
    assert vl_resp2.json() == []

    # Receipt should have recurrenceId cleared
    receipt_resp = client.get("/receipts/")
    receipt = next(r for r in receipt_resp.json() if r["uid"] == RECEIPT_UID)
    assert receipt["recurrenceId"] is None
