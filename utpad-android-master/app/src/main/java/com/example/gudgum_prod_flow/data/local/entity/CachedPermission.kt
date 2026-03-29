package com.example.gudgum_prod_flow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_permissions")
data class CachedPermission(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val module: String,
    val action: String,
    val resourceScope: String,
    val cachedAt: Long
)
