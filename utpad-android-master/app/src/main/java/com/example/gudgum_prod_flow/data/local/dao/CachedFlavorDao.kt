package com.example.gudgum_prod_flow.data.local.dao

import androidx.room.*
import com.example.gudgum_prod_flow.data.local.entity.CachedFlavorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedFlavorDao {
    @Query("SELECT * FROM cached_flavors WHERE active = 1 ORDER BY name ASC")
    fun getActiveFlavors(): Flow<List<CachedFlavorEntity>>

    @Query("SELECT * FROM cached_flavors WHERE id = :id")
    suspend fun getById(id: String): CachedFlavorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(flavors: List<CachedFlavorEntity>)

    @Query("DELETE FROM cached_flavors")
    suspend fun deleteAll()
}
