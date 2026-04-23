package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.DispatchedBatchDto
import com.example.gudgum_prod_flow.data.remote.dto.GgCustomerDto
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceDto
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
     * grouped by batch_code ordered by oldest session_date ASC (true FIFO).
     * Batches with zero or negative available boxes are excluded.
     */
    suspend fun getInventoryByFlavor(flavorId: String): Result<List<ProductionBatchFifoDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val sessionsResp = api.getPackingSessionsByFlavor(flavorId = "eq.$flavorId")
            val sessions = if (sessionsResp.isSuccessful) sessionsResp.body() ?: emptyList()
                           else error("Failed to load packing sessions for FIFO: ${sessionsResp.code()}")

            val dispatchResp = api.getDispatchedBoxesByFlavor(flavorId = "eq.$flavorId")
            val dispatchEvents = if (dispatchResp.isSuccessful) dispatchResp.body() ?: emptyList()
                                 else {
                                     Log.w(TAG, "getDispatchedBoxesByFlavor failed [${dispatchResp.code()}], treating as zero")
                                     emptyList()
                                 }

            val dispatchedPerBatch: Map<String, Int> = dispatchEvents
                .groupBy { it.batchCode }
                .mapValues { (_, events) -> events.sumOf { it.boxesDispatched } }

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
     * Submit FIFO pack+dispatch: inserts dispatch_events for every allocation line
     * (including partial-stock flavors), then PATCHes the invoice with updated
     * packed_boxes per flavor and optional is_packed / is_dispatched flags.
     *
     * - isPacked = true only when ALL flavors will be fully packed after this call
     * - isDispatched = true only when isPacked AND worker chose to dispatch now
     * - Inventory reduces immediately via dispatch_events insert
     */
    suspend fun submitFifoPackDispatch(
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
                        if (alloc.unitsToTake <= 0) continue
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
                        Log.d(TAG, "insertDispatchEvent [$code] batch=${alloc.batchCode} flavor=${result.flavorId} boxes=${alloc.unitsToTake}")
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
                            dispatchedAt = if (isDispatched) dispatchDate else null,
                            items = updatedItems,
                        ),
                    )
                    Log.d(TAG, "updateInvoiceStatus success for $invoiceId isPacked=$isPacked isDispatched=$isDispatched")
                } catch (e: Exception) {
                    Log.w(TAG, "updateInvoiceStatus non-fatal for $invoiceId: ${e.message}")
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
                        summary = "Pack dispatch queued — $totalBoxes boxes for $invoiceNumber",
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
     * Dispatch a BLUE invoice: inventory was already reduced during packing.
     * Only updates is_dispatched=true and dispatch_date on the invoice.
     */
    suspend fun submitBlueDispatch(
        invoiceId: String,
        dispatchDate: String,
        workerId: String,
        isOnline: Boolean,
    ): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            if (isOnline) {
                val response = api.updateInvoiceStatus(
                    invoiceId = "eq.$invoiceId",
                    body = UpdateInvoiceStatusRequest(
                        isDispatched = true,
                        dispatchedAt = dispatchDate,
                    ),
                )
                val code = response.code()
                if (code !in 200..204) {
                    val errBody = response.errorBody()?.string() ?: ""
                    Log.e(TAG, "submitBlueDispatch failed [$code]: $errBody")
                    error("Failed to dispatch invoice: HTTP $code")
                }
                Log.d(TAG, "submitBlueDispatch success [$code] for $invoiceId on $dispatchDate")
            } else {
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "dispatch",
                        workerId = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = "",
                        quantity = 0.0,
                        unit = "boxes",
                        summary = "Blue dispatch queued for invoice $invoiceId",
                        payloadJson = JSONObject().apply {
                            put("invoice_id", invoiceId)
                            put("is_dispatched", true)
                            put("dispatch_date", dispatchDate)
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
