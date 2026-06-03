package com.example.gudgum_prod_flow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Payload for POST /api/v1/ops/worker-devices. */
@Serializable
data class RegisterWorkerDeviceRequest(
    @SerialName("worker_id") val workerId: String,
    @SerialName("fcm_token") val fcmToken: String,
    @SerialName("platform")  val platform: String = "android",
)

/** Response shape from the OPS API after upsert. */
@Serializable
data class WorkerDeviceResponse(
    val id: String? = null,
    @SerialName("worker_id")    val workerId: String? = null,
    @SerialName("fcm_token")    val fcmToken: String? = null,
    @SerialName("platform")     val platform: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)
