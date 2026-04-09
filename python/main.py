from contextlib import asynccontextmanager

from fastapi import FastAPI

import models
from database import engine, SessionLocal
from routers import budget_items, categories, receipts
from routers import recurrences, validity_lookup
from recurrence_utils import populate_all_validity_lookups


@asynccontextmanager
async def lifespan(app: FastAPI):
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
