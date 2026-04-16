package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.local.dao.CachedFlavorDao
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.CachedFlavorEntity
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchWithPackingDto
import com.example.gudgum_prod_flow.data.remote.dto.SubmitPackingSessionRequest
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackingRepository @Inject constructor(
    private val pendingDao: PendingOperationEventDao,
    private val flavorDao: CachedFlavorDao,
) {
    private val api = SupabaseApiClient.api

    companion object {
        private const val TAG = "PackingRepository"
    }

    fun getActiveFlavors(): Flow<List<CachedFlavorEntity>> = flavorDao.getActiveFlavors()

    /**
     * Returns all open production batches joined with their packing sessions.
     * Used to derive which batches still need packing.
     *
     * NOTE: Requires a FK from packing_sessions.production_batch_id → production_batches.id
     * in Supabase for the embedded select to work.
     */
    private suspend fun getAllBatchesWithPackingStatus(): Result<List<ProductionBatchWithPackingDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getProductionBatchesWithPackingStatus()
                if (response.isSuccessful) {
                    val batches = response.body() ?: emptyList()
                    Log.d(TAG, "getAllBatchesWithPackingStatus: ${batches.size} open production batches")
                    batches.forEach { batch ->
                        val sessionSummary = if (batch.packingSessions.isEmpty()) {
                            "no sessions"
                        } else {
                            batch.packingSessions.joinToString { "status=${it.status ?: "NULL"}" }
                        }
                        Log.d(TAG, "  batch=${batch.batchCode} #${batch.batchNumber} sessions=[${sessionSummary}]")
                    }
                    // Assign fallback batch_number for rows where it is null
                    batches.mapIndexed { index, batch ->
                        if (batch.batchNumber == null) batch.copy(batchNumber = index + 1) else batch
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "getAllBatchesWithPackingStatus: HTTP ${response.code()} — $errorBody")
                    Log.e(TAG, "  ^^^ If 400: check that (a) packing_sessions has a FK to production_batches and (b) the 'status' column exists in packing_sessions")
                    emptyList()
                }
            }
        }

    /**
     * For PATH A ("Packing Complete") step 2.
     * Returns ALL open batches that have no complete packing session yet —
     * this intentionally includes BOTH truly-unpacked batches AND batches that
     * already have partial/null sessions. Both are shown together in a single
     * flat list so the worker can mark any of them as fully complete.
     */
    suspend fun getProductionBatchesForCompletePacking(): Result<List<ProductionBatchWithPackingDto>> =
        getAllBatchesWithPackingStatus().map { batches ->
            val result = batches.filter { !it.hasCompletePacking }
            Log.d(TAG, "getProductionBatchesForCompletePacking: ${result.size} batches (of ${batches.size}) have no complete session")
            result
        }

    /**
     * For "Yet to Finish Packing" step 2.
     * Returns Pair(partialBatches, unpackedBatches).
     *
     * - partialBatches : batches whose sessions are all partial/NULL (no complete session).
     *                    NULL status is treated the same as 'partial' (legacy rows).
     * - unpackedBatches: batches with NO packing sessions at all.
     *
     * Batches that have ANY session with status='complete' are excluded from both lists.
     */
    suspend fun getProductionBatchesForPartialPacking(): Result<Pair<List<ProductionBatchWithPackingDto>, List<ProductionBatchWithPackingDto>>> =
        getAllBatchesWithPackingStatus().map { batches ->
            // Exclude batches that already have a confirmed-complete packing session
            val notFullyPacked = batches.filter { !it.hasCompletePacking }

            // Left column: has at least one session marked status='partial' or status=NULL
            val partial = notFullyPacked.filter { batch ->
                batch.packingSessions.any { session -> session.status == "partial" || session.status == null }
            }

            // Right column: no packing sessions at all
            val unpacked = notFullyPacked.filter { it.packingSessions.isEmpty() }

            Log.d(TAG, "getProductionBatchesForPartialPacking: partial=${partial.size}, unpacked=${unpacked.size} (notFullyPacked=${notFullyPacked.size}, total=${batches.size})")
            partial.forEach { b ->
                Log.d(TAG, "  [partial-left] batch=${b.batchCode} #${b.batchNumber}")
                b.packingSessions.forEachIndexed { i, s ->
                    Log.d(TAG, "    session[$i] id=${s.id} status=${s.status ?: "NULL"} production_batch_id=${s.productionBatchId}")
                }
            }
            unpacked.forEach { b ->
                Log.d(TAG, "  [unpacked-right] batch=${b.batchCode} #${b.batchNumber} (0 sessions)")
            }

            Pair(partial, unpacked)
        }

    suspend fun submitPacking(
        batchCode: String,
        flavorId: String?,
        boxesPacked: Int,
        unitsPacked: Int?,
        packingDate: String,
        workerId: String,
        isOnline: Boolean,
        productionBatchId: String? = null,
        status: String = "partial",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                val request = SubmitPackingSessionRequest(
                    batchCode = batchCode,
                    flavorId = flavorId,
                    sessionDate = packingDate,
                    workerId = workerId,
                    boxesPacked = boxesPacked,
                    unitsPacked = unitsPacked,
                    productionBatchId = productionBatchId,
                    status = status,
                )

                val response = api.insertPackingSession(request)
                val body = response.errorBody()?.string().orEmpty()
                if (!response.isSuccessful && response.code() != 201) {
                    Log.e(TAG, "Packing save failed: ${response.code()} $body")
                    error("Unable to save the packing session right now. Please try again.")
                }
            }
        } else {
            runCatching {
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "packing",
                        workerId = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = batchCode,
                        quantity = boxesPacked.toDouble(),
                        unit = "boxes",
                        summary = "Packed $boxesPacked boxes for batch $batchCode ($status)",
                        payloadJson = JSONObject().apply {
                            put("batch_code", batchCode)
                            put("flavor_id", flavorId ?: JSONObject.NULL)
                            put("boxes_packed", boxesPacked)
                            put("units_packed", unitsPacked ?: JSONObject.NULL)
                            put("session_date", packingDate)
                            put("production_batch_id", productionBatchId ?: JSONObject.NULL)
                            put("status", status)
                        }.toString(),
                    )
                )
            }
        }
    }
}
