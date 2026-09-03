package com.example.gudgum_prod_flow.data.remote.api

import com.example.gudgum_prod_flow.data.remote.dto.OperationEventResponse
import com.example.gudgum_prod_flow.data.remote.dto.RegisterWorkerDeviceRequest
import com.example.gudgum_prod_flow.data.remote.dto.SubmitOperationEventRequest
import com.example.gudgum_prod_flow.data.remote.dto.WorkerDeviceResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

interface OperationsApiService {

    @POST("ops/events")
    suspend fun submitOperationEvent(
        @Body request: SubmitOperationEventRequest,
    ): Response<OperationEventResponse>

    /** Upsert this worker's FCM token in the worker_devices table. */
    @POST("ops/worker-devices")
    suspend fun registerWorkerDevice(
        @Body request: RegisterWorkerDeviceRequest,
    ): Response<WorkerDeviceResponse>

    /** Drop the device token — used on logout so the device stops getting pushes. */
    @DELETE("ops/worker-devices/{token}")
    suspend fun unregisterWorkerDevice(
        @Path("token") fcmToken: String,
    ): Response<Unit>

    // ── D2C dispatch requests ────────────────────────────────────────────────
    @POST("ops/d2c-requests")
    suspend fun createD2CRequest(
        @Body request: com.example.gudgum_prod_flow.data.remote.dto.CreateD2CRequestPayload,
    ): Response<com.example.gudgum_prod_flow.data.remote.dto.CreateD2CRequestResponse>

    @retrofit2.http.GET("ops/d2c-requests")
    suspend fun listD2CRequests(
        @retrofit2.http.Query("worker_id") workerId: String? = null,
        @retrofit2.http.Query("status")    status: String? = null,
    ): Response<List<com.example.gudgum_prod_flow.data.remote.dto.D2CRequestDto>>

    @retrofit2.http.GET("ops/d2c-requests/{id}")
    suspend fun getD2CRequest(
        @Path("id") id: String,
    ): Response<com.example.gudgum_prod_flow.data.remote.dto.D2CRequestDto>

    @retrofit2.http.PATCH("ops/d2c-requests/{id}")
    suspend fun editD2CRequest(
        @Path("id") id: String,
        @Body request: com.example.gudgum_prod_flow.data.remote.dto.CreateD2CRequestPayload,
    ): Response<Unit>

    @DELETE("ops/d2c-requests/{id}")
    suspend fun cancelD2CRequest(
        @Path("id") id: String,
    ): Response<Unit>

    @retrofit2.http.GET("ops/finished-goods-available")
    suspend fun getFinishedGoodsAvailable(): Response<List<com.example.gudgum_prod_flow.data.remote.dto.FinishedGoodsAvailableRow>>

    @retrofit2.http.GET("ops/d2c-channels")
    suspend fun getD2CChannels(): Response<List<String>>
}
