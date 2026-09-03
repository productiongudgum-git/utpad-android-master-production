package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.repository.FinishedGoodsRow
import com.example.gudgum_prod_flow.data.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Loads per-flavour finished-goods inventory (packed − dispatched).
 * Refresh is explicit (pull-to-refresh or initial load).
 */
@HiltViewModel
class FinishedGoodsViewModel @Inject constructor(
    private val repo: InventoryRepository,
) : ViewModel() {

    private val _rows = MutableStateFlow<List<FinishedGoodsRow>>(emptyList())
    val rows: StateFlow<List<FinishedGoodsRow>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { refresh() }

    fun refresh() {
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            repo.getFinishedGoodsInventory().fold(
                onSuccess = { _rows.value = it; _loading.value = false },
                onFailure = { _error.value = it.message ?: "Failed to load."; _loading.value = false },
            )
        }
    }

    fun clearError() { _error.value = null }
}
