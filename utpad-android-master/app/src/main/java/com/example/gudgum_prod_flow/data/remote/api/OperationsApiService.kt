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
}
