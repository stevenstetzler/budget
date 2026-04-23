from pydantic import BaseModel
from typing import Optional


class CategoryBase(BaseModel):
    uid: str
    name: str
    isPositive: bool
    updatedAt: int
    deleted: bool = False


class CategoryCreate(CategoryBase):
    pass


class CategoryResponse(CategoryBase):
    model_config = {"from_attributes": True}


class ReceiptBase(BaseModel):
    uid: str
    epochDay: int
    amount: float
    description: Optional[str] = None
    categoryUid: str
    updatedAt: int
    deleted: bool = False
    recurrenceId: Optional[str] = None


class ReceiptCreate(ReceiptBase):
    pass


class ReceiptResponse(ReceiptBase):
    # Set for recurring receipts returned by a targetMonth query; indicates the
    # computed occurrence date within the requested month (not the base receipt date).
    occurrenceEpochDay: Optional[int] = None
    model_config = {"from_attributes": True}


class BudgetItemBase(BaseModel):
    categoryUid: str
    monthKey: int
    value: float
    updatedAt: int
    deleted: bool = False


class BudgetItemCreate(BudgetItemBase):
    pass


class BudgetItemResponse(BudgetItemBase):
    model_config = {"from_attributes": True}


class RecurrenceBase(BaseModel):
    id: str
    receiptId: str
    frequency: str  # DAILY, WEEKLY, BI_WEEKLY, MONTHLY
    startDate: int  # epochDay
    endDate: Optional[int] = None  # epochDay; None = ongoing
    dayOfPeriod: int


class RecurrenceCreate(RecurrenceBase):
    pass


class RecurrenceResponse(RecurrenceBase):
    model_config = {"from_attributes": True}


class ValidityLookupBase(BaseModel):
    id: str
    recurrenceId: str
    targetMonth: int  # epochDay of first day of month
    isActive: bool = True


class ValidityLookupCreate(ValidityLookupBase):
    pass


class ValidityLookupResponse(ValidityLookupBase):
    model_config = {"from_attributes": True}


class ValidityLookupToggle(BaseModel):
    """Payload to toggle isActive for a specific month."""
    isActive: bool
