package com.example.gudgum_prod_flow.domain.usecase

import com.example.gudgum_prod_flow.data.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke() {
        authRepository.logout()
    }
}
