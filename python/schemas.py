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


class ReceiptCreate(ReceiptBase):
    pass


class ReceiptResponse(ReceiptBase):
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
