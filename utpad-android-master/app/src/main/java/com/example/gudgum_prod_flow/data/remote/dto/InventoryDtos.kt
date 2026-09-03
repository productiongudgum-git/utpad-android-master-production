package com.example.gudgum_prod_flow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Result of looking up a production batch by batch_code + batch_number.
 * Used by the "Update Inventory" flow to resolve the worker's typed input to
 * a concrete production_batch_id + flavour.
 */
@Serializable
data class BatchLookupDto(
    val id: String,
    @SerialName("batch_code")   val batchCode: String,
    @SerialName("batch_number") val batchNumber: Int? = null,
    @SerialName("flavor_id")    val flavorId: String,
    val flavor: BatchLookupFlavorDto? = null,
)

@Serializable
data class BatchLookupFlavorDto(
    val id: String,
    val name: String,
)

/** Single packing_sessions row used only to sum boxes per batch (small payload). */
@Serializable
data class PackingSessionBoxesDto(
    @SerialName("boxes_packed") val boxesPacked: Int? = null,
)

/** Finished goods view: one row per flavour with packed/dispatched/net counts. */
@Serializable
data class FlavorBoxesRow(
    @SerialName("flavor_id")    val flavorId: String,
    @SerialName("boxes_packed") val boxesPacked: Int? = null,
)

@Serializable
data class FlavorDispatchRow(
    @SerialName("sku_id")            val flavorId: String,
    @SerialName("boxes_dispatched")  val boxesDispatched: Int? = null,
)
