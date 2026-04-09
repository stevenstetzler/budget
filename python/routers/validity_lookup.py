from typing import List

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

import models
import schemas
from database import get_db

router = APIRouter(prefix="/validity-lookup", tags=["validity_lookup"])


@router.get("/", response_model=List[schemas.ValidityLookupResponse])
def list_validity_lookup(db: Session = Depends(get_db)):
    return db.query(models.ValidityLookup).all()


@router.get("/recurrence/{recurrence_id}", response_model=List[schemas.ValidityLookupResponse])
def list_validity_lookup_for_recurrence(recurrence_id: str, db: Session = Depends(get_db)):
    return (
        db.query(models.ValidityLookup)
        .filter(models.ValidityLookup.recurrenceId == recurrence_id)
        .all()
    )


@router.patch("/{lookup_id}", response_model=schemas.ValidityLookupResponse)
def toggle_validity_lookup(
    lookup_id: str,
    toggle: schemas.ValidityLookupToggle,
    db: Session = Depends(get_db),
):
    entry = db.query(models.ValidityLookup).filter(models.ValidityLookup.id == lookup_id).first()
    if entry is None:
        raise HTTPException(status_code=404, detail="ValidityLookup entry not found")
    entry.isActive = toggle.isActive
    db.commit()
    db.refresh(entry)
    return entry


@router.get("/month/{target_month}", response_model=List[schemas.ValidityLookupResponse])
def list_validity_lookup_for_month(target_month: int, db: Session = Depends(get_db)):
    """List all active validity_lookup entries for the given targetMonth (epochDay)."""
    return (
        db.query(models.ValidityLookup)
        .filter(
            models.ValidityLookup.targetMonth == target_month,
            models.ValidityLookup.isActive == True,  # noqa: E712
        )
        .all()
    )
