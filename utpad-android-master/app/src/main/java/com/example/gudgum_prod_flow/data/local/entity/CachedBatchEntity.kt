package com.example.gudgum_prod_flow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_batches")
data class CachedBatchEntity(
    @PrimaryKey val batchCode: String,
    val skuId: String,
    val skuName: String,
    val skuCode: String,
    val productionDate: String,
    val status: String,
    val plannedYield: Double?,
    val totalPacked: Int = 0,
    val hasOverdueAlert: Boolean = false,
)
