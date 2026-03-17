"""Tests for the FastAPI Budget application.

Verifies:
1. The app can start.
2. One can POST to the app and populate each database table.
3. One can GET data from the app and receive the correct values.
"""

CATEGORY_UID = "test-category-001"
RECEIPT_UID = "test-receipt-001"


# ---------------------------------------------------------------------------
# 1. App starts
# ---------------------------------------------------------------------------


def test_app_starts(client):
    """The app responds to a GET request without errors."""
    response = client.get("/categories/")
    assert response.status_code == 200
    assert isinstance(response.json(), list)


# ---------------------------------------------------------------------------
# 2. POST to populate each table  /  3. GET returns correct values
# ---------------------------------------------------------------------------


def test_post_category(client):
    """POST /categories/ inserts a row into the categories table."""
    payload = {
        "uid": CATEGORY_UID,
        "name": "grocery",
        "isPositive": False,
        "updatedAt": 1_000_000,
        "deleted": False,
    }
    response = client.post("/categories/", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["uid"] == CATEGORY_UID
    assert data["name"] == "grocery"
    assert data["isPositive"] is False


def test_get_categories(client):
    """GET /categories/ returns the previously posted category."""
    response = client.get("/categories/")
    assert response.status_code == 200
    categories = response.json()
    assert any(c["uid"] == CATEGORY_UID for c in categories)


def test_post_receipt(client):
    """POST /receipts/ inserts a row into the receipts table."""
    payload = {
        "uid": RECEIPT_UID,
        "epochDay": 19000,
        "amount": -45.50,
        "description": "Whole Foods",
        "categoryUid": CATEGORY_UID,
        "updatedAt": 1_000_001,
        "deleted": False,
    }
    response = client.post("/receipts/", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["uid"] == RECEIPT_UID
    assert data["amount"] == -45.50
    assert data["description"] == "Whole Foods"
    assert data["categoryUid"] == CATEGORY_UID


def test_get_receipts(client):
    """GET /receipts/ returns the previously posted receipt."""
    response = client.get("/receipts/")
    assert response.status_code == 200
    receipts = response.json()
    assert any(r["uid"] == RECEIPT_UID for r in receipts)


def test_post_budget_item(client):
    """POST /budget-items/ inserts a row into the budgetitems table."""
    payload = {
        "categoryUid": CATEGORY_UID,
        "monthKey": 202506,
        "value": 500.00,
        "updatedAt": 1_000_002,
        "deleted": False,
    }
    response = client.post("/budget-items/", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["categoryUid"] == CATEGORY_UID
    assert data["monthKey"] == 202506
    assert data["value"] == 500.00


def test_get_budget_items(client):
    """GET /budget-items/ returns the previously posted budget item."""
    response = client.get("/budget-items/")
    assert response.status_code == 200
    budget_items = response.json()
    assert any(
        item["categoryUid"] == CATEGORY_UID and item["monthKey"] == 202506
        for item in budget_items
    )
