package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.DispatchedBatchDto
import com.example.gudgum_prod_flow.data.remote.dto.GgCustomerDto
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceDto
import com.example.gudgum_prod_flow.data.remote.dto.PatchProductionBatchRequest
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

    suspend fun getInventoryByFlavor(flavorId: String): Result<List<ProductionBatchFifoDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getProductionBatchesByFlavor(flavorId = "eq.$flavorId")
            if (response.isSuccessful) response.body() ?: emptyList()
            else error("Failed to load batches for FIFO: ${response.code()}")
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
                // 1. Create dispatch events for each FIFO allocation line
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
                    if (!response.isSuccessful && response.code() != 201) {
                        val body = response.errorBody()?.string() ?: ""
                        Log.e(TAG, "Dispatch insert failed for batch ${alloc.batchCode}: ${response.code()} $body")
                        error("Failed to create dispatch for batch ${alloc.batchCode}")
                    }

                    // 2. Deduct expected_boxes and expected_units from production_batches
                    val newBoxes = alloc.availableUnits - alloc.unitsToTake
                    val newUnits = newBoxes * 15
                    val updateResp = api.patchProductionBatch(
                        id = "eq.${alloc.inventoryId}",
                        body = PatchProductionBatchRequest(
                            expectedBoxes = newBoxes,
                            expectedUnits = newUnits,
                        ),
                    )
                    if (!updateResp.isSuccessful) {
                        Log.w(TAG, "Production batch stock update failed for ${alloc.inventoryId}: ${updateResp.code()}")
                    }
                }

                // 3. Update invoice status
                if (isPacked || isDispatched) {
                    val now = java.time.Instant.now().toString()
                    api.updateInvoiceStatus(
                        invoiceId = "eq.$invoiceId",
                        body = UpdateInvoiceStatusRequest(
                            isPacked = if (isPacked) true else null,
                            packedAt = if (isPacked) now else null,
                            isDispatched = if (isDispatched) true else null,
                            dispatchedAt = if (isDispatched) now else null,
                        ),
                    )
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
