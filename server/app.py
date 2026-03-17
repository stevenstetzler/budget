"""
Budget API Server

A lightweight Flask server that exposes a REST API for the Budget web app
and optionally serves the frontend at /budget/.
Data is persisted in a local SQLite database (budget.db).
"""

import os
import sqlite3
import uuid
import time
from datetime import date
from flask import Flask, request, jsonify, g, send_from_directory
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

# Resolve the web directory relative to this file so the server can be run
# from any working directory.
_HERE = os.path.dirname(os.path.abspath(__file__))
WEB_DIR = os.path.join(_HERE, "..", "web")

DATABASE = "budget.db"


# ---------------------------------------------------------------------------
# Frontend route
# ---------------------------------------------------------------------------

@app.route("/budget/")
@app.route("/budget")
def serve_frontend():
    return send_from_directory(WEB_DIR, "index.html")


# ---------------------------------------------------------------------------
# Database helpers
# ---------------------------------------------------------------------------

def get_db() -> sqlite3.Connection:
    db = getattr(g, "_database", None)
    if db is None:
        db = g._database = sqlite3.connect(DATABASE)
        db.row_factory = sqlite3.Row
    return db


@app.teardown_appcontext
def close_connection(exception):
    db = getattr(g, "_database", None)
    if db is not None:
        db.close()


def init_db():
    """Create tables and seed default categories if this is a fresh database."""
    with app.app_context():
        db = get_db()
        db.executescript(
            """
            CREATE TABLE IF NOT EXISTS categories (
                uid       TEXT PRIMARY KEY,
                name      TEXT NOT NULL,
                is_positive INTEGER NOT NULL DEFAULT 0,
                updated_at  INTEGER NOT NULL,
                deleted     INTEGER NOT NULL DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS receipts (
                uid          TEXT PRIMARY KEY,
                epoch_day    INTEGER NOT NULL,
                amount       REAL NOT NULL,
                description  TEXT,
                category_uid TEXT NOT NULL,
                updated_at   INTEGER NOT NULL,
                deleted      INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (category_uid) REFERENCES categories(uid)
            );

            CREATE TABLE IF NOT EXISTS budget_items (
                category_uid TEXT NOT NULL,
                month_key    INTEGER NOT NULL,
                value        REAL NOT NULL DEFAULT 0,
                updated_at   INTEGER NOT NULL,
                deleted      INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (category_uid, month_key),
                FOREIGN KEY (category_uid) REFERENCES categories(uid)
            );
            """
        )

        # Seed default categories only when the table is empty
        count = db.execute("SELECT COUNT(*) FROM categories").fetchone()[0]
        if count == 0:
            now = _now_ms()
            income_cats = ["income", "paycheck"]
            expense_cats = [
                "taxes", "withholding", "grocery", "dining", "debt",
                "utilities", "car", "travel", "health", "subscriptions",
                "housing", "shopping", "charity", "misc", "entertainment",
            ]
            for name in income_cats:
                db.execute(
                    "INSERT INTO categories VALUES (?,?,?,?,?)",
                    (str(uuid.uuid4()), name, 1, now, 0),
                )
            for name in expense_cats:
                db.execute(
                    "INSERT INTO categories VALUES (?,?,?,?,?)",
                    (str(uuid.uuid4()), name, 0, now, 0),
                )
        db.commit()


def _now_ms() -> int:
    return int(time.time() * 1000)


def _epoch_day_for_month_key(month_key: int) -> tuple[int, int]:
    """Return (first_epoch_day, last_epoch_day) inclusive for a YYYYMM key."""
    year = month_key // 100
    month = month_key % 100
    from calendar import monthrange
    first = date(year, month, 1)
    last_day = monthrange(year, month)[1]
    last = date(year, month, last_day)
    epoch = date(1970, 1, 1)
    return (first - epoch).days, (last - epoch).days


# ---------------------------------------------------------------------------
# Category endpoints
# ---------------------------------------------------------------------------

@app.route("/budget/api/categories", methods=["GET"])
def get_categories():
    db = get_db()
    rows = db.execute(
        "SELECT uid, name, is_positive, updated_at FROM categories WHERE deleted = 0 ORDER BY is_positive DESC, name ASC"
    ).fetchall()
    return jsonify([dict(r) for r in rows])


@app.route("/budget/api/categories", methods=["POST"])
def create_category():
    data = request.get_json(force=True)
    name = (data.get("name") or "").strip()
    if not name:
        return jsonify({"error": "name is required"}), 400
    is_positive = 1 if data.get("is_positive") else 0
    uid = str(uuid.uuid4())
    now = _now_ms()
    db = get_db()
    db.execute(
        "INSERT INTO categories VALUES (?,?,?,?,?)",
        (uid, name, is_positive, now, 0),
    )
    db.commit()
    return jsonify({"uid": uid, "name": name, "is_positive": is_positive, "updated_at": now}), 201


# ---------------------------------------------------------------------------
# Transaction (receipt) endpoints
# ---------------------------------------------------------------------------

