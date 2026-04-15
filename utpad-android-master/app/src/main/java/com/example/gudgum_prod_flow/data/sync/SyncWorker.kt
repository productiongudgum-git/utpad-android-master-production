package com.example.gudgum_prod_flow.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.remote.api.OperationsApiClient
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.*
import com.example.gudgum_prod_flow.util.isDuplicateKeyConflict
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val pendingDao: PendingOperationEventDao,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val pendingEvents = pendingDao.getAllPendingEvents()
            if (pendingEvents.isEmpty()) return@withContext Result.success()

            var allSuccessful = true

            for (event in pendingEvents) {
                val success = when (event.module) {
                    "production" -> syncProductionBatch(event.payloadJson, event.batchCode, event.workerId)
                    "packing"    -> syncPackingSession(event.payloadJson, event.batchCode, event.workerId)
                    "dispatch"   -> syncDispatchEvents(event.payloadJson, event.workerId)
                    "inwarding"  -> syncInwardEvent(event.payloadJson, event.workerId)
                    "returns"    -> syncReturnEvent(event.payloadJson, event.workerId)
                    else         -> syncLegacyOpsEvent(event)
                }

                if (success) {
                    pendingDao.deleteEventById(event.id)
                } else {
                    allSuccessful = false
                    if (event.syncAttemptCount >= 3) {
                        pendingDao.updateEvent(event.copy(
                            syncAttemptCount = event.syncAttemptCount + 1,
                            lastSyncError = "Max retries exceeded",
                        ))
                    } else {
                        pendingDao.updateEvent(event.copy(
                            syncAttemptCount = event.syncAttemptCount + 1,
                        ))
                    }
                }
            }

            if (allSuccessful) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun syncProductionBatch(payloadJson: String, batchCode: String, workerId: String): Boolean {
        return try {
            val payload = JSONObject(payloadJson)
            val skuId = payload.getString("sku_id")
            val request = SubmitProductionBatchRequest(
                batchCode = batchCode,
                skuId = skuId,
                flavorId = skuId,
                recipeId = payload.getString("recipe_id"),
                productionDate = payload.getString("production_date"),
                workerId = workerId,
                plannedYield = if (payload.isNull("planned_yield")) null else payload.optDouble("planned_yield"),
                actualYield = if (payload.isNull("actual_yield")) null else payload.optDouble("actual_yield"),
            )

            val existing = SupabaseApiClient.api.findProductionBatch(
                batchCode = "eq.$batchCode",
                flavorId = "eq.$skuId",
            ).body().orEmpty().firstOrNull()

            if (existing?.id != null) {
                val updateResp = SupabaseApiClient.api.updateProductionBatch(
                    id = "eq.${existing.id}",
                    request = request,
                )
                return updateResp.isSuccessful
            }

            val batchResp = SupabaseApiClient.api.insertProductionBatch(request)
            val errorBody = batchResp.errorBody()?.string().orEmpty()
            if (batchResp.isSuccessful || batchResp.code() == 201) {
                true
            } else if (isDuplicateKeyConflict(batchResp.code(), errorBody)) {
                true
            } else {
                false
            }
        } catch (e: Exception) { false }
    }

    private suspend fun syncPackingSession(payloadJson: String, batchCode: String, workerId: String): Boolean {
        return try {
            val payload = JSONObject(payloadJson)
            val flavorId = if (payload.isNull("flavor_id")) null else payload.getString("flavor_id")
            val sessionDate = payload.getString("session_date")
            val kgsPacked = if (payload.isNull("kgs_packed")) null else payload.optDouble("kgs_packed")
            val unitsPacked = if (payload.isNull("units_packed")) null else payload.optInt("units_packed")
            val productionBatchId = if (payload.isNull("production_batch_id")) null else payload.optString("production_batch_id")
            val request = SubmitPackingSessionRequest(
                batchCode = batchCode,
                flavorId = flavorId,
                sessionDate = sessionDate,
                workerId = workerId,
                boxesPacked = payload.getInt("boxes_packed"),
                kgsPacked = kgsPacked,
                unitsPacked = unitsPacked,
                productionBatchId = productionBatchId,
            )

            val existing = SupabaseApiClient.api.findPackingSession(
                batchCode = "eq.$batchCode",
                flavorId = flavorId?.let { "eq.$it" } ?: "is.null",
                sessionDate = "eq.$sessionDate",
            ).body().orEmpty().firstOrNull()

            if (existing?.id != null) {
                val updateResp = SupabaseApiClient.api.updatePackingSession(
                    id = "eq.${existing.id}",
                    request = request,
                )
                return updateResp.isSuccessful
            }

            val resp = SupabaseApiClient.api.insertPackingSession(request)
            val errorBody = resp.errorBody()?.string().orEmpty()
            if (resp.isSuccessful || resp.code() == 201) {
                true
            } else if (isDuplicateKeyConflict(resp.code(), errorBody)) {
                true
            } else {
                false
            }
        } catch (e: Exception) { false }
    }

    private suspend fun syncDispatchEvents(payloadJson: String, workerId: String): Boolean {
        return try {
            val payload = JSONObject(payloadJson)
            val resp = SupabaseApiClient.api.insertDispatchEvent(
                SubmitDispatchEventRequest(
                    batchCode = payload.getString("batch_code"),
                    skuId = payload.getString("sku_id"),
                    boxesDispatched = payload.getInt("quantity_dispatched"),
                    customerName = if (payload.isNull("customer_name")) null else payload.getString("customer_name"),
                    invoiceNumber = payload.optString("invoice_number", ""),
                    dispatchDate = payload.getString("dispatch_date"),
                    workerId = workerId,
                )
            )
            resp.isSuccessful || resp.code() == 201
        } catch (e: Exception) { false }
    }

    private suspend fun syncInwardEvent(payloadJson: String, workerId: String): Boolean {
        return try {
            val payload = JSONObject(payloadJson)
            val resp = SupabaseApiClient.api.insertGgInwarding(
                GgInwardingRequest(
                    ingredientId = payload.getString("ingredient_id"),
                    qty = payload.getDouble("qty"),
                    unit = payload.getString("unit"),
                    vendorId = payload.getString("vendor_id"),
                    inwardDate = payload.getString("inward_date"),
                    expiryDate = if (payload.isNull("expiry_date")) null else payload.getString("expiry_date"),
                    lotRef = if (payload.isNull("lot_ref")) null else payload.getString("lot_ref"),
                    workerId = workerId,
                )
            )
            resp.isSuccessful || resp.code() == 201
        } catch (e: Exception) { false }
    }

    private suspend fun syncReturnEvent(payloadJson: String, workerId: String): Boolean {
        return try {
            val payload = JSONObject(payloadJson)
            val resp = SupabaseApiClient.api.insertReturnEvent(
                SubmitReturnEventRequest(
                    batchCode = payload.getString("batch_code"),
                    skuId = payload.getString("sku_id"),
                    qtyReturned = payload.getInt("qty_returned"),
                    reason = if (payload.isNull("reason")) null else payload.getString("reason"),
                    returnDate = payload.getString("return_date"),
                    workerId = workerId,
                )
            )
            resp.isSuccessful || resp.code() == 201
        } catch (e: Exception) { false }
    }

    private suspend fun syncLegacyOpsEvent(event: com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity): Boolean {
        return try {
            val payloadMap = try {
                val jsonObject = JSONObject(event.payloadJson)
                val map = mutableMapOf<String, String>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = jsonObject.get(key).toString()
                }
                map
            } catch (e: Exception) { emptyMap() }

            val resp = OperationsApiClient.operationsApi.submitOperationEvent(
                SubmitOperationEventRequest(
                    module = event.module,
                    workerId = event.workerId,
                    workerName = event.workerName,
                    workerRole = event.workerRole,
                    batchCode = event.batchCode,
                    quantity = event.quantity,
                    unit = event.unit,
                    summary = event.summary,
                    payload = payloadMap,
                )
            )
            resp.isSuccessful
        } catch (e: Exception) { false }
    }
}
