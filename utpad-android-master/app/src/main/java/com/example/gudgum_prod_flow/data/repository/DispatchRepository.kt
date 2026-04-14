package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.DispatchedBatchDto
import com.example.gudgum_prod_flow.data.remote.dto.GgCustomerDto
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceDto
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                // 1. Insert dispatch event — FATAL: if this fails the whole operation fails
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
                    // 200, 201, 204 are all valid success codes from Supabase
                    val code = response.code()
                    if (code !in 200..204) {
                        val errBody = response.errorBody()?.string() ?: ""
                        Log.e(TAG, "insertDispatchEvent failed [$code] for ${alloc.batchCode}: $errBody")
                        error("Failed to record dispatch for batch ${alloc.batchCode}: HTTP $code")
                    }
                    Log.d(TAG, "insertDispatchEvent success [$code] for ${alloc.batchCode}")
                    // Note: available stock is derived dynamically from packing_sessions minus
                    // dispatch_events, so no production_batches patch is needed here.
                }

                // 2. Update invoice status — NON-FATAL: data is saved, log and continue
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
            }
        } else {
            runCatching {
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

    /** Toggle packed/dispatched status on an invoice */
    suspend fun updateInvoiceStatus(
        invoiceId: String,
        isPacked: Boolean? = null,
        isDispatched: Boolean? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
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

private val WorkerIdentityStore get() = com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