@app.route("/budget/api/transactions", methods=["GET"])
def get_transactions():
    month_key = request.args.get("month", type=int)
    db = get_db()
    if month_key:
        first, last = _epoch_day_for_month_key(month_key)
        rows = db.execute(
            """
            SELECT r.uid, r.epoch_day, r.amount, r.description,
                   c.uid AS category_uid, c.name AS category_name, c.is_positive
            FROM receipts r
            JOIN categories c ON r.category_uid = c.uid
            WHERE r.deleted = 0 AND r.epoch_day BETWEEN ? AND ?
            ORDER BY r.epoch_day DESC
            """,
            (first, last),
        ).fetchall()
    else:
        rows = db.execute(
            """
            SELECT r.uid, r.epoch_day, r.amount, r.description,
                   c.uid AS category_uid, c.name AS category_name, c.is_positive
            FROM receipts r
            JOIN categories c ON r.category_uid = c.uid
            WHERE r.deleted = 0
            ORDER BY r.epoch_day DESC
            """
        ).fetchall()
    return jsonify([dict(r) for r in rows])


@app.route("/budget/api/transactions", methods=["POST"])
def create_transaction():
    data = request.get_json(force=True)
    epoch_day = data.get("epoch_day")
    amount = data.get("amount")
    category_uid = data.get("category_uid")
    description = data.get("description") or None

    if epoch_day is None or amount is None or not category_uid:
        return jsonify({"error": "epoch_day, amount, and category_uid are required"}), 400

    db = get_db()
    cat = db.execute(
        "SELECT uid, is_positive FROM categories WHERE uid = ? AND deleted = 0",
        (category_uid,),
    ).fetchone()
    if not cat:
        return jsonify({"error": "category not found"}), 404

    uid = str(uuid.uuid4())
    now = _now_ms()
    db.execute(
        "INSERT INTO receipts VALUES (?,?,?,?,?,?,?)",
        (uid, int(epoch_day), float(amount), description, category_uid, now, 0),
    )
    db.commit()
    return jsonify({"uid": uid}), 201


@app.route("/budget/api/transactions/<txn_id>", methods=["PUT"])
def update_transaction(txn_id):
    data = request.get_json(force=True)
    db = get_db()
    row = db.execute(
        "SELECT uid FROM receipts WHERE uid = ? AND deleted = 0", (txn_id,)
    ).fetchone()
    if not row:
        return jsonify({"error": "not found"}), 404

    fields = {}
    if "epoch_day" in data:
        fields["epoch_day"] = int(data["epoch_day"])
    if "amount" in data:
        fields["amount"] = float(data["amount"])
    if "description" in data:
        fields["description"] = data["description"] or None
    if "category_uid" in data:
        fields["category_uid"] = data["category_uid"]

    if not fields:
        return jsonify({"error": "no fields to update"}), 400

    fields["updated_at"] = _now_ms()
    set_clause = ", ".join(f"{k} = ?" for k in fields)
    db.execute(
        f"UPDATE receipts SET {set_clause} WHERE uid = ?",
        list(fields.values()) + [txn_id],
    )
    db.commit()
    return jsonify({"uid": txn_id})


@app.route("/budget/api/transactions/<txn_id>", methods=["DELETE"])
def delete_transaction(txn_id):
    db = get_db()
    row = db.execute(
        "SELECT uid FROM receipts WHERE uid = ? AND deleted = 0", (txn_id,)
    ).fetchone()
    if not row:
        return jsonify({"error": "not found"}), 404
    db.execute(
        "UPDATE receipts SET deleted = 1, updated_at = ? WHERE uid = ?",
        (_now_ms(), txn_id),
    )
    db.commit()
    return "", 204


# ---------------------------------------------------------------------------
# Budget item endpoints
# ---------------------------------------------------------------------------

@app.route("/budget/api/budget-items", methods=["GET"])
def get_budget_items():
    month_key = request.args.get("month", type=int)
    db = get_db()
    if month_key:
        rows = db.execute(
            """
            SELECT b.category_uid, b.month_key, b.value,
                   c.name AS category_name, c.is_positive
            FROM budget_items b
            JOIN categories c ON b.category_uid = c.uid
            WHERE b.deleted = 0 AND b.month_key = ?
            ORDER BY c.is_positive DESC, c.name ASC
            """,
            (month_key,),
        ).fetchall()
    else:
        rows = db.execute(
            """
            SELECT b.category_uid, b.month_key, b.value,
                   c.name AS category_name, c.is_positive
            FROM budget_items b
            JOIN categories c ON b.category_uid = c.uid
            WHERE b.deleted = 0
            ORDER BY b.month_key DESC, c.is_positive DESC, c.name ASC
            """
        ).fetchall()
    return jsonify([dict(r) for r in rows])


@app.route("/budget/api/budget-items", methods=["PUT"])
def upsert_budget_item():
    data = request.get_json(force=True)
    category_uid = data.get("category_uid")
    month_key = data.get("month_key")
    value = data.get("value")

    if not category_uid or month_key is None or value is None:
        return jsonify({"error": "category_uid, month_key, and value are required"}), 400

    db = get_db()
    cat = db.execute(
        "SELECT uid FROM categories WHERE uid = ? AND deleted = 0", (category_uid,)
    ).fetchone()
    if not cat:
        return jsonify({"error": "category not found"}), 404

    now = _now_ms()
    db.execute(
        """
        INSERT INTO budget_items (category_uid, month_key, value, updated_at, deleted)
        VALUES (?, ?, ?, ?, 0)
        ON CONFLICT(category_uid, month_key) DO UPDATE SET
            value = excluded.value,
            updated_at = excluded.updated_at,
            deleted = 0
        """,
        (category_uid, int(month_key), float(value), now),
    )
    db.commit()
    return jsonify({"category_uid": category_uid, "month_key": month_key, "value": value})


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    init_db()
    app.run(debug=True, host="0.0.0.0", port=5000)
