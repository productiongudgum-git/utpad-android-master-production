package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.DispatchEventFifoDto
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceDto
import com.example.gudgum_prod_flow.data.remote.dto.ReturnEventInsertRequest
import com.example.gudgum_prod_flow.data.remote.dto.SubmitPackingSessionRequest
import com.example.gudgum_prod_flow.data.remote.dto.UpdateInvoiceReturnStatusRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReturnsRepository @Inject constructor() {

    private val api = SupabaseApiClient.api

    companion object {
        private const val TAG = "ReturnsRepository"
    }

    /**
     * Search for a dispatched invoice by invoice_number.
     * Returns null if not found or not yet dispatched.
     */
    suspend fun searchInvoice(invoiceNumber: String): Result<InvoiceDto?> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = api.searchInvoiceByNumber(invoiceNumber = "eq.$invoiceNumber")
            if (!resp.isSuccessful) error("Search failed: HTTP ${resp.code()}")
            resp.body()?.firstOrNull()
        }
    }

    /**
     * Fetch all dispatch_events for an invoice (batch_code, flavor_id, boxes_dispatched).
     * Reuses the existing getDispatchEventsByInvoice endpoint.
     */
    suspend fun getDispatchEventsForInvoice(invoiceId: String): Result<List<DispatchEventFifoDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = api.getDispatchEventsByInvoice(invoiceId = "eq.$invoiceId")
                if (!resp.isSuccessful) error("Failed to load dispatch events: HTTP ${resp.code()}")
                resp.body() ?: emptyList()
            }
        }

    /**
     * Submit a return:
     * 1. Insert into returns_events (exact column mapping)
     * 2. Look up the production_batch to get its id (non-fatal)
     * 3. Insert packing_session to restock the returned boxes
     * 4. Re-query total returns for the invoice's batch_codes → update return_status
     */
    suspend fun submitReturn(
        invoice: InvoiceDto,
        batchCode: String,
        flavorId: String?,
        boxesToReturn: Int,
        reason: String,
        returnDate: String,
        allDispatchEvents: List<DispatchEventFifoDto>,
        workerId: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // ── 1. Insert returns_events ────────────────────────────────────────
            val returnResp = api.insertReturnsEvent(
                ReturnEventInsertRequest(
                    batchCode = batchCode,
                    skuId = flavorId,              // sku_id in table = flavor_id in app
                    qtyReturned = boxesToReturn,
                    reason = reason.ifBlank { null },
                    returnDate = returnDate,
                    workerId = workerId,
                    invoiceId = invoice.id,
                ),
            )
            val returnCode = returnResp.code()
            if (returnCode !in 200..204) {
                val errBody = returnResp.errorBody()?.string() ?: ""
                error("Failed to record return: HTTP $returnCode — $errBody")
            }
            Log.d(TAG, "insertReturnsEvent success [$returnCode] — $boxesToReturn boxes for $batchCode")

            // ── 2. Look up production_batch id for restocking (non-fatal) ───────
            val productionBatchId: String? = if (flavorId != null) {
                try {
                    val pbResp = api.findProductionBatch(
                        batchCode = "eq.$batchCode",
                        flavorId = "eq.$flavorId",
                    )
                    pbResp.body()?.firstOrNull()?.id.also { id ->
                        Log.d(TAG, "findProductionBatch for ($batchCode, $flavorId): id=$id")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "findProductionBatch non-fatal: ${e.message}")
                    null
                }
            } else null

            // ── 3. Insert packing_session to add boxes back to inventory ────────
            val psResp = api.insertPackingSession(
                SubmitPackingSessionRequest(
                    batchCode = batchCode,
                    flavorId = flavorId,
                    sessionDate = returnDate,
                    workerId = workerId,
                    boxesPacked = boxesToReturn,
                    productionBatchId = productionBatchId,
                    status = "complete",
                ),
            )
            val psCode = psResp.code()
            if (psCode !in 200..204) {
                Log.w(TAG, "insertPackingSession non-fatal [$psCode] — inventory restock may be incomplete")
            } else {
                Log.d(TAG, "insertPackingSession success [$psCode], productionBatchId=$productionBatchId")
            }

            // ── 4. Compute and update return_status ─────────────────────────────
            val totalDispatched = allDispatchEvents.sumOf { it.boxesDispatched }
            val batchCodes = allDispatchEvents.map { it.batchCode }.distinct()

            val returnStatus = if (batchCodes.isEmpty() || totalDispatched <= 0) {
                "partially_returned"
            } else {
                // Query returns_events (includes the row just inserted since it's committed)
                val totalReturned = try {
                    val batchIn = "in.(${batchCodes.joinToString(",")})"
                    val rResp = api.getReturnsByBatchCodes(batchCodeIn = batchIn)
                    (rResp.body() ?: emptyList()).sumOf { it.qtyReturned }
                } catch (e: Exception) {
                    Log.w(TAG, "getReturnsByBatchCodes non-fatal: ${e.message}")
                    // Fall back to counting just the current return
                    boxesToReturn
                }
                if (totalReturned >= totalDispatched) "completely_returned" else "partially_returned"
            }

            try {
                val srResp = api.updateInvoiceReturnStatus(
                    invoiceId = "eq.${invoice.id}",
                    body = UpdateInvoiceReturnStatusRequest(returnStatus),
                )
                Log.d(TAG, "updateInvoiceReturnStatus → $returnStatus [HTTP ${srResp.code()}]")
            } catch (e: Exception) {
                Log.w(TAG, "updateInvoiceReturnStatus non-fatal: ${e.message}")
            }
            Unit
        }
    }
}
