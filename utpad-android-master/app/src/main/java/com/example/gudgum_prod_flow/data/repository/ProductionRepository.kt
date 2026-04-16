package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.local.dao.CachedBatchDao
import com.example.gudgum_prod_flow.data.local.dao.CachedFlavorDao
import com.example.gudgum_prod_flow.data.local.dao.CachedRecipeLineDao
import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.CachedBatchEntity
import com.example.gudgum_prod_flow.data.local.entity.CachedFlavorEntity
import com.example.gudgum_prod_flow.data.local.entity.CachedRecipeLineEntity
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchDto
import com.example.gudgum_prod_flow.data.remote.dto.SubmitProductionBatchRequest
import com.example.gudgum_prod_flow.util.isDuplicateKeyConflict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductionRepository @Inject constructor(
    private val flavorDao: CachedFlavorDao,
    private val recipeLineDao: CachedRecipeLineDao,
    private val batchDao: CachedBatchDao,
    private val pendingDao: PendingOperationEventDao,
) {
    private val api = SupabaseApiClient.api

    companion object {
        private const val TAG = "ProductionRepository"
    }

    fun getActiveFlavors(): Flow<List<CachedFlavorEntity>> = flavorDao.getActiveFlavors()

    fun getRecipeLines(flavorId: String): Flow<List<CachedRecipeLineEntity>> =
        recipeLineDao.getByRecipeId(flavorId)

    fun getOpenBatches(): Flow<List<CachedBatchEntity>> = batchDao.getOpenBatches()

    suspend fun refreshFlavors(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getGgFlavors()
            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()
                flavorDao.deleteAll()
                flavorDao.insertAll(dtos.map {
                    CachedFlavorEntity(
                        id = it.id,
                        name = it.name,
                        code = it.code,
                        recipeId = it.recipeId,
                        active = it.active,
                        yieldThreshold = it.yieldThreshold,
                        shelfLifeDays = it.shelfLifeDays,
                    )
                })
            } else {
                error("Flavor refresh failed: ${response.code()}")
            }
        }
    }

    suspend fun refreshRecipeLines(flavorId: String): Result<Pair<String?, Double?>> = withContext(Dispatchers.IO) {
        runCatching {
            val recipeResponse = api.getGgRecipe(flavorId = "eq.$flavorId")
            if (!recipeResponse.isSuccessful) error("Recipe fetch failed: ${recipeResponse.code()}")

            val recipes = recipeResponse.body() ?: emptyList()
            val targetRecipe = recipes.firstOrNull()

            recipeLineDao.deleteByRecipeId(flavorId)

            if (targetRecipe != null) {
                val linesResponse = api.getRecipeLines(recipeId = "eq.${targetRecipe.id}")
                if (linesResponse.isSuccessful) {
                    val lines = linesResponse.body() ?: emptyList()
                    recipeLineDao.insertAll(lines.map { line ->
                        CachedRecipeLineEntity(
                            recipeId = flavorId,
                            ingredientId = line.ingredientId,
                            ingredientName = line.ingredient?.name ?: line.ingredientId,
                            plannedQty = line.qty,
                            unit = line.ingredient?.defaultUnit ?: "kg",
                        )
                    })
                }
            }

            Pair(targetRecipe?.id, targetRecipe?.yieldFactor)
        }
    }

    suspend fun refreshOpenBatches(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getOpenBatches()
            if (response.isSuccessful) {
                val dtos = response.body() ?: emptyList()
                batchDao.deleteAll()
                batchDao.upsertAll(dtos.map {
                    val resolvedSkuId = it.skuId ?: it.flavorId ?: it.batchCode
                    CachedBatchEntity(
                        batchCode = it.batchCode,
                        skuId = resolvedSkuId,
                        skuName = it.flavor?.name ?: resolvedSkuId,
                        skuCode = it.flavor?.code ?: "",
                        productionDate = it.productionDate,
                        status = it.status,
                        plannedYield = it.plannedYield,
                        totalPacked = it.actualYield?.toInt() ?: 0,
                    )
                })
            } else {
                error("Open batches refresh failed: ${response.code()}")
            }
        }
    }

    suspend fun countBatchesForCodeAndFlavor(batchCode: String, flavorId: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                Log.d(TAG, "countBatches: querying batch_code=eq.$batchCode flavor_id=eq.$flavorId")
                // Use a minimal select — deliberately excludes batch_number so the query
                // succeeds even before that column is fully backfilled on older rows.
                val response = api.getProductionBatchesByCodeAndFlavor(
                    batchCode = "eq.$batchCode",
                    flavorId = "eq.$flavorId",
                    select = "id,batch_code,flavor_id,production_date",
                    order = "production_date.asc",
                )
                val errorBody = if (!response.isSuccessful) response.errorBody()?.string().orEmpty() else ""
                Log.d(TAG, "countBatches: HTTP ${response.code()} rows=${response.body()?.size} error=${errorBody.ifBlank { "none" }}")
                if (response.isSuccessful) {
                    val count = response.body()?.size ?: 0
                    Log.d(TAG, "countBatches: existing=$count → next batch_number=${count + 1}")
                    count
                } else {
                    Log.e(TAG, "countBatches: query failed (${response.code()}) $errorBody")
                    0
                }
            }.onFailure { e ->
                Log.e(TAG, "countBatches: exception ${e.message}")
            }
        }

    suspend fun submitBatch(
        batchCode: String,
        skuId: String,
        skuCode: String,
        recipeId: String,
        productionDate: String,
        workerId: String,
        plannedYield: Double?,
        actualYield: Double?,
        isOnline: Boolean,
        batchSizeUnits: Int? = null,
        rawMaterialInput: Double? = null,
        expectedYield: Double? = null,
        expectedBoxes: Int? = null,
        expectedUnits: Int? = null,
        batchNumber: Int? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                val request = SubmitProductionBatchRequest(
                    batchCode = batchCode,
                    skuId = skuId,
                    flavorId = skuId,
                    recipeId = recipeId,
                    productionDate = productionDate,
                    workerId = workerId,
                    plannedYield = plannedYield,
                    actualYield = actualYield,
                    batchSizeUnits = batchSizeUnits,
                    rawMaterialInput = rawMaterialInput,
                    expectedYield = expectedYield,
                    expectedBoxes = expectedBoxes,
                    expectedUnits = expectedUnits,
                    batchNumber = batchNumber,
                )

                Log.d(TAG, buildString {
                    append("Submitting production batch:")
                    append(" batch_code=$batchCode")
                    append(" flavor_id=$skuId")
                    append(" recipe_id=$recipeId")
                    append(" production_date=$productionDate")
                    append(" worker_id=$workerId")
                    append(" planned_yield=$plannedYield")
                    append(" actual_yield=$actualYield")
                    append(" expected_boxes=$expectedBoxes")
                    append(" expected_units=$expectedUnits")
                    append(" batch_number=$batchNumber")
                })

                val batchResp = api.insertProductionBatch(request)
                val batchError = batchResp.errorBody()?.string().orEmpty()
                Log.d(TAG, "Production save HTTP ${batchResp.code()} — ${if (batchError.isBlank()) "no error body" else batchError}")

                if (!batchResp.isSuccessful) {
                    val msg = when {
                        batchError.isNotBlank() -> batchError
                        else -> "HTTP ${batchResp.code()}"
                    }
                    error("Production batch save failed: $msg")
                }
            }
        } else {
            runCatching {
                val payload = JSONObject().apply {
                    put("sku_id", skuId)
                    put("sku_code", skuCode)
                    put("recipe_id", recipeId)
                    put("production_date", productionDate)
                    put("planned_yield", plannedYield ?: JSONObject.NULL)
                    put("actual_yield", actualYield ?: JSONObject.NULL)
                    put("batch_size_units", batchSizeUnits ?: JSONObject.NULL)
                    put("raw_material_input", rawMaterialInput ?: JSONObject.NULL)
                    put("expected_yield", expectedYield ?: JSONObject.NULL)
                    put("expected_boxes", expectedBoxes ?: JSONObject.NULL)
                    put("expected_units", expectedUnits ?: JSONObject.NULL)
                    put("batch_number", batchNumber ?: JSONObject.NULL)
                }
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "production",
                        workerId = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = batchCode,
                        quantity = plannedYield ?: 0.0,
                        unit = "kg",
                        summary = "Production batch $batchCode queued",
                        payloadJson = payload.toString(),
                    )
                )
            }
        }
    }

}

private val WorkerIdentityStore get() = com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
