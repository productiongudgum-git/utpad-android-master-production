package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.local.entity.CachedFlavorEntity
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchDto
import com.example.gudgum_prod_flow.data.repository.PackingRepository
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ShiftSummary(val shift: String, val totalPacked: String, val totalBoxes: String)

/** Represents a batch+flavor pair for selection in packing */
data class BatchFlavorOption(
    val batchCode: String,
    val flavorId: String?,
    val flavorName: String?,
    val flavorCode: String?,
    val productionDate: String,
) {
    val displayLabel: String
        get() = if (flavorName != null) "$batchCode — $flavorName" else batchCode
}

@HiltViewModel
class PackingViewModel @Inject constructor(
    private val repository: PackingRepository,
    private val realtimeManager: com.example.gudgum_prod_flow.data.remote.SupabaseRealtimeManager,
) : ViewModel() {

    // Batch+flavor options from open production batches
    private val _batchFlavorOptions = MutableStateFlow<List<BatchFlavorOption>>(emptyList())
    val batchFlavorOptions: StateFlow<List<BatchFlavorOption>> = _batchFlavorOptions.asStateFlow()

    private val _batchCodesLoading = MutableStateFlow(false)
    val batchCodesLoading: StateFlow<Boolean> = _batchCodesLoading.asStateFlow()

    // Legacy — kept for backward compatibility with UI
    private val _batchCodes = MutableStateFlow<List<String>>(emptyList())
    val batchCodes: StateFlow<List<String>> = _batchCodes.asStateFlow()

    private val _batchCode = MutableStateFlow("")
    val batchCode: StateFlow<String> = _batchCode.asStateFlow()

    private val _selectedBatchFlavor = MutableStateFlow<BatchFlavorOption?>(null)
    val selectedBatchFlavor: StateFlow<BatchFlavorOption?> = _selectedBatchFlavor.asStateFlow()

    // Flavour selection
    private val _flavors = MutableStateFlow<List<CachedFlavorEntity>>(emptyList())
    val flavors: StateFlow<List<CachedFlavorEntity>> = _flavors.asStateFlow()

    private val _selectedFlavor = MutableStateFlow<CachedFlavorEntity?>(null)
    val selectedFlavor: StateFlow<CachedFlavorEntity?> = _selectedFlavor.asStateFlow()

    private val _qtyPacked = MutableStateFlow("")
    val qtyPacked: StateFlow<String> = _qtyPacked.asStateFlow()

    private val _boxesMade = MutableStateFlow("")
    val boxesMade: StateFlow<String> = _boxesMade.asStateFlow()

    private val _packingDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val packingDate: StateFlow<String> = _packingDate.asStateFlow()

    private val _shiftSummary = MutableStateFlow(ShiftSummary("Morning", "0", "0"))
    val shiftSummary: StateFlow<ShiftSummary> = _shiftSummary.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    private var isOnline: Boolean = true

    init {
        loadBatchCodes()
        loadFlavors()

        realtimeManager.connect()
        viewModelScope.launch {
            realtimeManager.tableChanged.collect { table ->
                when (table) {
                    "production_batches", "packing_sessions" -> loadBatchCodes()
                    "gg_flavors" -> loadFlavors()
                }
            }
        }
    }

    private fun loadFlavors() {
        viewModelScope.launch {
            repository.getActiveFlavors().collect { entities ->
                _flavors.value = entities
            }
        }
    }

    fun setOnlineStatus(online: Boolean) { isOnline = online }

    fun loadBatchCodes() {
        viewModelScope.launch {
            _batchCodesLoading.value = true

            val result = repository.getOpenBatches()
            val batches = result.getOrDefault(emptyList())

            val options = batches.map { batch ->
                BatchFlavorOption(
                    batchCode = batch.batchCode,
                    flavorId = batch.flavorId,
                    flavorName = batch.flavor?.name,
                    flavorCode = batch.flavor?.code,
                    productionDate = batch.productionDate,
                )
            }
            _batchFlavorOptions.value = options
            _batchCodes.value = options.map { it.batchCode }.distinct()

            // Default to first option
            if (_selectedBatchFlavor.value == null && options.isNotEmpty()) {
                onBatchFlavorSelected(options.first())
            }

            _batchCodesLoading.value = false
        }
    }

    fun onBatchFlavorSelected(option: BatchFlavorOption) {
        _selectedBatchFlavor.value = option
        _batchCode.value = option.batchCode
    }

    fun onBatchCodeSelected(code: String) {
        _batchCode.value = code
        // Find corresponding batch+flavor option
        val option = _batchFlavorOptions.value.firstOrNull { it.batchCode == code }
        _selectedBatchFlavor.value = option
    }

    fun onFlavorSelected(flavor: CachedFlavorEntity) { _selectedFlavor.value = flavor }
    fun onQtyPackedChanged(value: String) { _qtyPacked.value = value }
    fun onBoxesMadeChanged(value: String) { _boxesMade.value = value }
    fun onPackingDateChanged(value: String) { _packingDate.value = value }

    fun nextStep() { if (_currentWizardStep.value < 3) _currentWizardStep.value++ }
    fun previousStep() { if (_currentWizardStep.value > 1) _currentWizardStep.value-- }

    fun submit() {
        if (_submitState.value is SubmitState.Loading) return

        val selected = _selectedBatchFlavor.value
        val code = selected?.batchCode ?: _batchCode.value.trim()
        if (code.isBlank()) {
            _submitState.value = SubmitState.Error("Select a batch code")
            return
        }
        val flavor = _selectedFlavor.value
        if (flavor == null) {
            _submitState.value = SubmitState.Error("Select a flavour")
            return
        }
        val boxes = _boxesMade.value.toIntOrNull()
        if (boxes == null || boxes <= 0) {
            _submitState.value = SubmitState.Error("Enter a valid box count (> 0)")
            return
        }
        val kgs = _qtyPacked.value.toDoubleOrNull()
        val unitsPacked = boxes * 15

        _submitState.value = SubmitState.Loading
        viewModelScope.launch {
            val result = repository.submitPacking(
                batchCode = code,
                flavorId = flavor.id,
                boxesPacked = boxes,
                kgsPacked = kgs,
                unitsPacked = unitsPacked,
                packingDate = _packingDate.value,
                workerId = WorkerIdentityStore.workerId,
                isOnline = isOnline,
            )
            result.onSuccess {
                _shiftSummary.value = ShiftSummary(
                    shift = "Current",
                    totalPacked = _qtyPacked.value.ifBlank { boxes.toString() },
                    totalBoxes = boxes.toString(),
                )
                _submitState.value = SubmitState.Success(
                    "Packed $boxes boxes ($unitsPacked units) for batch $code — ${flavor.name}"
                )
                clear()
            }
            result.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "Submission failed")
            }
        }
    }

    fun clear() {
        _selectedBatchFlavor.value = _batchFlavorOptions.value.firstOrNull()
        _batchCode.value = _selectedBatchFlavor.value?.batchCode ?: ""
        _selectedFlavor.value = null
        _qtyPacked.value = ""
        _boxesMade.value = ""
        _packingDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
