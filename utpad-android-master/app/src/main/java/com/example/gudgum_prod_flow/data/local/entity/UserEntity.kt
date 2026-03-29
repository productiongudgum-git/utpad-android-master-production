package com.example.gudgum_prod_flow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val tenantId: String,
    val phone: String,
    val name: String,
    val role: String,
    val status: String,
    val factoryIds: String, // JSON array of factory IDs
    val permissions: String, // JSON array of permission strings
    val lastSyncTimestamp: Long,
    val createdAt: Long
)
