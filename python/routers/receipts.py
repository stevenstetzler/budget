from typing import List, Optional

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session
from sqlalchemy import or_

import models
import schemas
from database import get_db

router = APIRouter(prefix="/receipts", tags=["receipts"])


@router.get("/", response_model=List[schemas.ReceiptResponse])
def list_receipts(
    start_epoch_day: Optional[int] = Query(None, alias="startEpochDay"),
    end_epoch_day: Optional[int] = Query(None, alias="endEpochDay"),
    target_month: Optional[int] = Query(None, alias="targetMonth"),
    db: Session = Depends(get_db),
):
    """
    List receipts.

    - Without parameters: returns all non-deleted receipts.
    - With startEpochDay + endEpochDay: returns receipts in that date range.
    - With targetMonth (epochDay of first day of month): returns receipts for that
      month, including recurring receipts whose validity_lookup entry is active.
      Combining targetMonth with startEpochDay/endEpochDay restricts further.
    """
    if target_month is not None:
        # Determine range from targetMonth if not explicitly provided
        if start_epoch_day is None:
            start_epoch_day = target_month
        if end_epoch_day is None:
            # Compute first day of next month
            from datetime import date, timedelta
            base = date(1970, 1, 1) + timedelta(days=target_month)
            if base.month == 12:
                next_month_first = date(base.year + 1, 1, 1)
            else:
                next_month_first = date(base.year, base.month + 1, 1)
            end_epoch_day = (next_month_first - date(1970, 1, 1)).days

        # Receipts that fall in the date range (regular)
        regular_uids = {
            r.uid
            for r in db.query(models.Receipt.uid)
            .filter(
                models.Receipt.deleted == False,  # noqa: E712
                models.Receipt.epochDay >= start_epoch_day,
                models.Receipt.epochDay < end_epoch_day,
            )
            .all()
        }

        # Recurring receipt UIDs active in the target month
        recurring_receipt_ids = (
            db.query(models.Recurrence.receiptId)
            .join(
                models.ValidityLookup,
                models.ValidityLookup.recurrenceId == models.Recurrence.id,
            )
            .filter(
                models.ValidityLookup.targetMonth == target_month,
                models.ValidityLookup.isActive == True,  # noqa: E712
            )
            .all()
        )
        recurring_uids = {r.receiptId for r in recurring_receipt_ids}

        all_uids = regular_uids | recurring_uids
        if not all_uids:
            return []

        return (
            db.query(models.Receipt)
            .filter(
                models.Receipt.uid.in_(all_uids),
                models.Receipt.deleted == False,  # noqa: E712
            )
            .all()
        )

    # Plain date-range filter (no recurring lookup)
    query = db.query(models.Receipt).filter(models.Receipt.deleted == False)  # noqa: E712
    if start_epoch_day is not None:
        query = query.filter(models.Receipt.epochDay >= start_epoch_day)
    if end_epoch_day is not None:
        query = query.filter(models.Receipt.epochDay < end_epoch_day)
    return query.all()


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
