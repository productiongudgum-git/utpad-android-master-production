package com.example.gudgum_prod_flow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "pending_operation_events")
data class PendingOperationEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val module: String,
    val workerId: String,
    val workerName: String,
    val workerRole: String,
    val batchCode: String,
    val quantity: Double,
    val unit: String,
    val summary: String,
    val payloadJson: String, // Stored as a JSON string since SQLite doesn't natively support Map/Object
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val syncAttemptCount: Int = 0,
    val lastSyncError: String? = null
)
