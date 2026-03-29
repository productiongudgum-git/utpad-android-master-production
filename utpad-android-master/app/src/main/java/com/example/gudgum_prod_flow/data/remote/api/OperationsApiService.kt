package com.example.gudgum_prod_flow.data.remote.api

import com.example.gudgum_prod_flow.data.remote.dto.OperationEventResponse
import com.example.gudgum_prod_flow.data.remote.dto.SubmitOperationEventRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface OperationsApiService {

    @POST("ops/events")
    suspend fun submitOperationEvent(
        @Body request: SubmitOperationEventRequest,
    ): Response<OperationEventResponse>
}
