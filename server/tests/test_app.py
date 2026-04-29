"""Tests for the Flask Budget application.

Verifies:
1. The app can start.
2. One can POST to the app and populate each database table.
3. One can GET data from the app and receive the correct values.
"""


# ---------------------------------------------------------------------------
# 1. App starts
# ---------------------------------------------------------------------------


def test_app_starts(client):
    """The app responds to a GET request without errors."""
    response = client.get("/budget/api/categories")
    assert response.status_code == 200
    assert isinstance(response.get_json(), list)


# ---------------------------------------------------------------------------
# 2. POST to populate each table  /  3. GET returns correct values
# ---------------------------------------------------------------------------


def test_post_category(client):
    """POST /budget/api/categories inserts a row into the categories table."""
    payload = {"name": "test-category", "is_positive": False}
    response = client.post("/budget/api/categories", json=payload)
    assert response.status_code == 201
    data = response.get_json()
    assert "uid" in data
    assert data["name"] == "test-category"
    assert data["is_positive"] == 0


def test_get_categories(client):
    """GET /budget/api/categories returns the previously posted category."""
    response = client.get("/budget/api/categories")
    assert response.status_code == 200
    categories = response.get_json()
    assert any(c["name"] == "test-category" for c in categories)


def test_post_transaction(client):
    """POST /budget/api/transactions inserts a row into the receipts table."""
    categories = client.get("/budget/api/categories").get_json()
    category_uid = next(c["uid"] for c in categories if c["name"] == "test-category")

    payload = {
        "epoch_day": 19000,
        "amount": -45.50,
        "description": "Whole Foods",
        "category_uid": category_uid,
    }
    response = client.post("/budget/api/transactions", json=payload)
    assert response.status_code == 201
    data = response.get_json()
    assert "uid" in data


def test_get_transactions(client):
    """GET /budget/api/transactions returns the previously posted transaction."""
    response = client.get("/budget/api/transactions")
    assert response.status_code == 200
    transactions = response.get_json()
    assert any(t["description"] == "Whole Foods" for t in transactions)


def test_post_transaction_import_payload_converts_date_and_creates_category(client):
    """POST /budget/api/transactions supports import payload keys from CSV/JSON."""
    payload = {
        "date": "2026-03-01",
        "category": "import-expense",
        "isPositive": False,
        "amount": 45.50,
        "description": "Imported expense",
    }
    response = client.post("/budget/api/transactions", json=payload)
    assert response.status_code == 201

    categories = client.get("/budget/api/categories").get_json()
    imported_category = next(c for c in categories if c["name"] == "import-expense")
    assert imported_category["is_positive"] == 0

    transactions = client.get("/budget/api/transactions").get_json()
    imported_txn = next(t for t in transactions if t["description"] == "Imported expense")
    assert imported_txn["epoch_day"] == 20513
    assert imported_txn["category_uid"] == imported_category["uid"]
    assert imported_txn["amount"] == -45.50


def test_post_transaction_import_payload_reuses_existing_category(client):
    """Import payload reuses existing categories and keeps their polarity."""
    create_cat = client.post(
        "/budget/api/categories",
        json={"name": "import-income", "is_positive": True},
    )
    assert create_cat.status_code == 201
    existing_uid = create_cat.get_json()["uid"]

    payload = {
        "date": "2026-03-02",
        "category": "import-income",
        "isPositive": False,
        "amount": 100.0,
        "description": "Imported income",
    }
    response = client.post("/budget/api/transactions", json=payload)
    assert response.status_code == 201

    categories = client.get("/budget/api/categories").get_json()
    import_income_categories = [c for c in categories if c["name"] == "import-income"]
    assert len(import_income_categories) == 1
    assert import_income_categories[0]["uid"] == existing_uid

    transactions = client.get("/budget/api/transactions").get_json()
    imported_txn = next(t for t in transactions if t["description"] == "Imported income")
    assert imported_txn["category_uid"] == existing_uid
    assert imported_txn["amount"] == 100.0


def test_post_transaction_with_date_and_category_uid(client):
    """Date-based payloads work even when category UID is provided directly."""
    categories = client.get("/budget/api/categories").get_json()
    category_uid = next(c["uid"] for c in categories if c["name"] == "test-category")

    payload = {
        "date": "2026-03-03",
        "amount": 20.0,
        "description": "Imported with uid",
        "category_uid": category_uid,
    }
    response = client.post("/budget/api/transactions", json=payload)
    assert response.status_code == 201

    transactions = client.get("/budget/api/transactions").get_json()
    imported_txn = next(t for t in transactions if t["description"] == "Imported with uid")
    assert imported_txn["epoch_day"] == 20515
    assert imported_txn["category_uid"] == category_uid
    assert imported_txn["amount"] == -20.0


def test_put_budget_item(client):
    """PUT /budget/api/budget-items inserts a row into the budget_items table."""
    categories = client.get("/budget/api/categories").get_json()
    category_uid = next(c["uid"] for c in categories if c["name"] == "test-category")

    payload = {
        "category_uid": category_uid,
        "month_key": 202506,
        "value": 500.00,
    }
    response = client.put("/budget/api/budget-items", json=payload)
    assert response.status_code == 200
    data = response.get_json()
    assert data["category_uid"] == category_uid
    assert data["month_key"] == 202506
    assert data["value"] == 500.00


def test_get_budget_items(client):
    """GET /budget/api/budget-items returns the previously posted budget item."""
    response = client.get("/budget/api/budget-items")
    assert response.status_code == 200
    budget_items = response.get_json()
    assert any(item["month_key"] == 202506 for item in budget_items)
