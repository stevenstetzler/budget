from contextlib import asynccontextmanager

from fastapi import FastAPI
from sqlalchemy import inspect as sa_inspect, text

import models
from database import engine, SessionLocal
from routers import budget_items, categories, receipts
from routers import recurrences, validity_lookup
from recurrence_utils import populate_all_validity_lookups


def _ensure_schema_current() -> None:
    """Apply any schema changes that ``create_all()`` cannot handle (ALTER TABLE).

    This is an in-process migration helper for existing SQLite databases that were
    created before the recurring-expenses schema was introduced.  For brand-new
    databases ``Base.metadata.create_all`` (below) creates all tables including
    the new columns, so this function becomes a no-op.
    """
    inspector = sa_inspect(engine)
    if 'receipts' in inspector.get_table_names():
        columns = [c['name'] for c in inspector.get_columns('receipts')]
        if 'recurrenceId' not in columns:
            with engine.connect() as conn:
                conn.execute(
                    text("ALTER TABLE receipts ADD COLUMN recurrenceId TEXT DEFAULT NULL")
                )
                conn.commit()


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Ensure the schema is up-to-date (handles existing DBs without recurrenceId)
    _ensure_schema_current()
    # On startup: ensure validity_lookup is populated up to 12 months ahead
    db = SessionLocal()
    try:
        populate_all_validity_lookups(db)
    finally:
        db.close()
    yield


models.Base.metadata.create_all(bind=engine)

app = FastAPI(title="Budget API", lifespan=lifespan)

app.include_router(categories.router)
app.include_router(receipts.router)
app.include_router(budget_items.router)
app.include_router(recurrences.router)
app.include_router(validity_lookup.router)
