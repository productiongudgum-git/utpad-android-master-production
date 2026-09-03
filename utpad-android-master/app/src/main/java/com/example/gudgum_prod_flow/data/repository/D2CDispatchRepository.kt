package com.example.gudgum_prod_flow.data.repository

import android.util.Log
import com.example.gudgum_prod_flow.data.remote.api.OperationsApiService
import com.example.gudgum_prod_flow.data.remote.dto.CreateD2CRequestItem
import com.example.gudgum_prod_flow.data.remote.dto.CreateD2CRequestPayload
import com.example.gudgum_prod_flow.data.remote.dto.CreateD2CRequestResponse
import com.example.gudgum_prod_flow.data.remote.dto.D2CRequestDto
import com.example.gudgum_prod_flow.data.remote.dto.FinishedGoodsAvailableRow
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mobile-side bridge to the D2C dispatch request flow.
 *
 * Stock checks + FIFO computation live on the server (so the same logic is
 * used by web Approve). The mobile app just sends inputs and renders results.
 *
 * Offline path is deliberately omitted for v1 — workers in the dispatch area
 * are expected to have connectivity. Easy to add later via PendingOperationEvent.
 */
@Singleton
class D2CDispatchRepository @Inject constructor(
    private val opsApi: OperationsApiService,
) {

    suspend fun getFinishedGoodsAvailable(): Result<List<FinishedGoodsAvailableRow>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val res = opsApi.getFinishedGoodsAvailable()
                if (!res.isSuccessful) error("HTTP ${res.code()}")
                res.body() ?: emptyList()
            }
        }

    suspend fun getChannels(): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val res = opsApi.getD2CChannels()
                if (!res.isSuccessful) error("HTTP ${res.code()}")
                res.body() ?: emptyList()
            }
        }

    suspend fun createRequest(
        channel: String,
        items: List<CreateD2CRequestItem>,
    ): Result<CreateD2CRequestResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = CreateD2CRequestPayload(
                channel  = channel,
                workerId = WorkerIdentityStore.workerId,
                items    = items,
            )
            val res = opsApi.createD2CRequest(payload)
            if (res.code() == 409) {
                val body = res.errorBody()?.string().orEmpty()
                error("Insufficient stock — $body")
            }
            if (!res.isSuccessful) error("HTTP ${res.code()}")
            res.body() ?: error("Empty response")
        }
    }

    suspend fun listMyPending(): Result<List<D2CRequestDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val res = opsApi.listD2CRequests(
                workerId = WorkerIdentityStore.workerId,
                status   = "pending",
            )
            if (!res.isSuccessful) error("HTTP ${res.code()}")
            // partially_approved requests also have pending lines worth showing
            val pending = res.body().orEmpty()
            val res2 = opsApi.listD2CRequests(
                workerId = WorkerIdentityStore.workerId,
                status   = "partially_approved",
            )
            val partial = if (res2.isSuccessful) res2.body().orEmpty() else emptyList()
            pending + partial
        }
    }

    suspend fun editRequest(
        requestId: String,
        channel: String,
        items: List<CreateD2CRequestItem>,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = CreateD2CRequestPayload(
                channel  = channel,
                workerId = WorkerIdentityStore.workerId,
                items    = items,
            )
            val res = opsApi.editD2CRequest(requestId, payload)
            if (res.code() == 409) {
                val body = res.errorBody()?.string().orEmpty()
                error("Insufficient stock — $body")
            }
            if (!res.isSuccessful) error("HTTP ${res.code()}")
            Unit
        }
    }

    suspend fun cancelRequest(requestId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val res = opsApi.cancelD2CRequest(requestId)
            if (!res.isSuccessful && res.code() != 204) error("HTTP ${res.code()}")
            Unit
        }
    }
}
