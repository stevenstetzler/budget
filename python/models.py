from sqlalchemy import Boolean, Column, Float, Integer, String, UniqueConstraint
from database import Base


class Category(Base):
    __tablename__ = "categories"

    uid = Column(String, primary_key=True, index=True)
    name = Column(String, unique=True, nullable=False)
    isPositive = Column(Boolean, nullable=False)
    updatedAt = Column(Integer, nullable=False)
    deleted = Column(Boolean, nullable=False, default=False)


class Receipt(Base):
    __tablename__ = "receipts"

    uid = Column(String, primary_key=True, index=True)
    epochDay = Column(Integer, nullable=False, index=True)
    amount = Column(Float, nullable=False)
    description = Column(String, nullable=True)
    categoryUid = Column(String, nullable=False, index=True)
    updatedAt = Column(Integer, nullable=False)
    deleted = Column(Boolean, nullable=False, default=False)
    recurrenceId = Column(String, nullable=True, index=True, default=None)


class BudgetItem(Base):
    __tablename__ = "budgetitems"

    categoryUid = Column(String, primary_key=True)
    monthKey = Column(Integer, primary_key=True)
    value = Column(Float, nullable=False)
    updatedAt = Column(Integer, nullable=False)
    deleted = Column(Boolean, nullable=False, default=False)


class Recurrence(Base):
    """Defines the recurrence pattern for a recurring receipt."""

    __tablename__ = "recurrence"

    id = Column(String, primary_key=True, index=True)
    receiptId = Column(String, nullable=False, index=True)
    # Frequency: DAILY, WEEKLY, BI_WEEKLY, MONTHLY
    frequency = Column(String, nullable=False)
    startDate = Column(Integer, nullable=False)   # epochDay of first occurrence
    endDate = Column(Integer, nullable=True)       # epochDay of last occurrence; NULL = ongoing
    dayOfPeriod = Column(Integer, nullable=False)  # day within period (e.g. day of month for MONTHLY)


class ValidityLookup(Base):
    """Pre-computed lookup: which months a recurrence is active in."""

    __tablename__ = "validity_lookup"

    id = Column(String, primary_key=True, index=True)
    recurrenceId = Column(String, nullable=False, index=True)
    targetMonth = Column(Integer, nullable=False, index=True)  # epochDay of first day of month
    isActive = Column(Boolean, nullable=False, default=True)

    __table_args__ = (
        UniqueConstraint("recurrenceId", "targetMonth", name="uq_validity_lookup_recurrence_month"),
    )
