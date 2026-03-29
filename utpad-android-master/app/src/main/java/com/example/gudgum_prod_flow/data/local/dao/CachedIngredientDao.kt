package com.example.gudgum_prod_flow.data.local.dao

import androidx.room.*
import com.example.gudgum_prod_flow.data.local.entity.CachedIngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedIngredientDao {
    @Query("SELECT * FROM cached_ingredients WHERE active = 1 ORDER BY name ASC")
    fun getActiveIngredients(): Flow<List<CachedIngredientEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(ingredients: List<CachedIngredientEntity>)

    @Query("DELETE FROM cached_ingredients")
    suspend fun deleteAll()
}
