package com.example.gudgum_prod_flow.data.remote.api

import com.example.gudgum_prod_flow.data.remote.dto.AuthResponse
import com.example.gudgum_prod_flow.data.remote.dto.PhoneLoginRequest
import com.example.gudgum_prod_flow.data.remote.dto.RefreshTokenRequest
import com.example.gudgum_prod_flow.data.remote.dto.SyncEventsRequest
import com.example.gudgum_prod_flow.data.remote.dto.SyncEventsResponse
import com.example.gudgum_prod_flow.data.remote.dto.UserInfoResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApiService {

    @POST("auth/login/phone")
    suspend fun loginWithPhone(
        @Body request: PhoneLoginRequest
    ): Response<AuthResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") token: String
    ): Response<Unit>

    @GET("auth/me")
    suspend fun getCurrentUser(
        @Header("Authorization") token: String
    ): Response<UserInfoResponse>

    @GET("auth/permissions")
    suspend fun getPermissions(
        @Header("Authorization") token: String
    ): Response<List<com.example.gudgum_prod_flow.data.remote.dto.PermissionDto>>

    @POST("auth/sync/events")
    suspend fun syncEvents(
        @Header("Authorization") token: String,
        @Body request: SyncEventsRequest
    ): Response<SyncEventsResponse>
}
