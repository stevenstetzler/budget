from fastapi import FastAPI

import models
from database import engine
from routers import budget_items, categories, receipts

models.Base.metadata.create_all(bind=engine)

app = FastAPI(title="Budget API")

app.include_router(categories.router)
app.include_router(receipts.router)
app.include_router(budget_items.router)
