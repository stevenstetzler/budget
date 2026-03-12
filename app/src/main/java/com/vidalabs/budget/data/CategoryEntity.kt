package com.vidalabs.budget.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["uid"], unique = true),
        Index(value = ["name"], unique = true),
    ]
)
data class CategoryEntity(
    @PrimaryKey val uid: String,   // UUID
    val name: String,
    val isPositive: Boolean,
    val updatedAt: Long,
    val deleted: Boolean = false
)
