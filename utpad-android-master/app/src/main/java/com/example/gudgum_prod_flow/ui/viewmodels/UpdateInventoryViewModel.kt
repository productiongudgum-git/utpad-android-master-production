package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.network.ConnectivityObserver
import com.example.gudgum_prod_flow.data.remote.dto.BatchLookupDto
import com.example.gudgum_prod_flow.data.repository.InventoryRepository
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Update Inventory wizard:
 *   1. Worker types batch_code + batch_number → tap "Find batch" → goes to step 2
 *   2. Shows resolved flavour + current packed boxes; worker enters "add boxes" → tap Confirm → step 3
 *   3. Success/error state; worker can return to step 1 to do another batch.
 *
 * Online: writes a packing_sessions row directly.
 * Offline: queues a `packing_topup` event in the offline queue.
 */
@HiltViewModel
class UpdateInventoryViewModel @Inject constructor(
    private val repo: InventoryRepository,
    private val connectivity: ConnectivityObserver,
) : ViewModel() {

    private val _step = MutableStateFlow(UpdateInventoryStep.EnterBatch)
    val step: StateFlow<UpdateInventoryStep> = _step.asStateFlow()

    private val _batchCode = MutableStateFlow("")
    val batchCode: StateFlow<String> = _batchCode.asStateFlow()

    private val _batchNumber = MutableStateFlow("")
    val batchNumber: StateFlow<String> = _batchNumber.asStateFlow()

    private val _matches = MutableStateFlow<List<BatchLookupDto>>(emptyList())
    val matches: StateFlow<List<BatchLookupDto>> = _matches.asStateFlow()

    private val _resolved = MutableStateFlow<BatchLookupDto?>(null)
    val resolved: StateFlow<BatchLookupDto?> = _resolved.asStateFlow()

    private val _currentBoxes = MutableStateFlow(0)
    val currentBoxes: StateFlow<Int> = _currentBoxes.asStateFlow()

    private val _addBoxes = MutableStateFlow("")
    val addBoxes: StateFlow<String> = _addBoxes.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success.asStateFlow()

    fun onBatchCodeChange(v: String)   { _batchCode.value = v.uppercase(); _error.value = null }
    fun onBatchNumberChange(v: String) { _batchNumber.value = v.filter(Char::isDigit); _error.value = null }
    fun onAddBoxesChange(v: String)    { _addBoxes.value = v.filter(Char::isDigit); _error.value = null }
    fun clearError() { _error.value = null }
    fun clearSuccess() { _success.value = null }

    /**
     * Step 1 → 2 (or directly → 3). Resolves batch.
     *   - 0 matches → error
     *   - 1 match  → auto-pick that flavour, go straight to AddBoxes
     *   - 2+ matches → show PickFlavor step so the worker can choose
     */
    fun findBatch() {
        val code = _batchCode.value.trim()
        val num  = _batchNumber.value.toIntOrNull()
        if (code.isBlank()) { _error.value = "Enter the batch code."; return }
        if (num == null || num <= 0) { _error.value = "Enter a valid batch number."; return }
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            repo.lookupBatch(code, num).fold(
                onSuccess = { list ->
                    when {
                        list.isEmpty() -> {
                            _error.value = "Batch $code #$num not found."
                            _busy.value = false
                        }
                        list.size == 1 -> {
                            _matches.value = list
                            selectFlavor(list.first())
                        }
                        else -> {
                            _matches.value = list
                            _step.value = UpdateInventoryStep.PickFlavor
                            _busy.value = false
                        }
                    }
                },
                onFailure = {
                    _error.value = "Lookup failed: ${it.message ?: "unknown error"}"
                    _busy.value = false
                },
            )
        }
    }

    /** Called from the PickFlavor step (or auto-called when only one match). */
    fun selectFlavor(match: BatchLookupDto) {
        _busy.value = true
        _resolved.value = match
        viewModelScope.launch {
            repo.getBoxesPackedForBatch(match.id).fold(
                onSuccess = { _currentBoxes.value = it },
                onFailure = { _currentBoxes.value = 0 },
            )
            _step.value = UpdateInventoryStep.AddBoxes
            _busy.value = false
        }
    }

    /** Step 2 → 3. Writes the top-up row. */
    fun confirmTopUp() {
        val match = _resolved.value ?: return
        val toAdd = _addBoxes.value.toIntOrNull() ?: 0
        if (toAdd <= 0) { _error.value = "Enter a positive number of boxes."; return }
        if (WorkerIdentityStore.workerId.isBlank()) {
            _error.value = "Not logged in."
            return
        }
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            val online = connectivity.isOnline()
            repo.submitTopUpBoxes(
                batchCode         = match.batchCode,
                flavorId          = match.flavorId,
                productionBatchId = match.id,
                boxes             = toAdd,
                isOnline          = online,
            ).fold(
                onSuccess = {
                    val newTotal = _currentBoxes.value + toAdd
                    _success.value = if (online)
                        "Added $toAdd boxes. Batch now at $newTotal boxes."
                    else
                        "Saved offline. $toAdd boxes will sync when back online."
                    _busy.value = false
                    _step.value = UpdateInventoryStep.Done
                },
                onFailure = {
                    _error.value = it.message ?: "Failed to save."
                    _busy.value = false
                },
            )
        }
    }

    /** Reset wizard to enter another batch. */
    fun reset() {
        _step.value = UpdateInventoryStep.EnterBatch
        _batchCode.value = ""
        _batchNumber.value = ""
        _matches.value = emptyList()
        _resolved.value = null
        _currentBoxes.value = 0
        _addBoxes.value = ""
        _error.value = null
        _success.value = null
    }
}

enum class UpdateInventoryStep { EnterBatch, PickFlavor, AddBoxes, Done }
