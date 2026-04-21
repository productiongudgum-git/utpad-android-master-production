package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.DispatchedBatchDto
import com.example.gudgum_prod_flow.data.remote.dto.GgCustomerDto
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceDto
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceItemDto
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceItemJson
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchFifoDto
import com.example.gudgum_prod_flow.data.remote.dto.UpdateInvoiceStatusRequest
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchDto
import com.example.gudgum_prod_flow.data.remote.dto.SubmitDispatchEventRequest
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DispatchRepository @Inject constructor(
    private val pendingDao: PendingOperationEventDao,
) {
    private val api = SupabaseApiClient.api

    companion object {
        private const val TAG = "DispatchRepository"
    }

    suspend fun getDispatchedBatches(): Result<List<DispatchedBatchDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getDispatchedBatches()
            if (response.isSuccessful) response.body() ?: emptyList()
            else error("Failed to load dispatched batches: ${response.code()}")
        }
    }

    suspend fun getCustomers(): Result<List<GgCustomerDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getGgCustomers()
            if (response.isSuccessful) response.body() ?: emptyList()
            else error("Failed to load customers: ${response.code()}")
        }
    }

    suspend fun getOpenBatches(): Result<List<ProductionBatchDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getOpenBatches()
            if (response.isSuccessful) response.body() ?: emptyList()
            else emptyList()
        }
    }

    // ── Invoice-based dispatch methods ───────────────────────────────

    suspend fun getActiveInvoices(): Result<List<InvoiceDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getActiveInvoices()
            if (response.isSuccessful) response.body() ?: emptyList()
            else error("Failed to load invoices: ${response.code()}")
        }
    }

    /**
     * Returns available inventory per batch for FIFO allocation.
     *
     * Available boxes = SUM(packing_sessions.boxes_packed) - SUM(dispatch_events.boxes_dispatched),
     * grouped by batch_code and ordered by oldest session_date ASC (true FIFO).
     * Batches with zero or negative available boxes are excluded.
     */
    suspend fun getInventoryByFlavor(flavorId: String): Result<List<ProductionBatchFifoDto>> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. All packing sessions for this flavor, ordered oldest first
            val sessionsResp = api.getPackingSessionsByFlavor(flavorId = "eq.$flavorId")
            val sessions = if (sessionsResp.isSuccessful) sessionsResp.body() ?: emptyList()
                           else error("Failed to load packing sessions for FIFO: ${sessionsResp.code()}")

            // 2. All dispatch events for this flavor (non-fatal: treat missing as zero)
            val dispatchResp = api.getDispatchedBoxesByFlavor(flavorId = "eq.$flavorId")
            val dispatchEvents = if (dispatchResp.isSuccessful) dispatchResp.body() ?: emptyList()
                                 else {
                                     Log.w(TAG, "getDispatchedBoxesByFlavor failed [${dispatchResp.code()}], treating as zero")
                                     emptyList()
                                 }

            // 3. Sum dispatched boxes per batch_code
            val dispatchedPerBatch: Map<String, Int> = dispatchEvents
                .groupBy { it.batchCode }
                .mapValues { (_, events) -> events.sumOf { it.boxesDispatched } }

            // 4. Group sessions by batch_code; sessions are already sorted ASC so first() = oldest date
            sessions.groupBy { it.batchCode }
                .mapNotNull { (batchCode, batchSessions) ->
                    val totalPacked = batchSessions.sumOf { it.boxesPacked }
                    val alreadyDispatched = dispatchedPerBatch[batchCode] ?: 0
                    val available = maxOf(0, totalPacked - alreadyDispatched)
                    if (available <= 0) return@mapNotNull null
                    val oldestSessionDate = batchSessions.minOf { it.sessionDate }
                    ProductionBatchFifoDto(
                        id = batchCode,
                        batchCode = batchCode,
                        flavorId = flavorId,
                        productionDate = oldestSessionDate,
                        expectedBoxes = available,
                        expectedUnits = available * 15,
                    )
                }
                .sortedBy { it.productionDate }
        }
    }

    /**
     * Returns already-dispatched boxes per flavor_id for a specific invoice.
     * Used to compute delta when an invoice is edited after partial dispatch.
     * Map key = flavorId, value = total boxes already dispatched for that flavor on this invoice.
     */
    suspend fun getAlreadyDispatchedPerFlavor(invoiceId: String): Result<Map<String, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = api.getDispatchEventsByInvoice(invoiceId = "eq.$invoiceId")
            val events = if (resp.isSuccessful) resp.body() ?: emptyList()
                         else {
                             Log.w(TAG, "getDispatchEventsByInvoice failed [${resp.code()}]")
                             emptyList()
                         }
            events
                .groupBy { it.flavorId.orEmpty() }
                .filterKeys { it.isNotEmpty() }
                .mapValues { (_, es) -> es.sumOf { it.boxesDispatched } }
        }
    }

    /**
     * Submit a FIFO dispatch: creates dispatch_events for each FIFO allocation line,
     * deducts inventory, and optionally updates invoice packed/dispatched status.
     */
    suspend fun submitFifoDispatch(
        invoiceId: String,
        invoiceNumber: String,
        customerName: String,
        flavorId: String,
        allocations: List<FifoAllocation>,
        isPacked: Boolean,
        isDispatched: Boolean,
        dispatchDate: String,
        workerId: String,
        isOnline: Boolean,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            if (isOnline) {
                for (alloc in allocations) {
                    val request = SubmitDispatchEventRequest(
                        batchCode = alloc.batchCode,
                        skuId = flavorId,
                        boxesDispatched = alloc.unitsToTake,
                        customerName = customerName,
                        invoiceNumber = invoiceNumber,
                        dispatchDate = dispatchDate,
                        workerId = workerId,
                        invoiceId = invoiceId,
                        unitsDispatched = alloc.unitsToTake * 15,
                        flavorId = flavorId,
                        isPacked = isPacked,
                        isDispatched = isDispatched,
                    )
                    val response = api.insertDispatchEvent(request)
                    val code = response.code()
                    if (code !in 200..204) {
                        val errBody = response.errorBody()?.string() ?: ""
                        Log.e(TAG, "insertDispatchEvent failed [$code] for ${alloc.batchCode}: $errBody")
                        error("Failed to record dispatch for batch ${alloc.batchCode}: HTTP $code")
                    }
                    Log.d(TAG, "insertDispatchEvent success [$code] for ${alloc.batchCode}")
                }
                if (isPacked || isDispatched) {
                    try {
                        val now = java.time.Instant.now().toString()
                        val statusResp = api.updateInvoiceStatus(
                            invoiceId = "eq.$invoiceId",
                            body = UpdateInvoiceStatusRequest(
                                isPacked = if (isPacked) true else null,
                                packedAt = if (isPacked) now else null,
                                isDispatched = if (isDispatched) true else null,
                                dispatchedAt = if (isDispatched) now else null,
                            ),
                        )
                        if (statusResp.code() !in 200..204) {
                            Log.w(TAG, "updateInvoiceStatus non-fatal [${statusResp.code()}] for invoice $invoiceId")
                        } else {
                            Log.d(TAG, "updateInvoiceStatus success [${statusResp.code()}] for invoice $invoiceId")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "updateInvoiceStatus threw (non-fatal) for invoice $invoiceId: ${e.message}")
                    }
                }
            } else {
                val totalUnits = allocations.sumOf { it.unitsToTake }
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "dispatch",
                        workerId = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = allocations.firstOrNull()?.batchCode ?: "",
                        quantity = totalUnits.toDouble(),
                        unit = "units",
                        summary = "FIFO dispatch queued — $totalUnits units for invoice $invoiceNumber",
                        payloadJson = JSONObject().apply {
                            put("invoice_id", invoiceId)
                            put("invoice_number", invoiceNumber)
                            put("customer_name", customerName)
                            put("flavor_id", flavorId)
                            put("total_units", totalUnits)
                            put("is_packed", isPacked)
                            put("is_dispatched", isDispatched)
                            put("dispatch_date", dispatchDate)
                        }.toString(),
                    )
                )
            }
        }
    }

    /**
     * Submit dispatch events for multiple flavors in one invoice.
     * Only processes allocations passed in (caller filters to sufficient ones).
     * Updates invoice status and items jsonb in a single PATCH after inserts.
     */
    suspend fun submitMultipleFlavorsDispatch(
        invoiceId: String,
        invoiceNumber: String,
        customerName: String,
        allocations: List<FifoAllocationResult>,
        isPacked: Boolean,
        isDispatched: Boolean,
        dispatchDate: String,
        workerId: String,
        isOnline: Boolean,
        updatedItems: List<InvoiceItemJson>,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            if (isOnline) {
                for (result in allocations) {
                    for (alloc in result.allocations) {
                        val request = SubmitDispatchEventRequest(
                            batchCode = alloc.batchCode,
                            skuId = result.flavorId,
                            boxesDispatched = alloc.unitsToTake,
                            customerName = customerName,
                            invoiceNumber = invoiceNumber,
                            dispatchDate = dispatchDate,
                            workerId = workerId,
                            invoiceId = invoiceId,
                            unitsDispatched = alloc.unitsToTake * 15,
                            flavorId = result.flavorId,
                            isPacked = isPacked,
                            isDispatched = isDispatched,
                        )
                        val response = api.insertDispatchEvent(request)
                        val code = response.code()
                        if (code !in 200..204) {
                            val errBody = response.errorBody()?.string() ?: ""
                            Log.e(TAG, "insertDispatchEvent failed [$code] ${alloc.batchCode}: $errBody")
                            error("Failed to record dispatch for ${alloc.batchCode}: HTTP $code")
                        }
                        Log.d(TAG, "insertDispatchEvent [$code] ${alloc.batchCode} flavor=${result.flavorId}")
                    }
                }
                try {
                    val now = java.time.Instant.now().toString()
                    api.updateInvoiceStatus(
                        invoiceId = "eq.$invoiceId",
                        body = UpdateInvoiceStatusRequest(
                            isPacked = if (isPacked) true else null,
                            packedAt = if (isPacked) now else null,
                            isDispatched = if (isDispatched) true else null,
                            dispatchedAt = if (isDispatched) now else null,
                            items = updatedItems,
                        ),
                    )
                    Log.d(TAG, "updateInvoiceStatus+items success for $invoiceId")
                } catch (e: Exception) {
                    Log.w(TAG, "updateInvoiceStatus non-fatal: ${e.message}")
                }
            } else {
                val totalBoxes = allocations.sumOf { r -> r.allocations.sumOf { it.unitsToTake } }
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "dispatch",
                        workerId = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = allocations.firstOrNull()?.allocations?.firstOrNull()?.batchCode ?: "",
                        quantity = totalBoxes.toDouble(),
                        unit = "boxes",
                        summary = "Multi-flavor dispatch queued — $totalBoxes boxes for $invoiceNumber",
                        payloadJson = JSONObject().apply {
                            put("invoice_id", invoiceId)
                            put("invoice_number", invoiceNumber)
                            put("customer_name", customerName)
                            put("total_boxes", totalBoxes)
                            put("is_packed", isPacked)
                            put("is_dispatched", isDispatched)
                            put("dispatch_date", dispatchDate)
                        }.toString(),
                    )
                )
            }
        }
    }

    /**
     * Case 1 yellow dispatch: invoice was never dispatched before.
     * Computes FIFO internally and inserts dispatch_events for all flavors, then
     * sets is_dispatched=true on the invoice. No pre-computed allocations needed.
     */
    suspend fun submitYellowCase1Dispatch(
        invoiceId: String,
        invoiceNumber: String,
        customerName: String,
        items: List<InvoiceItemDto>,
        workerId: String,
        isOnline: Boolean,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            if (isOnline) {
                val dispatchDate = java.time.LocalDate.now().toString()
                val now = java.time.Instant.now().toString()
                val updatedItems = mutableListOf<InvoiceItemJson>()

                for (item in items) {
                    val inventory = getInventoryByFlavor(item.flavorId).getOrElse { emptyList() }
                    var remaining = item.resolvedBoxes
                    for (batch in inventory) {
                        if (remaining <= 0) break
                        val take = minOf(remaining, batch.expectedBoxes)
                        val request = SubmitDispatchEventRequest(
                            batchCode = batch.batchCode,
                            skuId = item.flavorId,
                            boxesDispatched = take,
                            customerName = customerName,
                            invoiceNumber = invoiceNumber,
                            dispatchDate = dispatchDate,
                            workerId = workerId,
                            invoiceId = invoiceId,
                            unitsDispatched = take * 15,
                            flavorId = item.flavorId,
                            isPacked = true,
                            isDispatched = true,
                        )
                        val response = api.insertDispatchEvent(request)
                        val code = response.code()
                        if (code !in 200..204) {
                            val errBody = response.errorBody()?.string() ?: ""
                            Log.e(TAG, "Case1 insertDispatchEvent failed [$code] ${batch.batchCode}: $errBody")
                            error("Failed to record dispatch for ${batch.batchCode}: HTTP $code")
                        }
                        Log.d(TAG, "Case1 dispatch [$code] ${batch.batchCode} ${take}boxes flavor=${item.flavorId}")
                        remaining -= take
                    }
                    updatedItems.add(
                        InvoiceItemJson(
                            flavorId = item.flavorId,
                            flavorName = item.flavor?.name ?: "Unknown",
                            quantityUnits = item.quantityUnits,
                            quantityBoxes = item.quantityBoxes,
                            dispatched = true,
                        )
                    )
                }

                try {
                    api.updateInvoiceStatus(
                        invoiceId = "eq.$invoiceId",
                        body = UpdateInvoiceStatusRequest(
                            isPacked = true,
                            isDispatched = true,
                            dispatchedAt = now,
                            items = updatedItems,
                        ),
                    )
                    Log.d(TAG, "Case1 invoice update success for $invoiceId")
                } catch (e: Exception) {
                    Log.w(TAG, "Case1 updateInvoiceStatus non-fatal: ${e.message}")
                }
            } else {
                val totalBoxes = items.sumOf { it.resolvedBoxes }
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "dispatch",
                        workerId = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = "",
                        quantity = totalBoxes.toDouble(),
                        unit = "boxes",
                        summary = "Yellow dispatch queued — $totalBoxes boxes for $invoiceNumber",
                        payloadJson = JSONObject().apply {
                            put("invoice_id", invoiceId)
                            put("invoice_number", invoiceNumber)
                            put("customer_name", customerName)
                            put("total_boxes", totalBoxes)
                            put("is_packed", true)
                            put("is_dispatched", true)
                        }.toString(),
                    )
                )
            }
        }
    }

    /** Toggle packed/dispatched status on an invoice */
    suspend fun updateInvoiceStatus(
        invoiceId: String,
        isPacked: Boolean? = null,
        isDispatched: Boolean? = null,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            if (isPacked != null || isDispatched != null) {
                val now = java.time.Instant.now().toString()
                val response = api.updateInvoiceStatus(
                    invoiceId = "eq.$invoiceId",
                    body = UpdateInvoiceStatusRequest(
                        isPacked = isPacked,
                        packedAt = if (isPacked == true) now else null,
                        isDispatched = isDispatched,
                        dispatchedAt = if (isDispatched == true) now else null,
                    ),
                )
                if (!response.isSuccessful) {
                    error("Failed to update invoice status: ${response.code()}")
                }
            }
        }
    }
}

/** A single FIFO allocation line: which inventory row to take how many units from */
data class FifoAllocation(
    val inventoryId: String,
    val batchCode: String,
    val availableUnits: Int,
    val unitsToTake: Int,
)

/** FIFO allocation result for one flavor: computed stock snapshot + allocation lines */
data class FifoAllocationResult(
    val flavorId: String,
    val flavorName: String,
    val boxesNeeded: Int,
    val availableBoxes: Int,
    val allocations: List<FifoAllocation>,
) {
    val isSufficient: Boolean get() = availableBoxes >= boxesNeeded
}

private val WorkerIdentityStore get() = com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
