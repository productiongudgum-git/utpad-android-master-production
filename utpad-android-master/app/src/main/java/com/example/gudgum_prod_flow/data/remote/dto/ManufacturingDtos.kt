package com.example.gudgum_prod_flow.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Flavors / SKUs (gg_flavors) ────────────────────────────────────
@Serializable
data class FlavorDto(
    val id: String,
    val name: String,
    val code: String,
    val active: Boolean = true,
    @SerialName("recipe_id") val recipeId: String? = null,
    @SerialName("yield_threshold") val yieldThreshold: Double? = null,
    @SerialName("shelf_life_days") val shelfLifeDays: Int? = null,
)

// ── Recipe Lines (BOM) — recipe_lines joined with gg_ingredients ──
@Serializable
data class RecipeLineDto(
    @SerialName("recipe_id") val recipeId: String,
    @SerialName("ingredient_id") val ingredientId: String,
    val qty: Double,
    // Joined fields from gg_ingredients
    val ingredient: RecipeLineIngredientDto? = null,
)

@Serializable
data class RecipeLineIngredientDto(
    val id: String,
    val name: String,
    @SerialName("default_unit") val defaultUnit: String? = null,
)

// ── Production Batches (production_batches) ────────────────────────
@Serializable
data class SubmitProductionBatchRequest(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("flavor_id") val flavorId: String,
    @SerialName("recipe_id") val recipeId: String,
    @SerialName("production_date") val productionDate: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("planned_yield") val plannedYield: Double? = null,
    @SerialName("actual_yield") val actualYield: Double? = null,
    val status: String = "open",
    @SerialName("batch_size_units") val batchSizeUnits: Int? = null,
    @SerialName("raw_material_input") val rawMaterialInput: Double? = null,
    @SerialName("expected_yield") val expectedYield: Double? = null,
    @SerialName("expected_boxes") val expectedBoxes: Int? = null,
    @SerialName("expected_units") val expectedUnits: Int? = null,
)

@Serializable
data class ProductionBatchDto(
    val id: String? = null,
    @SerialName("batch_code") val batchCode: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("flavor_id") val flavorId: String? = null,
    @SerialName("recipe_id") val recipeId: String? = null,
    @SerialName("production_date") val productionDate: String,
    @SerialName("worker_id") val workerId: String? = null,
    val status: String = "open",
    @SerialName("planned_yield") val plannedYield: Double? = null,
    @SerialName("actual_yield") val actualYield: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // Joined flavor
    val flavor: FlavorJoinDto? = null,
)

@Serializable
data class FlavorJoinDto(
    val id: String? = null,
    val name: String? = null,
    val code: String? = null,
)

// ── Packing Sessions (packing_sessions) ────────────────────────────
@Serializable
data class SubmitPackingSessionRequest(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("flavor_id") val flavorId: String? = null,
    @SerialName("session_date") val sessionDate: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("boxes_packed") val boxesPacked: Int,
    @SerialName("kgs_packed") val kgsPacked: Double? = null,
    @SerialName("units_packed") val unitsPacked: Int? = null,
)

