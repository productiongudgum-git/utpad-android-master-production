package com.example.gudgum_prod_flow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Payload for POST /api/v1/ops/d2c-requests. */
@Serializable
data class CreateD2CRequestPayload(
    val channel: String,
    @SerialName("worker_id") val workerId: String,
    val items: List<CreateD2CRequestItem>,
    val notes: String? = null,
)

@Serializable
data class CreateD2CRequestItem(
    @SerialName("flavor_id") val flavorId: String,
    val boxes: Int,
)

/** Response from create. Includes server-computed FIFO splits per item. */
@Serializable
data class CreateD2CRequestResponse(
    val id: String,
    val channel: String,
    @SerialName("worker_id") val workerId: String,
    val items: List<D2CCreateResponseItem>,
)

@Serializable
data class D2CCreateResponseItem(
    @SerialName("flavor_id") val flavorId: String,
    val boxes: Int,
    val splits: List<D2CBatchSplit>,
)

@Serializable
data class D2CBatchSplit(
    // Nullable: OPENING-STOCK rows have no production_batches row and come back null.
    @SerialName("production_batch_id") val productionBatchId: String? = null,
    @SerialName("batch_code")          val batchCode: String,
    @SerialName("batch_number")        val batchNumber: Int? = null,
    @SerialName("session_date")        val sessionDate: String? = null,
    val boxes: Int,
)

/** Existing request returned by GET list / detail. */
@Serializable
data class D2CRequestDto(
    val id: String,
    val channel: String,
    @SerialName("worker_id")     val workerId: String,
    @SerialName("header_status") val headerStatus: String,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val items: List<D2CRequestItemDto> = emptyList(),
)

@Serializable
data class D2CRequestItemDto(
    val id: String,
    @SerialName("flavor_id")        val flavorId: String,
    @SerialName("boxes_requested")  val boxesRequested: Int,
    val status: String,
    @SerialName("batch_breakdown")  val batchBreakdown: List<D2CBatchSplit>? = null,
    @SerialName("approved_by")      val approvedBy: String? = null,
    @SerialName("decided_at")       val decidedAt: String? = null,
    @SerialName("allocation_id")    val allocationId: String? = null,
)

/** Per-flavour available boxes — used by the picker on Step 2. */
@Serializable
data class FinishedGoodsAvailableRow(
    @SerialName("flavor_id")       val flavorId: String,
    @SerialName("flavor_name")     val flavorName: String,
    @SerialName("boxes_available") val boxesAvailable: Int,
)

/** Insufficient-stock error response shape. */
@Serializable
data class InsufficientStockError(
    val error: String,
    val insufficient: List<InsufficientLine> = emptyList(),
)

@Serializable
data class InsufficientLine(
    @SerialName("flavor_id") val flavorId: String,
    val requested: Int,
    val available: Int,
)
