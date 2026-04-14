package com.example.gudgum_prod_flow.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.gudgum_prod_flow.BuildConfig
import com.example.gudgum_prod_flow.data.local.dao.CachedIngredientDao
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.CachedIngredientEntity
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.OperationsApiClient
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.GgInwardingRequest
import com.example.gudgum_prod_flow.data.remote.dto.GgIngredientInsertRequest
import com.example.gudgum_prod_flow.data.remote.dto.GgVendorDto
import com.example.gudgum_prod_flow.data.remote.dto.GgVendorInsertRequest
import com.example.gudgum_prod_flow.data.remote.dto.RawMaterialStockInsertRequest
import com.example.gudgum_prod_flow.data.remote.dto.RawMaterialStockUpdateRequest
import com.example.gudgum_prod_flow.data.remote.dto.SubmitOperationEventRequest
import com.example.gudgum_prod_flow.data.remote.dto.SubmitReturnEventRequest
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InwardingRepository @Inject constructor(
    private val ingredientDao: CachedIngredientDao,
    private val pendingDao: PendingOperationEventDao,
) {
    private companion object {
        const val TAG = "InwardingRepository"
    }

    private val api = SupabaseApiClient.api
    private val operationsApi = OperationsApiClient.operationsApi

    fun getActiveIngredients(): Flow<List<CachedIngredientEntity>> =
        ingredientDao.getActiveIngredients()

    suspend fun refreshIngredients(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getGgIngredients()
            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()
                ingredientDao.deleteAll()
                ingredientDao.insertAll(dtos.map {
                    CachedIngredientEntity(
                        id = it.id,
                        name = it.name,
                        unit = it.defaultUnit,
                        active = true,
                        defaultSupplierName = null,
                    )
                })
            } else {
                error("Ingredient refresh failed: ${response.code()}")
            }
        }
    }

    suspend fun submitInwardEvent(
        request: GgInwardingRequest,
        isOnline: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                val response = api.insertGgInwarding(request)
                if (!response.isSuccessful && response.code() != 201) {
                    val body = response.errorBody()?.string() ?: ""
                    error("Inward event insert failed: ${response.code()} | $body")
                }

                // Update inventory_raw_materials: increment if exists, insert if not
                runCatching {
                    val stockResponse = api.getRawMaterialStock("eq.${request.ingredientId}")
                    val existing = stockResponse.body()?.firstOrNull()
                    if (existing != null) {
                        api.updateRawMaterialStock(
                            ingredientId = "eq.${request.ingredientId}",
                            body = RawMaterialStockUpdateRequest(currentQty = existing.currentQty + request.qty),
                        )
                    } else {
                        api.insertRawMaterialStock(
                            body = RawMaterialStockInsertRequest(
                                ingredientId = request.ingredientId,
                                currentQty = request.qty,
                                unit = request.unit,
                            ),
                        )
                    }
                }.onFailure { error ->
                    Log.w(TAG, "inventory_raw_materials update failed after inward save", error)
                }

                runCatching {
                    val opsResponse = operationsApi.submitOperationEvent(
                        SubmitOperationEventRequest(
                            module = "inwarding",
                            workerId = request.workerId,
                            workerName = WorkerIdentityStore.workerName,
                            workerRole = WorkerIdentityStore.workerRole,
                            batchCode = request.lotRef ?: "N/A",
                            quantity = request.qty,
                            unit = request.unit,
                            summary = "Inwarded ingredient ${request.ingredientId}",
                            payload = mutableMapOf<String, String>().apply {
                                put("ingredient_id", request.ingredientId)
                                put("vendor_id", request.vendorId)
                                put("inward_date", request.inwardDate)
                                request.expiryDate?.let { put("expiry_date", it) }
                                request.lotRef?.let { put("lot_ref", it) }
                            },
                        )
                    )
                    if (!opsResponse.isSuccessful) {
                        Log.w(TAG, "Ops event publish failed after inward save: ${opsResponse.code()}")
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Ops event publish threw after inward save", error)
                }
                Unit
            }
        } else {
            runCatching {
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "inwarding",
                        workerId = request.workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = "N/A",
                        quantity = request.qty,
                        unit = request.unit,
                        summary = "Inward event queued for ingredient ${request.ingredientId}",
                        payloadJson = JSONObject().apply {
                            put("ingredient_id", request.ingredientId)
                            put("qty", request.qty)
                            put("unit", request.unit)
                            put("inward_date", request.inwardDate)
                            put("expiry_date", request.expiryDate ?: JSONObject.NULL)
                            put("vendor_id", request.vendorId)
                            put("lot_ref", request.lotRef ?: JSONObject.NULL)
                        }.toString(),
                    )
                )
            }
        }
    }

    suspend fun submitReturnEvent(
        request: SubmitReturnEventRequest,
        isOnline: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                val response = api.insertReturnEvent(request)
                if (!response.isSuccessful && response.code() != 201) {
                    error("Return event insert failed: ${response.code()}")
                }
            }
        } else {
            runCatching {
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "returns",
                        workerId = request.workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = request.batchCode,
                        quantity = request.qtyReturned.toDouble(),
                        unit = "boxes",
                        summary = "Return queued for batch ${request.batchCode}",
                        payloadJson = JSONObject().apply {
                            put("batch_code", request.batchCode)
                            put("sku_id", request.skuId)
                            put("qty_returned", request.qtyReturned)
                            put("reason", request.reason ?: JSONObject.NULL)
                            put("return_date", request.returnDate)
                        }.toString(),
                    )
                )
            }
        }
    }

    /**
     * Uploads a bill photo to Supabase Storage bucket "bill-photos"
     * and returns the public URL on success.
     */
    suspend fun uploadBillPhoto(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not read photo from URI")

            val filename = "inward_bill_${System.currentTimeMillis()}.jpg"
            val uploadUrl = "${BuildConfig.SUPABASE_API_URL}/storage/v1/object/bill-photos/$filename"

            val requestBody = bytes.toRequestBody("image/jpeg".toMediaType())
            val request = Request.Builder()
                .url(uploadUrl)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                .post(requestBody)
                .build()

            val response = SupabaseApiClient.httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                error("Photo upload failed: ${response.code} $body")
            }

            "${BuildConfig.SUPABASE_API_URL}/storage/v1/object/public/bill-photos/$filename"
        }
    }

    suspend fun getVendors(): List<GgVendorDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.getGgVendors()
            if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun createVendor(name: String, contactPhone: String? = null): Result<GgVendorDto> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.insertGgVendor(GgVendorInsertRequest(name = name, phone = contactPhone))
            if (!response.isSuccessful) error("Failed to create vendor: ${response.code()}")
            response.body()?.firstOrNull() ?: GgVendorDto(id = "vnd-${System.currentTimeMillis()}", name = name, phone = contactPhone)
        }
    }

    suspend fun createIngredient(
        name: String,
        unit: String,
        vendorId: String? = null,
    ): Result<CachedIngredientEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.insertGgIngredient(
                GgIngredientInsertRequest(
                    name = name,
                    defaultUnit = unit,
                )
            )
            if (!response.isSuccessful) error("Failed to create ingredient: ${response.code()}")
            val dto = response.body()?.firstOrNull()
                ?: throw IllegalStateException("No ingredient returned from API")
            val entity = CachedIngredientEntity(
                id = dto.id,
                name = dto.name,
                unit = dto.defaultUnit,
                active = true,
                defaultSupplierName = null,
            )
            ingredientDao.insertAll(listOf(entity))
            entity
        }
    }
}
