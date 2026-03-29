package com.example.gudgum_prod_flow.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SubmitOperationEventRequest(
    val module: String,
    val workerId: String,
    val workerName: String,
    val workerRole: String,
    val batchCode: String,
    val quantity: Double,
    val unit: String,
    val summary: String,
    val payload: Map<String, String> = emptyMap(),
)

@Serializable
data class OperationEventResponse(
    val id: String,
    val module: String,
    val workerId: String,
    val workerName: String,
    val workerRole: String,
    val createdAt: String,
    val batchCode: String,
    val quantity: Double,
    val unit: String,
    val summary: String,
    val payload: Map<String, String> = emptyMap(),
)
