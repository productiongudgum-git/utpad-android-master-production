package com.example.gudgum_prod_flow.data.repository

import com.example.gudgum_prod_flow.data.local.dao.PendingOperationEventDao
import com.example.gudgum_prod_flow.data.local.entity.PendingOperationEventEntity
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.remote.dto.DispatchedBatchDto
import com.example.gudgum_prod_flow.data.remote.dto.GgCustomerDto
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

    /** Fetch open production batches for batch selection */
    suspend fun getOpenBatches(): Result<List<ProductionBatchDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getOpenBatches()
            if (response.isSuccessful) response.body() ?: emptyList()
            else emptyList()
        }
    }

    /** Get distinct batch codes from open production batches */
    suspend fun getOpenBatchCodes(): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getOpenBatches()
            if (response.isSuccessful) {
                response.body()?.map { it.batchCode }?.distinct() ?: emptyList()
            } else emptyList()
        }
    }

    suspend fun submitDispatch(
        batchCode: String,
        skuId: String,
        customerName: String,
        invoiceNumber: String,
        quantityDispatched: Int,
        dispatchDate: String,
        workerId: String,
        isOnline: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (isOnline) {
            runCatching {
                val response = api.insertDispatchEvent(
                    SubmitDispatchEventRequest(
                        batchCode = batchCode,
                        skuId = skuId,
                        boxesDispatched = quantityDispatched,
                        customerName = customerName.ifBlank { null },
                        invoiceNumber = invoiceNumber,
                        dispatchDate = dispatchDate,
                        workerId = workerId,
                    )
                )
                if (!response.isSuccessful && response.code() != 201) {
                    val body = response.errorBody()?.string() ?: ""
                    error("Dispatch insert failed: ${response.code()} | $body")
                }
            }
        } else {
            runCatching {
                pendingDao.insertEvent(
                    PendingOperationEventEntity(
                        module = "dispatch",
                        workerId = workerId,
                        workerName = WorkerIdentityStore.workerName,
                        workerRole = WorkerIdentityStore.workerRole,
                        batchCode = batchCode,
                        quantity = quantityDispatched.toDouble(),
                        unit = "boxes",
                        summary = "Dispatch queued — $quantityDispatched boxes for batch $batchCode",
                        payloadJson = JSONObject().apply {
                            put("batch_code", batchCode)
                            put("sku_id", skuId)
                            put("customer_name", customerName)
                            put("invoice_number", invoiceNumber)
                            put("quantity_dispatched", quantityDispatched)
                            put("dispatch_date", dispatchDate)
                        }.toString(),
                    )
                )
            }
        }
    }
}
