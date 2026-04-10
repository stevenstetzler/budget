from typing import List, Optional
import calendar

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

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
      Regular (non-recurring) receipts are only included when their epochDay falls
      within the date range for that month.  Recurring receipts are included solely
      via the validity_lookup join and have their occurrenceEpochDay set to
      targetMonth (the first day of the target month).
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

        # Regular (non-recurring) receipts that fall in the date range
        regular_uids = {
            r.uid
            for r in db.query(models.Receipt.uid)
            .filter(
                models.Receipt.deleted == False,  # noqa: E712
                models.Receipt.recurrenceId == None,  # noqa: E711
                models.Receipt.epochDay >= start_epoch_day,
                models.Receipt.epochDay < end_epoch_day,
            )
            .all()
        }

        # Recurring receipt UIDs active in the target month, along with their
        # recurrence metadata for computing the occurrence date.
        recurring_rows = (
            db.query(
                models.Recurrence.receiptId,
                models.Recurrence.frequency,
                models.Recurrence.dayOfPeriod,
            )
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
        # Map receiptId → (frequency, dayOfPeriod) for occurrence-date computation
        recurring_uid_to_rec = {r.receiptId: (r.frequency, r.dayOfPeriod) for r in recurring_rows}
        recurring_uids = set(recurring_uid_to_rec.keys())

        all_uids = regular_uids | recurring_uids
        if not all_uids:
            return []

        receipts = (
            db.query(models.Receipt)
            .filter(
                models.Receipt.uid.in_(all_uids),
                models.Receipt.deleted == False,  # noqa: E712
            )
            .all()
        )

        # Build response: recurring receipts get a computed occurrenceEpochDay so
        # clients always receive an in-range date for display/ordering.
        from datetime import date as _date, timedelta as _td
        _epoch_origin = _date(1970, 1, 1)
        _base = _epoch_origin + _td(days=target_month)  # first day of target month

        responses = []
        for r in receipts:
            resp = schemas.ReceiptResponse.model_validate(r)
            if r.uid in recurring_uid_to_rec:
                freq, day_of_period = recurring_uid_to_rec[r.uid]
                if freq == "MONTHLY":
                    # Clamp day to the actual length of the target month
                    max_day = calendar.monthrange(_base.year, _base.month)[1]
                    occ_day = min(day_of_period, max_day)
                    occ_date = _date(_base.year, _base.month, occ_day)
                    resp.occurrenceEpochDay = (occ_date - _epoch_origin).days
                else:
                    # For DAILY / WEEKLY / BI_WEEKLY use the start of the target month
                    resp.occurrenceEpochDay = target_month
            responses.append(resp)
        return responses

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
