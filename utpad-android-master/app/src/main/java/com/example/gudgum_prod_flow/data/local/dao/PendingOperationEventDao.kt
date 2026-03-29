package com.example.gudgum_prod_flow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity

@Dao
interface PendingOperationEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: PendingOperationEventEntity)

    @Query("SELECT * FROM pending_operation_events ORDER BY createdAtTimestamp ASC")
    suspend fun getAllPendingEvents(): List<PendingOperationEventEntity>

    @Update
    suspend fun updateEvent(event: PendingOperationEventEntity)

    @Query("DELETE FROM pending_operation_events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: String)
}
