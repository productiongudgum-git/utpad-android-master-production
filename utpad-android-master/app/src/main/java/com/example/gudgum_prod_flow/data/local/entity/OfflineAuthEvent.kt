package com.example.gudgum_prod_flow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "offline_auth_events")
data class OfflineAuthEvent(
    @PrimaryKey val eventId: String = UUID.randomUUID().toString(),
    val userId: String,
    val eventType: String, // "login", "logout", "action"
    val timestamp: Long,
    val metadata: String, // JSON payload
    val synced: Boolean = false
)
