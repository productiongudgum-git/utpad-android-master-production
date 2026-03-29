package com.example.gudgum_prod_flow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_flavors")
data class CachedFlavorEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val recipeId: String? = null,
    val active: Boolean = true,
    val yieldThreshold: Double? = null,
    val shelfLifeDays: Int? = null,
)
