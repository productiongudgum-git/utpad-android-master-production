package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.local.dao.CachedFlavorDao
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.BatchLookupDto
import com.example.gudgum_prod_flow.data.remote.dto.SubmitPackingSessionRequest
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repo for the new packing-side inventory features:
 *
 *  - `lookupBatch()` / `getBoxesPackedForBatch()` — back the "Update Inventory" flow.
 *  - `submitTopUpBoxes()` — writes one fresh `packing_sessions` row (always insert, never
 *    overwrite). The ziplock auto-deduction trigger runs on the new row.
 *  - `getFinishedGoodsInventory()` — per-flavour net stock for the View Inventory screen.
 *
 * Separate from PackingRepository so it doesn't share the existing
 * "session per batch per day" semantics — top-ups always create new rows.
 */
@Singleton
class InventoryRepository @Inject constructor(
    private val pendingDao: PendingOperationEventDao,
    private val flavorDao: CachedFlavorDao,
) {
    private val api = SupabaseApiClient.api

    companion object {
        private const val TAG = "InventoryRepository"
        /** Distinct module string for SyncWorker — bypasses the packing find-then-update path. */
        const val MODULE_PACKING_TOPUP = "packing_topup"
    }

    /** Resolve worker-typed batch_code + batch_number to every matching (production_batch, flavour).
     *  Returns 0..N rows — one per flavour produced under the same batch_code/batch_number. */
    suspend fun lookupBatch(batchCode: String, batchNumber: Int): Result<List<BatchLookupDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = api.findProductionBatchByCodeNumber(
                    batchCode = "eq.${batchCode.trim()}",
                    batchNumber = "eq.$batchNumber",
                )
                if (!resp.isSuccessful) {
                    Log.w(TAG, "lookupBatch failed: HTTP ${resp.code()}")
                    return@runCatching emptyList()
                }
                resp.body().orEmpty()
            }
        }

    /** Total boxes already packed for this production_batch (sum across all packing_sessions rows). */
    suspend fun getBoxesPackedForBatch(productionBatchId: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = api.getPackingBoxesForBatch(productionBatchId = "eq.$productionBatchId")
                if (!resp.isSuccessful) return@runCatching 0
                (resp.body() ?: emptyList()).sumOf { it.boxesPacked ?: 0 }
            }
        }

    /**
     * Add N boxes to this batch as a fresh packing_sessions row.
     * Status 'topup' so finance/dashboards can split top-ups from normal sessions if needed.
     */
    suspend fun submitTopUpBoxes(
        batchCode: String,
        flavorId: String,
        productionBatchId: String,
        boxes: Int,
        isOnline: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val today    = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val workerId = WorkerIdentityStore.workerId

        if (isOnline) {
            runCatching {
                val request = SubmitPackingSessionRequest(
                    batchCode         = batchCode,
                    flavorId          = flavorId,
                    sessionDate       = today,
                    workerId          = workerId,
                    boxesPacked       = boxes,
                    unitsPacked       = boxes * 15,
                    productionBatchId = productionBatchId,
                    status            = "topup",
                )
                val resp = api.insertPackingSession(request)
                if (!resp.isSuccessful && resp.code() != 201) {
                    val err = resp.errorBody()?.string().orEmpty()
                    Log.e(TAG, "Top-up save failed: ${resp.code()} $err")
                    // Surface the ziplock trigger's RAISE message verbatim when present.
                    val msg = err.takeIf { it.contains("Ziplock", ignoreCase = true) }
                        ?: "Unable to update inventory right now. Please try again."
                    error(msg)
                }
            }
        } else {
            runCatching {
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module     = MODULE_PACKING_TOPUP,
                        workerId   = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode  = batchCode,
                        quantity   = boxes.toDouble(),
                        unit       = "boxes",
                        summary    = "Top-up: +$boxes boxes to batch $batchCode",
                        payloadJson = JSONObject().apply {
                            put("batch_code", batchCode)
                            put("flavor_id", flavorId)
                            put("boxes_packed", boxes)
                            put("units_packed", boxes * 15)
                            put("session_date", today)
                            put("production_batch_id", productionBatchId)
                            put("status", "topup")
                        }.toString(),
                    )
                )
            }
        }
    }

    /** Per-flavour finished-goods inventory: packed − dispatched, sorted by flavour name.
     *  Falls back to fetching flavours directly from Supabase when the local Room cache
     *  is empty (e.g. on a fresh install where the worker hasn't opened Production yet). */
    suspend fun getFinishedGoodsInventory(): Result<List<FinishedGoodsRow>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val packingResp  = api.getAllPackingSessionBoxes()
                val dispatchResp = api.getAllDispatchEventBoxes()
                if (!packingResp.isSuccessful) error("Unable to load packed totals (${packingResp.code()}).")
                if (!dispatchResp.isSuccessful) error("Unable to load dispatch totals (${dispatchResp.code()}).")

                val packedByFlavor = packingResp.body().orEmpty()
                    .groupBy { it.flavorId }
                    .mapValues { e -> e.value.sumOf { it.boxesPacked ?: 0 } }

                val dispatchedByFlavor = dispatchResp.body().orEmpty()
                    .groupBy { it.flavorId }
                    .mapValues { e -> e.value.sumOf { it.boxesDispatched ?: 0 } }

                // Try local cache first; fall back to a direct Supabase fetch on empty.
                val cached = flavorDao.getActiveFlavors().first()
                val flavorPairs: List<Pair<String, String>> = if (cached.isNotEmpty()) {
                    cached.map { it.id to it.name }
                } else {
                    val resp = api.getGgFlavors()
                    if (!resp.isSuccessful) error("Unable to load flavours (${resp.code()}).")
                    (resp.body() ?: emptyList()).map { it.id to it.name }
                }

                flavorPairs.map { (id, name) ->
                    val packed     = packedByFlavor[id] ?: 0
                    val dispatched = dispatchedByFlavor[id] ?: 0
                    FinishedGoodsRow(
                        flavorId       = id,
                        flavorName     = name,
                        boxesPacked    = packed,
                        boxesDispatched = dispatched,
                        boxesAvailable = packed - dispatched,
                    )
                }.sortedBy { it.flavorName }
            }
        }
}

/** Display row for the finished-goods inventory screen. */
data class FinishedGoodsRow(
    val flavorId: String,
    val flavorName: String,
    val boxesPacked: Int,
    val boxesDispatched: Int,
    val boxesAvailable: Int,
)
