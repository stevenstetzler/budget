from sqlalchemy import Boolean, Column, Float, Integer, String
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


class BudgetItem(Base):
    __tablename__ = "budgetitems"

    categoryUid = Column(String, primary_key=True)
    monthKey = Column(Integer, primary_key=True)
    value = Column(Float, nullable=False)
    updatedAt = Column(Integer, nullable=False)
    deleted = Column(Boolean, nullable=False, default=False)
