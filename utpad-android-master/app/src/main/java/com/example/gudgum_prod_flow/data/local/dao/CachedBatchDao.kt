package com.example.gudgum_prod_flow.data.local.dao

import androidx.room.*
import com.example.gudgum_prod_flow.data.local.entity.CachedBatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedBatchDao {
    @Query("SELECT * FROM cached_batches WHERE status = 'open' ORDER BY productionDate ASC")
    fun getOpenBatches(): Flow<List<CachedBatchEntity>>

    @Query("SELECT * FROM cached_batches WHERE status = 'packed' ORDER BY productionDate ASC")
    fun getPackedBatches(): Flow<List<CachedBatchEntity>>

    @Query("SELECT * FROM cached_batches ORDER BY productionDate ASC")
    fun getAllBatches(): Flow<List<CachedBatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBatch(batch: CachedBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(batches: List<CachedBatchEntity>)

    @Query("DELETE FROM cached_batches")
    suspend fun deleteAll()
}
