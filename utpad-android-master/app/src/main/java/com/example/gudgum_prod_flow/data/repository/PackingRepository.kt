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
     */
    private suspend fun getAllBatchesWithPackingStatus(): Result<List<ProductionBatchWithPackingDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getProductionBatchesWithPackingStatus()
                if (response.isSuccessful) {
                    val batches = response.body() ?: emptyList()
                    // Assign fallback batch_number for rows where it is null
                    batches.mapIndexed { index, batch ->
                        if (batch.batchNumber == null) batch.copy(batchNumber = index + 1) else batch
                    }
                } else {
                    Log.e(TAG, "getAllBatchesWithPackingStatus: HTTP ${response.code()} ${response.errorBody()?.string()}")
                    emptyList()
                }
            }
        }

    /**
     * For "Packing Complete" step 2: all open batches that do NOT have a
     * packing session with status='complete' yet.
     */
    suspend fun getProductionBatchesForCompletePacking(): Result<List<ProductionBatchWithPackingDto>> =
        getAllBatchesWithPackingStatus().map { batches ->
            batches.filter { !it.hasCompletePacking }
        }

    /**
     * For "Yet to Finish Packing" step 2:
     * Returns Pair(partialBatches, unpackedBatches).
     * - partialBatches: batches that have at least one packing session (but none with status='complete')
     * - unpackedBatches: batches with no packing sessions at all
     */
    suspend fun getProductionBatchesForPartialPacking(): Result<Pair<List<ProductionBatchWithPackingDto>, List<ProductionBatchWithPackingDto>>> =
        getAllBatchesWithPackingStatus().map { batches ->
            val incomplete = batches.filter { !it.hasCompletePacking }
            val partial = incomplete.filter { it.packingSessions.isNotEmpty() }
            val unpacked = incomplete.filter { it.packingSessions.isEmpty() }
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
