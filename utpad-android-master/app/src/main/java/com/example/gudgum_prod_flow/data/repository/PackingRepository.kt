package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.local.dao.CachedFlavorDao
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.CachedFlavorEntity
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchDto
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

    suspend fun getOpenBatches(): Result<List<ProductionBatchDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getOpenBatches()
            if (response.isSuccessful) {
                response.body() ?: emptyList()
            } else {
                emptyList()
            }
        }
    }

    suspend fun getOpenBatchCodes(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getOpenBatches()
            if (response.isSuccessful) {
                response.body()?.map { it.batchCode }?.distinct() ?: emptyList()
            } else {
                emptyList()
            }
        }
    }

    suspend fun getProductionBatches(batchCode: String, flavorId: String): Result<List<ProductionBatchDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                Log.d(TAG, "getProductionBatches: querying batch_code=eq.$batchCode flavor_id=eq.$flavorId")
                val response = api.getProductionBatchesByCodeAndFlavor(
                    batchCode = "eq.$batchCode",
                    flavorId = "eq.$flavorId",
                )
                val errorBody = if (!response.isSuccessful) response.errorBody()?.string().orEmpty() else ""
                Log.d(TAG, "getProductionBatches: HTTP ${response.code()} rows=${response.body()?.size} error=${errorBody.ifBlank { "none" }}")
                if (response.isSuccessful) {
                    val batches = response.body() ?: emptyList()
                    batches.forEach { b ->
                        Log.d(TAG, "  batch id=${b.id} batch_number=${b.batchNumber} expected_boxes=${b.expectedBoxes} date=${b.productionDate}")
                    }
                    batches
                } else {
                    Log.e(TAG, "getProductionBatches: failed (${response.code()}) $errorBody")
                    emptyList()
                }
            }.onFailure { e ->
                Log.e(TAG, "getProductionBatches: exception ${e.message}")
            }.map { it }
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
                        summary = "Packed $boxesPacked boxes for batch $batchCode",
                        payloadJson = JSONObject().apply {
                            put("batch_code", batchCode)
                            put("flavor_id", flavorId ?: JSONObject.NULL)
                            put("boxes_packed", boxesPacked)
                            put("units_packed", unitsPacked ?: JSONObject.NULL)
                            put("session_date", packingDate)
                            put("production_batch_id", productionBatchId ?: JSONObject.NULL)
                        }.toString(),
                    )
                )
            }
        }
    }

}
