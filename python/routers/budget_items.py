from typing import List

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

import models
import schemas
from database import get_db

router = APIRouter(prefix="/budget-items", tags=["budget_items"])


@router.get("/", response_model=List[schemas.BudgetItemResponse])
def list_budget_items(db: Session = Depends(get_db)):
    return db.query(models.BudgetItem).all()


@router.post("/", response_model=schemas.BudgetItemResponse)
def upsert_budget_item(budget_item: schemas.BudgetItemCreate, db: Session = Depends(get_db)):
    existing = (
        db.query(models.BudgetItem)
        .filter(
            models.BudgetItem.categoryUid == budget_item.categoryUid,
            models.BudgetItem.monthKey == budget_item.monthKey,
        )
        .first()
    )
    if existing is None:
        db_item = models.BudgetItem(**budget_item.model_dump())
        db.add(db_item)
    else:
        if budget_item.updatedAt >= existing.updatedAt:
            for key, value in budget_item.model_dump().items():
                setattr(existing, key, value)
        db_item = existing
    db.commit()
    db.refresh(db_item)
    return db_item
