package com.example.gudgum_prod_flow.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PhoneLoginRequest(
    val phone: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserInfoResponse
)

@Serializable
data class UserInfoResponse(
    val userId: String,
    val tenantId: String,
    val phone: String,
    val name: String,
    val role: String,
    val status: String,
    val factoryIds: List<String>,
    val permissions: List<PermissionDto>
)

@Serializable
data class PermissionDto(
    val module: String,
    val action: String,
    val resourceScope: String
)

@Serializable
data class SyncEventsRequest(
    val events: List<SyncEventDto>
)

@Serializable
data class SyncEventDto(
    val eventId: String,
    val userId: String,
    val eventType: String,
    val timestamp: Long,
    val metadata: String
)

@Serializable
data class SyncEventsResponse(
    val syncedCount: Int,
    val conflicts: List<SyncConflict>
)

@Serializable
data class SyncConflict(
    val type: String, // "pin_changed", "account_locked", "account_suspended"
    val userId: String,
    val message: String
)
