package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.PackingSessionDto
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchDto
import com.example.gudgum_prod_flow.data.remote.dto.SubmitPackingSessionRequest
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import com.example.gudgum_prod_flow.util.isDuplicateKeyConflict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PackingRepository @Inject constructor(
    private val pendingDao: PendingOperationEventDao,
) {
    private val api = SupabaseApiClient.api

    companion object {
        private const val TAG = "PackingRepository"
    }

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

    suspend fun submitPacking(
        batchCode: String,
        flavorId: String?,
        boxesPacked: Int,
        packingDate: String,
        workerId: String,
        isOnline: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                val request = SubmitPackingSessionRequest(
                    batchCode = batchCode,
                    flavorId = flavorId,
                    sessionDate = packingDate,
                    workerId = workerId,
                    boxesPacked = boxesPacked,
                )

                val existingSession = findExistingSession(batchCode, flavorId, packingDate)
                if (existingSession?.id != null) {
                    updateSession(existingSession.id, request)
                    return@runCatching
                }

                val response = api.insertPackingSession(request)
                val body = response.errorBody()?.string().orEmpty()
                if (!response.isSuccessful && response.code() != 201) {
                    if (isDuplicateKeyConflict(response.code(), body)) {
                        val duplicateSession = findExistingSession(batchCode, flavorId, packingDate)
                        if (duplicateSession?.id != null) {
                            updateSession(duplicateSession.id, request)
                            return@runCatching
                        }
                        error("This packing session is already saved for the selected batch and date.")
                    }

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
                            put("session_date", packingDate)
                        }.toString(),
                    )
                )
            }
        }
    }

    private suspend fun findExistingSession(
        batchCode: String,
        flavorId: String?,
        packingDate: String,
    ): PackingSessionDto? {
        val response = api.findPackingSession(
            batchCode = "eq.$batchCode",
            flavorId = flavorId?.let { "eq.$it" } ?: "is.null",
            sessionDate = "eq.$packingDate",
        )

        if (!response.isSuccessful) {
            Log.w(TAG, "Failed to lookup packing session: ${response.code()}")
            return null
        }

        return response.body().orEmpty().firstOrNull()
    }

    private suspend fun updateSession(sessionId: String, request: SubmitPackingSessionRequest) {
        val response = api.updatePackingSession(
            id = "eq.$sessionId",
            request = request,
        )
        val errorBody = response.errorBody()?.string().orEmpty()
        if (!response.isSuccessful) {
            Log.e(TAG, "Failed to update packing session $sessionId: ${response.code()} $errorBody")
            error("Unable to update the existing packing session right now. Please try again.")
        }
    }
}
