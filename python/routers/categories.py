from typing import List

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

import models
import schemas
from database import get_db

router = APIRouter(prefix="/categories", tags=["categories"])


@router.get("/", response_model=List[schemas.CategoryResponse])
def list_categories(db: Session = Depends(get_db)):
    return db.query(models.Category).all()


@router.post("/", response_model=schemas.CategoryResponse)
def upsert_category(category: schemas.CategoryCreate, db: Session = Depends(get_db)):
    existing = db.query(models.Category).filter(models.Category.uid == category.uid).first()
    if existing is None:
        db_category = models.Category(**category.model_dump())
        db.add(db_category)
    else:
        if category.updatedAt >= existing.updatedAt:
            for key, value in category.model_dump().items():
                setattr(existing, key, value)
        db_category = existing
    db.commit()
    db.refresh(db_category)
    return db_category
