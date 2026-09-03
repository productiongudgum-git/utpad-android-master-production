package com.example.gudgum_prod_flow.data.local.dao

import androidx.room.*
import com.example.gudgum_prod_flow.data.local.entity.CachedFlavorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedFlavorDao {
    @Query("SELECT * FROM cached_flavors WHERE active = 1 ORDER BY name ASC")
    fun getActiveFlavors(): Flow<List<CachedFlavorEntity>>

    /** Base flavours only — what Production offers, since a variant is never produced. */
    @Query("SELECT * FROM cached_flavors WHERE active = 1 AND parentFlavorId IS NULL ORDER BY name ASC")
    fun getActiveBaseFlavors(): Flow<List<CachedFlavorEntity>>

    /**
     * The box formats a batch of [baseFlavorId] can be packed into: the flavour
     * itself (the standard format) followed by its packing variants, larger box
     * first. Returns a single row when the flavour has no variants, which lets
     * the packing flow skip the format question entirely.
     */
    @Query(
        """
        SELECT * FROM cached_flavors
        WHERE active = 1 AND (id = :baseFlavorId OR parentFlavorId = :baseFlavorId)
        ORDER BY (parentFlavorId IS NOT NULL) ASC, unitsPerBox DESC
        """
    )
    suspend fun getPackFormatsFor(baseFlavorId: String): List<CachedFlavorEntity>

    @Query("SELECT * FROM cached_flavors WHERE id = :id")
    suspend fun getById(id: String): CachedFlavorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(flavors: List<CachedFlavorEntity>)

    @Query("DELETE FROM cached_flavors")
    suspend fun deleteAll()
}
