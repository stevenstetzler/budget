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

# Epoch day for 2026-01-01 (reference date)
_EPOCH = date(1970, 1, 1)


def _epoch_day(d: date) -> int:
    return (d - _EPOCH).days


MONTH_JAN_2026 = _epoch_day(date(2026, 1, 1))   # 2026-01-01 epoch day
MONTH_FEB_2026 = _epoch_day(date(2026, 2, 1))   # 2026-02-01 epoch day

RECEIPT_EPOCH_DAY = _epoch_day(date(2026, 1, 15))


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
        "startDate": MONTH_JAN_2026,
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
    # Jan 2026 should be active
    jan_entries = [e for e in entries if e["targetMonth"] == MONTH_JAN_2026]
    assert len(jan_entries) == 1
    assert jan_entries[0]["isActive"] is True

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
            "startDate": MONTH_JAN_2026,
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
            "startDate": MONTH_JAN_2026,
            "endDate": None,
            "dayOfPeriod": 15,
        },
    )

    # Find the validity_lookup entry for Jan 2026
    vl_entries = client.get(f"/validity-lookup/recurrence/{rec_id}").json()
    jan_entry = next(e for e in vl_entries if e["targetMonth"] == MONTH_JAN_2026)

    # Toggle off
    patch_resp = client.patch(
        f"/validity-lookup/{jan_entry['id']}", json={"isActive": False}
    )
    assert patch_resp.status_code == 200
    assert patch_resp.json()["isActive"] is False

    # Toggle back on
    patch_resp2 = client.patch(
        f"/validity-lookup/{jan_entry['id']}", json={"isActive": True}
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
    # Create monthly recurrence starting Jan 2026
    client.post(
        "/recurrences/",
        json={
            "id": rec_id,
            "receiptId": RECEIPT_UID,
            "frequency": "MONTHLY",
            "startDate": MONTH_JAN_2026,
            "endDate": None,
            "dayOfPeriod": 15,
        },
    )

    # Feb 2026 has no regular receipt but the recurrence should include it
    resp = client.get(f"/receipts/?targetMonth={MONTH_FEB_2026}")
    assert resp.status_code == 200
    uids = [r["uid"] for r in resp.json()]
    assert RECEIPT_UID in uids

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
            "startDate": MONTH_JAN_2026,
            "endDate": None,
            "dayOfPeriod": 15,
        },
    )

    # Disable the Feb 2026 entry
    vl_entries = client.get(f"/validity-lookup/recurrence/{rec_id}").json()
    feb_entry = next(
        (e for e in vl_entries if e["targetMonth"] == MONTH_FEB_2026), None
    )
    if feb_entry:
        client.patch(f"/validity-lookup/{feb_entry['id']}", json={"isActive": False})

    # Feb 2026 should NOT include the recurring receipt now
    resp = client.get(f"/receipts/?targetMonth={MONTH_FEB_2026}")
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
            "startDate": MONTH_JAN_2026,
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
