from typing import List

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

import models
import schemas
from database import get_db

router = APIRouter(prefix="/receipts", tags=["receipts"])


@router.get("/", response_model=List[schemas.ReceiptResponse])
def list_receipts(db: Session = Depends(get_db)):
    return db.query(models.Receipt).all()


@router.post("/", response_model=schemas.ReceiptResponse)
def upsert_receipt(receipt: schemas.ReceiptCreate, db: Session = Depends(get_db)):
    existing = db.query(models.Receipt).filter(models.Receipt.uid == receipt.uid).first()
    if existing is None:
        db_receipt = models.Receipt(**receipt.model_dump())
        db.add(db_receipt)
    else:
        if receipt.updatedAt >= existing.updatedAt:
            for key, value in receipt.model_dump().items():
                setattr(existing, key, value)
        db_receipt = existing
    db.commit()
    db.refresh(db_receipt)
    return db_receipt
