"""Utility functions for managing recurrence and validity_lookup tables."""

import uuid
from datetime import date, timedelta
from typing import Optional

from sqlalchemy.orm import Session

import models


# Number of months ahead to pre-populate validity_lookup
VALIDITY_LOOKAHEAD_MONTHS = 12


def _first_day_of_month(year: int, month: int) -> date:
    return date(year, month, 1)


def _epoch_day(d: date) -> int:
    """Convert a date to epoch day (days since 1970-01-01)."""
    return (d - date(1970, 1, 1)).days


def _from_epoch_day(epoch_day: int) -> date:
    return date(1970, 1, 1) + timedelta(days=epoch_day)


def _advance_month(year: int, month: int, n: int = 1):
    """Advance (year, month) by n months."""
    month += n
    while month > 12:
        month -= 12
        year += 1
    return year, month


def _recurrence_active_in_month(
    recurrence: models.Recurrence,
    year: int,
    month: int,
) -> bool:
    """Return True if the recurrence produces at least one occurrence in the given month."""
    first_day = _first_day_of_month(year, month)
    # Calculate first day of next month
    next_year, next_month = _advance_month(year, month)
    last_day_exclusive = _first_day_of_month(next_year, next_month)

    start = _from_epoch_day(recurrence.startDate)
    end = _from_epoch_day(recurrence.endDate) if recurrence.endDate is not None else None

    # The recurrence must have started before the end of the month
    if start >= last_day_exclusive:
        return False

    # The recurrence must not have ended before the start of the month
    if end is not None and end < first_day:
        return False

    freq = recurrence.frequency
    if freq == "MONTHLY":
        return True
    elif freq == "WEEKLY":
        # Occurs every 7 days from startDate; check if any occurrence falls in month
        return _has_occurrence_in_month(start, 7, first_day, last_day_exclusive)
    elif freq == "BI_WEEKLY":
        return _has_occurrence_in_month(start, 14, first_day, last_day_exclusive)
    elif freq == "DAILY":
        return True
    return False


def _has_occurrence_in_month(
    start: date,
    interval_days: int,
    month_start: date,
    month_end_exclusive: date,
) -> bool:
    """Check if a repeating event (starting at start, every interval_days) hits [month_start, month_end_exclusive)."""
    if start >= month_end_exclusive:
        return False
    if start >= month_start:
        return True
    # Days since start to month_start
    delta = (month_start - start).days
    remainder = delta % interval_days
    # The next occurrence on or after month_start
    if remainder == 0:
        next_occ = month_start
    else:
        next_occ = month_start + timedelta(days=(interval_days - remainder))
    return next_occ < month_end_exclusive


def prune_validity_lookup_for_recurrence(
    db: Session,
    recurrence: models.Recurrence,
) -> None:
    """
    Remove validity_lookup rows that are no longer valid for the given recurrence.

    This covers:
    - Months before the recurrence's startDate.
    - Months after the recurrence's endDate (if set).
    - Months where the recurrence no longer produces an occurrence (e.g., after a
      frequency change).

    Rows whose month is still active are left untouched so that any user-set
    ``isActive=False`` overrides are preserved.
    """
    entries = (
        db.query(models.ValidityLookup)
        .filter(models.ValidityLookup.recurrenceId == recurrence.id)
        .all()
    )
    for entry in entries:
        target = _from_epoch_day(entry.targetMonth)
        if not _recurrence_active_in_month(recurrence, target.year, target.month):
            db.delete(entry)
    db.commit()


def populate_validity_lookup_for_recurrence(
    db: Session,
    recurrence: models.Recurrence,
    lookahead_months: int = VALIDITY_LOOKAHEAD_MONTHS,
    prune_stale: bool = False,
) -> None:
    """
    Pre-populate validity_lookup for the given recurrence covering the period from
    the recurrence's startDate month through lookahead_months ahead of today.

    Existing rows are preserved (their isActive values are not reset); only missing
    rows are inserted.

    When *prune_stale* is True (set on recurrence updates), stale rows that no
    longer correspond to an active month are removed first, so that edited
    recurrences don't keep appearing in months they should have left.
    """
    if prune_stale:
        prune_validity_lookup_for_recurrence(db, recurrence)

    today = date.today()
    # Start from the month of the recurrence's start date
    start_date = _from_epoch_day(recurrence.startDate)

    year, month = start_date.year, start_date.month
    end_year, end_month = _advance_month(today.year, today.month, lookahead_months)

    while (year < end_year) or (year == end_year and month <= end_month):
        # Check if the recurrence is active in this month
        if _recurrence_active_in_month(recurrence, year, month):
            target_epoch = _epoch_day(_first_day_of_month(year, month))
            existing = (
                db.query(models.ValidityLookup)
                .filter(
                    models.ValidityLookup.recurrenceId == recurrence.id,
                    models.ValidityLookup.targetMonth == target_epoch,
                )
                .first()
            )
            if existing is None:
                entry = models.ValidityLookup(
                    id=str(uuid.uuid4()),
                    recurrenceId=recurrence.id,
                    targetMonth=target_epoch,
                    isActive=True,
                )
                db.add(entry)

        year, month = _advance_month(year, month)

    db.commit()


def populate_all_validity_lookups(db: Session) -> None:
    """
    On startup: ensure validity_lookup is populated up to 12 months ahead
    for every existing recurrence.
    """
    recurrences = db.query(models.Recurrence).all()
    for rec in recurrences:
        populate_validity_lookup_for_recurrence(db, rec)
