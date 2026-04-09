from typing import List

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

import models
import schemas
from database import get_db
from recurrence_utils import populate_validity_lookup_for_recurrence

router = APIRouter(prefix="/recurrences", tags=["recurrences"])


@router.get("/", response_model=List[schemas.RecurrenceResponse])
def list_recurrences(db: Session = Depends(get_db)):
    return db.query(models.Recurrence).all()


@router.get("/{recurrence_id}", response_model=schemas.RecurrenceResponse)
def get_recurrence(recurrence_id: str, db: Session = Depends(get_db)):
    rec = db.query(models.Recurrence).filter(models.Recurrence.id == recurrence_id).first()
    if rec is None:
        raise HTTPException(status_code=404, detail="Recurrence not found")
    return rec


@router.post("/", response_model=schemas.RecurrenceResponse)
def upsert_recurrence(recurrence: schemas.RecurrenceCreate, db: Session = Depends(get_db)):
    existing = db.query(models.Recurrence).filter(models.Recurrence.id == recurrence.id).first()
    if existing is None:
        db_rec = models.Recurrence(**recurrence.model_dump())
        db.add(db_rec)
        db.commit()
        db.refresh(db_rec)
        # Also update the receipt to reference this recurrence
        receipt = db.query(models.Receipt).filter(models.Receipt.uid == recurrence.receiptId).first()
        if receipt is not None:
            receipt.recurrenceId = recurrence.id
            db.commit()
        # Pre-populate validity_lookup for 12 months
        populate_validity_lookup_for_recurrence(db, db_rec)
    else:
        for key, value in recurrence.model_dump().items():
            setattr(existing, key, value)
        db.commit()
        db.refresh(existing)
        db_rec = existing
        # Re-populate validity_lookup after update
        populate_validity_lookup_for_recurrence(db, db_rec)
    return db_rec


@router.delete("/{recurrence_id}")
def delete_recurrence(recurrence_id: str, db: Session = Depends(get_db)):
    rec = db.query(models.Recurrence).filter(models.Recurrence.id == recurrence_id).first()
    if rec is None:
        raise HTTPException(status_code=404, detail="Recurrence not found")
    # Clear recurrenceId on the associated receipt
    receipt = db.query(models.Receipt).filter(models.Receipt.uid == rec.receiptId).first()
    if receipt is not None:
        receipt.recurrenceId = None
        db.commit()
    # Remove validity_lookup entries
    db.query(models.ValidityLookup).filter(
        models.ValidityLookup.recurrenceId == recurrence_id
    ).delete()
    db.delete(rec)
    db.commit()
    return {"detail": "Recurrence deleted"}
