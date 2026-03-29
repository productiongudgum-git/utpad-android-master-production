package com.example.gudgum_prod_flow.domain.model

sealed class AuthResult {
    data class Success(
        val user: User,
        val offlineMode: Boolean = false
    ) : AuthResult()

    data class Error(val message: String) : AuthResult()
}
