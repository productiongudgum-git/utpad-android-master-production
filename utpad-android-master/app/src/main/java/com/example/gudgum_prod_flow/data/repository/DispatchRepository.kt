package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.DispatchedBatchDto
import com.example.gudgum_prod_flow.data.remote.dto.GgCustomerDto
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceDto
import com.example.gudgum_prod_flow.data.remote.dto.InventoryFinishedGoodDto
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

    suspend fun getInventoryByFlavor(flavorId: String): Result<List<InventoryFinishedGoodDto>> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "getInventoryByFlavor: querying with flavorId='$flavorId' (sent as sku_id filter 'eq.$flavorId')")
            val response = api.getInventoryByFlavor(skuId = "eq.$flavorId")
            Log.d(TAG, "getInventoryByFlavor: response code=${response.code()} isSuccessful=${response.isSuccessful}")
            if (response.isSuccessful) {
                val rows = response.body() ?: emptyList()
                Log.d(TAG, "getInventoryByFlavor: returned ${rows.size} row(s)")
                rows.forEachIndexed { i, row ->
                    Log.d(TAG, "  row[$i] id=${row.id} skuId=${row.skuId} batchCode=${row.batchCode} unitsAvailable=${row.unitsAvailable}")
                }
                rows
            } else {
                val errBody = response.errorBody()?.string() ?: ""
                Log.e(TAG, "getInventoryByFlavor: error body=$errBody")
                error("Failed to load inventory: ${response.code()}")
            }
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
                        flavorId = flavorId,
                        invoiceId = invoiceId,
                        customerName = customerName,
                        invoiceNumber = invoiceNumber,
                        dispatchDate = dispatchDate,
                        workerId = workerId,
                        boxesDispatched = alloc.unitsToTake / 15,
                        unitsDispatched = alloc.unitsToTake,
                        isPacked = isPacked,
                        isDispatched = isDispatched,
                    )
                    val response = api.insertDispatchEvent(request)
                    if (!response.isSuccessful && response.code() != 201) {
                        val body = response.errorBody()?.string() ?: ""
                        Log.e(TAG, "Dispatch insert failed for batch ${alloc.batchCode}: ${response.code()} $body")
                        error("Failed to create dispatch for batch ${alloc.batchCode}")
                    }

                    // 2. Deduct units from inventory
                    val newUnits = alloc.availableUnits - alloc.unitsToTake
                    val updateResp = api.updateInventory(
                        id = "eq.${alloc.inventoryId}",
                        body = mapOf("units_available" to newUnits),
                    )
                    if (!updateResp.isSuccessful) {
                        Log.w(TAG, "Inventory update failed for ${alloc.inventoryId}: ${updateResp.code()}")
                    }
                }

                // 3. Update invoice status
                val statusBody = mutableMapOf<String, Any?>()
                if (isPacked) {
                    statusBody["is_packed"] = true
                    statusBody["packed_at"] = java.time.Instant.now().toString()
                }
                if (isDispatched) {
                    statusBody["is_dispatched"] = true
                    statusBody["dispatched_at"] = java.time.Instant.now().toString()
                }
                if (statusBody.isNotEmpty()) {
                    api.updateInvoiceStatus(
                        invoiceId = "eq.$invoiceId",
                        body = statusBody,
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
            val body = mutableMapOf<String, Any?>()
            if (isPacked != null) {
                body["is_packed"] = isPacked
                if (isPacked) body["packed_at"] = java.time.Instant.now().toString()
            }
            if (isDispatched != null) {
                body["is_dispatched"] = isDispatched
                if (isDispatched) body["dispatched_at"] = java.time.Instant.now().toString()
            }
            if (body.isNotEmpty()) {
                val response = api.updateInvoiceStatus(
                    invoiceId = "eq.$invoiceId",
                    body = body,
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
