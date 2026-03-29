package com.example.gudgum_prod_flow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_ingredients")
data class CachedIngredientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String,
    val active: Boolean = true,
    val defaultSupplierName: String? = null,
)
