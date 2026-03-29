package com.example.gudgum_prod_flow.ui.viewmodels

sealed class SubmitState {
    object Idle : SubmitState()
    object Loading : SubmitState()
    data class Success(val message: String) : SubmitState()
    data class Error(val message: String) : SubmitState()
}
