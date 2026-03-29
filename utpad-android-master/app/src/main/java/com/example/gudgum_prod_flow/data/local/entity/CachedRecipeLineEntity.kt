package com.example.gudgum_prod_flow.data.local.entity

import androidx.room.Entity

@Entity(tableName = "cached_recipe_lines", primaryKeys = ["recipeId", "ingredientId"])
data class CachedRecipeLineEntity(
    val recipeId: String,
    val ingredientId: String,
    val ingredientName: String,
    val plannedQty: Double,
    val unit: String,
)