@Serializable
data class PackingSessionDto(
    val id: String? = null,
    @SerialName("batch_code") val batchCode: String,
    @SerialName("flavor_id") val flavorId: String? = null,
    @SerialName("session_date") val sessionDate: String,
    @SerialName("worker_id") val workerId: String? = null,
    @SerialName("boxes_packed") val boxesPacked: Int = 0,
    @SerialName("kgs_packed") val kgsPacked: Double? = null,
    @SerialName("units_packed") val unitsPacked: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

// ── Dispatch Events (dispatch_events) ──────────────────────────────
@Serializable
data class SubmitDispatchEventRequest(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("boxes_dispatched") val boxesDispatched: Int,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("invoice_number") val invoiceNumber: String,
    @SerialName("dispatch_date") val dispatchDate: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("invoice_id") val invoiceId: String? = null,
    @SerialName("units_dispatched") val unitsDispatched: Int = 0,
    @SerialName("flavor_id") val flavorId: String? = null,
    @SerialName("is_packed") val isPacked: Boolean = false,
    @SerialName("is_dispatched") val isDispatched: Boolean = false,
)

@Serializable
data class DispatchedBatchDto(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("boxes_dispatched") val boxesDispatched: Int,
    @SerialName("dispatch_date") val dispatchDate: String,
    @SerialName("customer_name") val customerName: String? = null,
    val sku: FlavorJoinDto? = null,
)

// ── Invoices (gg_invoices) — items stored as JSON column ──────────
@Serializable
data class InvoiceItemJson(
    @SerialName("flavor_id") val flavorId: String = "",
    @SerialName("flavor_name") val flavorName: String = "Unknown",
    @SerialName("quantity_units") val quantityUnits: Int = 0,
    @SerialName("quantity_boxes") val quantityBoxes: Int? = null,
)

@Serializable
data class InvoiceDto(
    val id: String,
    @SerialName("invoice_number") val invoiceNumber: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("customer_name") val customerName: String,
    @SerialName("is_packed") val isPacked: Boolean = false,
    @SerialName("is_dispatched") val isDispatched: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    val items: List<InvoiceItemJson> = emptyList(),
)

// ── Invoice Items (constructed from gg_invoices.items JSON, not a DB table) ──
data class InvoiceItemDto(
    val id: String = "",
    val invoiceId: String = "",
    val flavorId: String,
    val quantityUnits: Int,
    val quantityBoxes: Int? = null,
    val flavor: FlavorJoinDto? = null,
) {
    /** Boxes to dispatch: use quantity_boxes if present and > 0, else ceil(quantity_units / 15). */
    val resolvedBoxes: Int get() =
        if (quantityBoxes != null && quantityBoxes > 0) quantityBoxes
        else Math.ceil(quantityUnits / 15.0).toInt()
}

// ── Inventory Finished Goods (inventory_finished_goods) ────────────
@Serializable
data class InventoryFinishedGoodDto(
    val id: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("batch_code") val batchCode: String,
    @SerialName("units_available") val unitsAvailable: Int = 0,
    @SerialName("boxes_available") val boxesAvailable: Int = 0,
)

// ── Inwarding (gg_inwarding) ───────────────────────────────────────
@Serializable
data class SubmitInwardEventRequest(
    @SerialName("ingredient_id") val ingredientId: String,
    val qty: Double,
    val unit: String,
    @SerialName("inward_date") val inwardDate: String,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("lot_ref") val lotRef: String? = null,
    @SerialName("vendor_id") val vendorId: String? = null,
    @SerialName("worker_id") val workerId: String,
)

// Alias for the gg_inwarding insert — maps to the same table columns
@Serializable
data class GgInwardingRequest(
    @SerialName("ingredient_id") val ingredientId: String,
    val qty: Double,
    val unit: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("inward_date") val inwardDate: String,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("lot_ref") val lotRef: String? = null,
    @SerialName("worker_id") val workerId: String,
)

// ── Returns (returns_events) ───────────────────────────────────────
@Serializable
data class SubmitReturnEventRequest(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("sku_id") val skuId: String,
    @SerialName("qty_returned") val qtyReturned: Int,
    val reason: String? = null,
    @SerialName("return_date") val returnDate: String,
    @SerialName("worker_id") val workerId: String,
)

// ── FIFO ───────────────────────────────────────────────────────────
@Serializable
data class FifoAllocationRequest(
    @SerialName("p_sku_id") val skuId: String,
    @SerialName("p_qty") val qty: Int,
)

@Serializable
data class FifoAllocationLine(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("production_date") val productionDate: String,
    @SerialName("boxes_available") val boxesAvailable: Int,
    @SerialName("boxes_to_take") val boxesToTake: Int,
)

/** PATCH body for updating invoice packed/dispatched status */
@Serializable
data class UpdateInvoiceStatusRequest(
    @SerialName("is_packed") val isPacked: Boolean? = null,
    @SerialName("is_dispatched") val isDispatched: Boolean? = null,
    @SerialName("packed_at") val packedAt: String? = null,
    @SerialName("dispatched_at") val dispatchedAt: String? = null,
)

/** PATCH body for updating available units in inventory_finished_goods */
@Serializable
data class UpdateInventoryRequest(
    @SerialName("units_available") val unitsAvailable: Int,
)

/** PATCH body for deducting stock from production_batches after dispatch */
@Serializable
data class PatchProductionBatchRequest(
    @SerialName("expected_boxes") val expectedBoxes: Int,
    @SerialName("expected_units") val expectedUnits: Int,
)

/** Lightweight projection of production_batches used for FIFO allocation */
@Serializable
data class ProductionBatchFifoDto(
    val id: String,
    @SerialName("batch_code") val batchCode: String,
    @SerialName("flavor_id") val flavorId: String,
    @SerialName("production_date") val productionDate: String,
    /** Available boxes = packed - dispatched (computed in DispatchRepository, not a raw DB column) */
    @SerialName("expected_boxes") val expectedBoxes: Int = 0,
    @SerialName("expected_units") val expectedUnits: Int = 0,
)

/** Packing session row fetched for FIFO available-boxes computation */
@Serializable
data class PackingSessionFifoDto(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("flavor_id") val flavorId: String? = null,
    @SerialName("session_date") val sessionDate: String,
    @SerialName("boxes_packed") val boxesPacked: Int = 0,
)

/** Dispatch event row fetched for FIFO already-dispatched computation */
@Serializable
data class DispatchEventFifoDto(
    @SerialName("batch_code") val batchCode: String,
    @SerialName("boxes_dispatched") val boxesDispatched: Int = 0,
)

// ── Gg_ table DTOs ────────────────────────────────────────────────
@Serializable
data class GgUserDto(
    val id: String,
    @SerialName("mobile_number") val mobileNumber: String,
    val name: String,
    val role: String,
    val modules: List<String> = emptyList(),
    val active: Boolean = true,
)

@Serializable
data class GgCustomerDto(
    val id: String,
    val name: String,
    @SerialName("contact_person") val contactPerson: String? = null,
    val phone: String? = null,
)

@Serializable
data class GgFlavorDto(
    val id: String,
    val name: String,
    val code: String,
    val active: Boolean = true,
)

@Serializable
data class GgIngredientDto(
    val id: String,
    val name: String,
    @SerialName("default_unit") val defaultUnit: String,
)

@Serializable
data class GgRecipeDto(
    val id: String,
    val title: String? = null,
    val code: String? = null,
    @SerialName("flavor_id") val flavorId: String? = null,
    @SerialName("yield_factor") val yieldFactor: Double? = null,
    @SerialName("tolerance_pct") val tolerancePct: Double? = null,
    @SerialName("primary_ingredient_id") val primaryIngredientId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
)

@Serializable
data class GgVendorDto(
    val id: String,
    val name: String,
    val phone: String? = null,
)

@Serializable
data class GgVendorInsertRequest(
    val name: String,
    val phone: String? = null,
)

@Serializable
data class GgIngredientInsertRequest(
    val name: String,
    @SerialName("default_unit") val defaultUnit: String,
)

@Serializable
data class SkuStockDto(
    @SerialName("sku_id") val skuId: String,
    @SerialName("sku_name") val skuName: String,
    @SerialName("sku_code") val skuCode: String,
    @SerialName("boxes_available") val boxesAvailable: Int,
)
