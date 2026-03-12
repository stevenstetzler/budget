package com.vidalabs.budget.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "budgetitems",
    primaryKeys = ["categoryUid", "monthKey"],
    indices = [Index("monthKey"), Index("categoryUid")]
)
data class BudgetItemEntity(
    val categoryUid: String,
    val monthKey: Int,              // YYYYMM
    val value: Double,              // positive
    val updatedAt: Long,
    val deleted: Boolean = false
)
