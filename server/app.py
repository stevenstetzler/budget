"""
Budget API Server

A lightweight Flask server that exposes a REST API for the Budget web app
and optionally serves the frontend at /budget/.
Data is persisted in a local SQLite database (budget.db).
"""

import calendar
import os
import sqlite3
import uuid
import time
from datetime import date, timedelta
from flask import Flask, request, jsonify, g, send_from_directory
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

# Resolve the web directory relative to this file so the server can be run
# from any working directory.
_HERE = os.path.dirname(os.path.abspath(__file__))
WEB_DIR = os.path.join(_HERE, "..", "web")

DATABASE = "budget.db"

_EPOCH_ORIGIN = date(1970, 1, 1)


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
    """Create tables if this is a fresh database."""
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
                uid            TEXT PRIMARY KEY,
                epoch_day      INTEGER NOT NULL,
                amount         REAL NOT NULL,
                description    TEXT,
                category_uid   TEXT NOT NULL,
                updated_at     INTEGER NOT NULL,
                deleted        INTEGER NOT NULL DEFAULT 0,
                recurrence_id  TEXT DEFAULT NULL,
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

            CREATE TABLE IF NOT EXISTS recurrence (
                id           TEXT PRIMARY KEY,
                receipt_id   TEXT NOT NULL,
                frequency    TEXT NOT NULL,
                start_date   INTEGER NOT NULL,
                end_date     INTEGER,
                day_of_period INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS validity_lookup (
                id             TEXT PRIMARY KEY,
                recurrence_id  TEXT NOT NULL,
                target_month   INTEGER NOT NULL,
                is_active      INTEGER NOT NULL DEFAULT 1,
                UNIQUE (recurrence_id, target_month)
            );
            """
        )
        _ensure_schema_current(db)
        db.commit()


def _ensure_schema_current(db: sqlite3.Connection):
    """Add new columns / tables to existing databases that predate recurring support."""
    existing_cols = {
        row[1] for row in db.execute("PRAGMA table_info(receipts)").fetchall()
    }
    if "recurrence_id" not in existing_cols:
        db.execute("ALTER TABLE receipts ADD COLUMN recurrence_id TEXT DEFAULT NULL")

    db.executescript(
        """
        CREATE TABLE IF NOT EXISTS recurrence (
            id           TEXT PRIMARY KEY,
            receipt_id   TEXT NOT NULL,
            frequency    TEXT NOT NULL,
            start_date   INTEGER NOT NULL,
            end_date     INTEGER,
            day_of_period INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS validity_lookup (
            id             TEXT PRIMARY KEY,
            recurrence_id  TEXT NOT NULL,
            target_month   INTEGER NOT NULL,
            is_active      INTEGER NOT NULL DEFAULT 1,
            UNIQUE (recurrence_id, target_month)
        );
        """
    )


def _now_ms() -> int:
    return int(time.time() * 1000)


def _epoch_day_for_month_key(month_key: int) -> tuple[int, int]:
    """Return (first_epoch_day, next_month_first_epoch_day) for a YYYYMM key."""
    year = month_key // 100
    month = month_key % 100
    first = date(year, month, 1)
    if month == 12:
        next_first = date(year + 1, 1, 1)
    else:
        next_first = date(year, month + 1, 1)
    return (first - _EPOCH_ORIGIN).days, (next_first - _EPOCH_ORIGIN).days


def _populate_validity_lookup(db: sqlite3.Connection, rec: dict):
    """Pre-populate validity_lookup for the next 12 months for a recurrence."""
    today = date.today()
    # Compute end of lookahead: 12 months from today
    ey = today.year + (today.month + 12 - 1) // 12
    em = (today.month + 12 - 1) % 12 + 1

    start_date = _EPOCH_ORIGIN + timedelta(days=rec["start_date"])
    end_date_val = rec.get("end_date")
    end_date = (_EPOCH_ORIGIN + timedelta(days=end_date_val)) if end_date_val is not None else None

    cy, cm = start_date.year, start_date.month
    while (cy < ey) or (cy == ey and cm <= em):
        first_of_month = date(cy, cm, 1)
        if cm == 12:
            next_month_first = date(cy + 1, 1, 1)
        else:
            next_month_first = date(cy, cm + 1, 1)

        rec_start = _EPOCH_ORIGIN + timedelta(days=rec["start_date"])
        in_range = rec_start < next_month_first and (end_date is None or end_date >= first_of_month)
        if in_range and _is_recurrence_active_in_month(rec, cy, cm):
            target_epoch = (first_of_month - _EPOCH_ORIGIN).days
            db.execute(
                "INSERT OR IGNORE INTO validity_lookup (id, recurrence_id, target_month, is_active) VALUES (?,?,?,1)",
                (str(uuid.uuid4()), rec["id"], target_epoch),
            )

        cm += 1
        if cm > 12:
            cm = 1
            cy += 1


def _is_recurrence_active_in_month(rec: dict, year: int, month: int) -> bool:
    freq = rec["frequency"]
    if freq in ("MONTHLY", "DAILY"):
        return True
    if freq in ("WEEKLY", "BI_WEEKLY"):
        interval = 7 if freq == "WEEKLY" else 14
        start = _EPOCH_ORIGIN + timedelta(days=rec["start_date"])
        first_of_month = date(year, month, 1)
        next_month_first = date(year + 1, 1, 1) if month == 12 else date(year, month + 1, 1)
        return _has_occurrence_in_month(start, interval, first_of_month, next_month_first)
    return False


def _has_occurrence_in_month(start: date, interval_days: int, month_start: date, month_end_exclusive: date) -> bool:
    if start >= month_end_exclusive:
        return False
    if start >= month_start:
        return True
    delta = (month_start - start).days
    remainder = delta % interval_days
    next_occ = month_start if remainder == 0 else month_start + timedelta(days=interval_days - remainder)
    return next_occ < month_end_exclusive


def _compute_occurrence_epoch_day(rec: dict, target_month_epoch_day: int) -> int:
    """Return the occurrence epoch day within the target month for a recurrence."""
    base = _EPOCH_ORIGIN + timedelta(days=target_month_epoch_day)
    if rec["frequency"] == "MONTHLY":
        max_day = calendar.monthrange(base.year, base.month)[1]
        day = min(rec["day_of_period"], max_day)
        occ = date(base.year, base.month, day)
        return (occ - _EPOCH_ORIGIN).days
    return target_month_epoch_day


def _prune_validity_lookup(db: sqlite3.Connection, rec: dict):
    """Delete validity_lookup rows that are no longer valid for this recurrence."""
    rows = db.execute(
        "SELECT id, target_month FROM validity_lookup WHERE recurrence_id = ?",
        (rec["id"],),
    ).fetchall()
    rec_start = _EPOCH_ORIGIN + timedelta(days=rec["start_date"])
    end_date_val = rec.get("end_date")
    rec_end = (_EPOCH_ORIGIN + timedelta(days=end_date_val)) if end_date_val is not None else None
    for row in rows:
        target = _EPOCH_ORIGIN + timedelta(days=row["target_month"])
        target_y, target_m = target.year, target.month
        next_month_first = date(target_y + 1, 1, 1) if target_m == 12 else date(target_y, target_m + 1, 1)
        in_range = rec_start < next_month_first and (rec_end is None or rec_end >= target)
        if not in_range or not _is_recurrence_active_in_month(rec, target_y, target_m):
            db.execute("DELETE FROM validity_lookup WHERE id = ?", (row["id"],))


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
        first, next_first = _epoch_day_for_month_key(month_key)
        target_month_epoch = first  # epochDay of first day of month

        # Regular (non-recurring) receipts in the date range
        regular_rows = db.execute(
            """
            SELECT r.uid, r.epoch_day, r.amount, r.description,
                   c.uid AS category_uid, c.name AS category_name, c.is_positive,
                   r.recurrence_id
            FROM receipts r
            JOIN categories c ON r.category_uid = c.uid
            WHERE r.deleted = 0
              AND r.recurrence_id IS NULL
              AND r.epoch_day >= ? AND r.epoch_day < ?
            ORDER BY r.epoch_day DESC
            """,
            (first, next_first),
        ).fetchall()

        # Recurring receipts active in this month
        recurring_rows = db.execute(
            """
            SELECT r.uid, r.epoch_day, r.amount, r.description,
                   c.uid AS category_uid, c.name AS category_name, c.is_positive,
                   r.recurrence_id,
                   rec.frequency, rec.day_of_period
            FROM receipts r
            JOIN categories c ON r.category_uid = c.uid
            JOIN recurrence rec ON rec.receipt_id = r.uid
            JOIN validity_lookup vl ON vl.recurrence_id = rec.id
            WHERE r.deleted = 0
              AND vl.target_month = ?
              AND vl.is_active = 1
            """,
            (target_month_epoch,),
        ).fetchall()

        results = [dict(r) for r in regular_rows]
        for r in recurring_rows:
            row = dict(r)
            row["epoch_day"] = _compute_occurrence_epoch_day(
                {"frequency": row.pop("frequency"), "day_of_period": row.pop("day_of_period")},
                target_month_epoch,
            )
            results.append(row)

        results.sort(key=lambda x: x["epoch_day"], reverse=True)
        return jsonify(results)

    rows = db.execute(
        """
        SELECT r.uid, r.epoch_day, r.amount, r.description,
               c.uid AS category_uid, c.name AS category_name, c.is_positive,
               r.recurrence_id
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
        "INSERT INTO receipts (uid, epoch_day, amount, description, category_uid, updated_at, deleted) VALUES (?,?,?,?,?,?,?)",
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
# Recurrence endpoints
# ---------------------------------------------------------------------------

@app.route("/budget/api/recurrences/<receipt_id>", methods=["GET"])
def get_recurrence_for_receipt(receipt_id):
    db = get_db()
    row = db.execute(
        "SELECT * FROM recurrence WHERE receipt_id = ?", (receipt_id,)
    ).fetchone()
    if row is None:
        return jsonify(None)
    return jsonify(dict(row))


@app.route("/budget/api/recurrences", methods=["POST"])
def upsert_recurrence():
    data = request.get_json(force=True)
    rec_id = data.get("id") or str(uuid.uuid4())
    receipt_id = data.get("receipt_id")
    frequency = data.get("frequency", "MONTHLY")
    start_date = data.get("start_date")
    end_date = data.get("end_date")
    day_of_period = data.get("day_of_period", 1)

    if not receipt_id or start_date is None:
        return jsonify({"error": "receipt_id and start_date are required"}), 400

    db = get_db()
    existing = db.execute("SELECT * FROM recurrence WHERE id = ?", (rec_id,)).fetchone()

    rec = {
        "id": rec_id,
        "receipt_id": receipt_id,
        "frequency": frequency,
        "start_date": int(start_date),
        "end_date": int(end_date) if end_date is not None else None,
        "day_of_period": int(day_of_period),
    }

    if existing is None:
        db.execute(
            "INSERT INTO recurrence (id, receipt_id, frequency, start_date, end_date, day_of_period) VALUES (?,?,?,?,?,?)",
            (rec_id, receipt_id, frequency, int(start_date), rec["end_date"], int(day_of_period)),
        )
        db.execute("UPDATE receipts SET recurrence_id = ? WHERE uid = ?", (rec_id, receipt_id))
        db.commit()
        _populate_validity_lookup(db, rec)
    else:
        old_receipt_id = existing["receipt_id"]
        db.execute(
            "UPDATE recurrence SET receipt_id=?, frequency=?, start_date=?, end_date=?, day_of_period=? WHERE id=?",
            (receipt_id, frequency, int(start_date), rec["end_date"], int(day_of_period), rec_id),
        )
        if old_receipt_id != receipt_id:
            db.execute("UPDATE receipts SET recurrence_id = NULL WHERE uid = ?", (old_receipt_id,))
            db.execute("UPDATE receipts SET recurrence_id = ? WHERE uid = ?", (rec_id, receipt_id))
        else:
            db.execute(
                "UPDATE receipts SET recurrence_id = ? WHERE uid = ? AND (recurrence_id IS NULL OR recurrence_id != ?)",
                (rec_id, receipt_id, rec_id),
            )
        db.commit()
        _prune_validity_lookup(db, rec)
        _populate_validity_lookup(db, rec)

    db.commit()
    row = db.execute("SELECT * FROM recurrence WHERE id = ?", (rec_id,)).fetchone()
    return jsonify(dict(row)), 201


@app.route("/budget/api/recurrences/<rec_id>", methods=["DELETE"])
def delete_recurrence(rec_id):
    db = get_db()
    row = db.execute("SELECT * FROM recurrence WHERE id = ?", (rec_id,)).fetchone()
    if row is None:
        return jsonify({"error": "not found"}), 404
    db.execute("UPDATE receipts SET recurrence_id = NULL WHERE uid = ?", (row["receipt_id"],))
    db.execute("DELETE FROM validity_lookup WHERE recurrence_id = ?", (rec_id,))
    db.execute("DELETE FROM recurrence WHERE id = ?", (rec_id,))
    db.commit()
    return "", 204


@app.route("/budget/api/recurrences/<rec_id>/months/<int:target_month_epoch>", methods=["GET"])
def get_recurrence_month_status(rec_id, target_month_epoch):
    db = get_db()
    row = db.execute(
        "SELECT is_active FROM validity_lookup WHERE recurrence_id = ? AND target_month = ?",
        (rec_id, target_month_epoch),
    ).fetchone()
    is_active = bool(row["is_active"]) if row else True
    return jsonify({"is_active": is_active})


@app.route("/budget/api/recurrences/<rec_id>/months/<int:target_month_epoch>", methods=["PATCH"])
def toggle_recurrence_month(rec_id, target_month_epoch):
    data = request.get_json(force=True)
    is_active = 1 if data.get("is_active", True) else 0
    db = get_db()
    db.execute(
        """
        INSERT INTO validity_lookup (id, recurrence_id, target_month, is_active)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(recurrence_id, target_month) DO UPDATE SET is_active = excluded.is_active
        """,
        (str(uuid.uuid4()), rec_id, target_month_epoch, is_active),
    )
    db.commit()
    return jsonify({"is_active": bool(is_active)})

if __name__ == "__main__":
    init_db()
    debug = os.environ.get("FLASK_DEBUG", "0") == "1"
    app.run(debug=debug, host="0.0.0.0", port=8888)
