package com.example.gudgum_prod_flow.domain.model

data class User(
    val userId: String,
    val tenantId: String,
    val phone: String,
    val name: String,
    val role: String,
    val factoryIds: List<String>,
    val allowedModules: List<String> = emptyList(),
)
