# Budget Server

Lightweight Python/Flask backend that powers the Budget web app (`web/index.html`).

## Requirements

- Python 3.10+

## Setup

```bash
cd server
pip install -r requirements.txt
```

## Running

```bash
python app.py
```

The server starts on **http://localhost:5000** by default.
All API routes are prefixed with `/budget/api/`.
The web frontend is served at `http://localhost:5000/budget/`.

To enable Flask debug/reload mode (development only):

```bash
FLASK_DEBUG=1 python app.py
```

## API Reference

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/budget/api/categories` | List all categories |
| `POST` | `/budget/api/categories` | Create a category |
| `GET`  | `/budget/api/transactions?month=YYYYMM` | List transactions (optionally filtered by month) |
| `POST` | `/budget/api/transactions` | Add a transaction |
| `PUT`  | `/budget/api/transactions/<uid>` | Update a transaction |
| `DELETE` | `/budget/api/transactions/<uid>` | Delete a transaction |
| `GET`  | `/budget/api/budget-items?month=YYYYMM` | List budget allocations |
| `PUT`  | `/budget/api/budget-items` | Upsert a budget allocation |

### POST `/budget/api/categories`

```json
{ "name": "coffee", "is_positive": false }
```

### POST `/budget/api/transactions`

```json
{
  "epoch_day": 19827,
  "amount": -45.50,
  "category_uid": "<uid>",
  "description": "Whole Foods"
}
```

`epoch_day` is the number of days since 1970-01-01 (Unix epoch in days).
`amount` is signed: positive for income, negative for expense.

### PUT `/budget/api/budget-items`

```json
{
  "category_uid": "<uid>",
  "month_key": 202506,
  "value": 500.00
}
```

`month_key` is an integer in `YYYYMM` format.

## Data Storage

Data is persisted in `budget.db` (SQLite) in the directory where `app.py` is run.
The database is created automatically on first start and seeded with default categories.

## Using with the Web App

Open `web/index.html` in a browser, then set the **Endpoint URL** in Preferences to
`http://localhost:5000` (or wherever the server is running).

> **CORS** is enabled for all origins, making it safe to open `index.html` directly
> from the filesystem or any web server.
