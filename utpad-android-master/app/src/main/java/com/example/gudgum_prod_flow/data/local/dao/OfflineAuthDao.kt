package com.example.gudgum_prod_flow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.gudgum_prod_flow.data.local.entity.OfflineAuthEvent

@Dao
interface OfflineAuthDao {

    @Insert
    suspend fun insertEvent(event: OfflineAuthEvent)

    @Query("SELECT * FROM offline_auth_events WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedEvents(): List<OfflineAuthEvent>

    @Query("UPDATE offline_auth_events SET synced = 1 WHERE eventId = :eventId")
    suspend fun markSynced(eventId: String)

    @Query("DELETE FROM offline_auth_events WHERE synced = 1 AND timestamp < :cutoffTimestamp")
    suspend fun deleteSyncedEvents(cutoffTimestamp: Long)
}
