package com.example.gudgum_prod_flow.data.local.dao

import androidx.room.*
import com.example.gudgum_prod_flow.data.local.entity.CachedRecipeLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedRecipeLineDao {
    @Query("SELECT * FROM cached_recipe_lines WHERE recipeId = :recipeId ORDER BY ingredientName ASC")
    fun getByRecipeId(recipeId: String): Flow<List<CachedRecipeLineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lines: List<CachedRecipeLineEntity>)

    @Query("DELETE FROM cached_recipe_lines WHERE recipeId = :recipeId")
    suspend fun deleteByRecipeId(recipeId: String)

    @Query("DELETE FROM cached_recipe_lines")
    suspend fun deleteAll()
}
