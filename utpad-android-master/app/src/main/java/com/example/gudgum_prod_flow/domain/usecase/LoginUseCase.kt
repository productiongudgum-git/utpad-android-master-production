package com.example.gudgum_prod_flow.domain.usecase

import com.example.gudgum_prod_flow.data.repository.AuthRepository
import com.example.gudgum_prod_flow.domain.model.AuthResult
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phone: String): AuthResult {
        val normalizedPhone = normalizePhone(phone)
        if (normalizedPhone.isBlank()) {
            return AuthResult.Error("Phone is required.")
        }
        if (!normalizedPhone.matches(Regex("^[6-9]\\d{9}$"))) {
            return AuthResult.Error("Enter a valid 10-digit Indian mobile number.")
        }
        return authRepository.login(normalizedPhone)
    }

    private fun normalizePhone(phone: String): String {
        val digits = phone.filter(Char::isDigit)
        return when {
            digits.length >= 12 && digits.startsWith("91") -> digits.takeLast(10)
            else -> digits.take(10)
        }
    }
}
