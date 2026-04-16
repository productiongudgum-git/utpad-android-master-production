package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import com.example.gudgum_prod_flow.domain.model.AuthResult
import com.example.gudgum_prod_flow.domain.usecase.LoginUseCase
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkerSession(
    val workerLabel: String,
    val phone: String,
    val role: String,
    val authorizedRoutes: Set<String>,
    val homeRoute: String,
)

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val workerLabel: String, val authorizedRoute: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _workerSession = MutableStateFlow<WorkerSession?>(null)
    val workerSession: StateFlow<WorkerSession?> = _workerSession.asStateFlow()

    fun onPhoneChanged(value: String) {
        val digits = value.filter(Char::isDigit)
        _phone.value = when {
            digits.length >= 12 && digits.startsWith("91") -> digits.takeLast(10)
            else -> digits.take(10)
        }
        if (_loginState.value is LoginState.Error) {
            _loginState.value = LoginState.Idle
        }
    }

    fun resetLoginState() {
        if (_loginState.value is LoginState.Error) {
            _loginState.value = LoginState.Idle
        }
    }

    fun submitLogin() {
        performLogin()
    }

    fun consumeLoginSuccess() {
        if (_loginState.value is LoginState.Success) {
            _loginState.value = LoginState.Idle
        }
    }

    fun authorizedHomeRoute(): String {
        return _workerSession.value?.homeRoute ?: AppRoute.WorkerLogin
    }

    private fun performLogin() {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            when (val result = loginUseCase(_phone.value)) {
                is AuthResult.Success -> {
                    val user = result.user
                    val allowedRoutes = routesForModules(user.allowedModules, user.role)
                    val homeRoute = allowedRoutes.firstOrNull()

                    if (homeRoute == null) {
                        _loginState.value = LoginState.Error(
                            "This user is not assigned to any mobile module.",
                        )
                        return@launch
                    }

                    _workerSession.value = WorkerSession(
                        workerLabel = user.name,
                        phone = user.phone,
                        role = user.role,
                        authorizedRoutes = allowedRoutes,
                        homeRoute = homeRoute,
                    )
                    WorkerIdentityStore.setIdentity(
                        userId = user.userId,
                        phone = user.phone,
                        label = user.name,
                        role = user.role,
                    )
                    _loginState.value = LoginState.Success(
                        workerLabel = user.name,
                        authorizedRoute = homeRoute,
                    )
                }
                is AuthResult.Error -> {
                    _loginState.value = LoginState.Error(result.message)
                }
            }
        }
    }

    fun logout() {
        WorkerIdentityStore.clear()
        _workerSession.value = null
        _loginState.value = LoginState.Idle
        _phone.value = ""
    }

    private fun routesForModules(allowedModules: List<String>, role: String): Set<String> {
        val fromModules = allowedModules
            .map { it.trim().lowercase() }
            .mapNotNull { moduleToRoute(it) }
            .toCollection(linkedSetOf())

        // Returns is always bundled with Dispatch — a worker who can dispatch can also process returns
        if (AppRoute.Dispatch in fromModules) fromModules.add(AppRoute.Returns)

        if (fromModules.isNotEmpty()) return fromModules

        return when (role.trim().lowercase().replace('-', '_').replace(' ', '_')) {
            "inwarding", "inwarding_staff" -> linkedSetOf(AppRoute.Inwarding)
            "production", "production_operator" -> linkedSetOf(AppRoute.Production)
            "packing", "packing_staff" -> linkedSetOf(AppRoute.Packing)
            "dispatch", "dispatch_staff" -> linkedSetOf(AppRoute.Dispatch, AppRoute.Returns)
            "factory_supervisor", "tenant_admin", "platform_admin", "worker" -> linkedSetOf(
                AppRoute.Inwarding,
                AppRoute.Production,
                AppRoute.Packing,
                AppRoute.Dispatch,
                AppRoute.Returns,
            )
            else -> emptySet()
        }
    }

    private fun moduleToRoute(module: String): String? {
        return when (module) {
            "inwarding" -> AppRoute.Inwarding
            "production" -> AppRoute.Production
            "packing" -> AppRoute.Packing
            "dispatch" -> AppRoute.Dispatch
            "returns" -> AppRoute.Returns
            else -> null
        }
    }
}
