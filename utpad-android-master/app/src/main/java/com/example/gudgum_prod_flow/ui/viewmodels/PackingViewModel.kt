package com.example.gudgum_prod_flow.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchWithPackingDto
import com.example.gudgum_prod_flow.data.remote.SupabaseRealtimeManager
import com.example.gudgum_prod_flow.data.repository.PackingRepository
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class PackingStatus { Complete, Partial }

@HiltViewModel
class PackingViewModel @Inject constructor(
    private val repository: PackingRepository,
    private val realtimeManager: SupabaseRealtimeManager,
) : ViewModel() {

    companion object {
        private const val TAG = "PackingViewModel"
    }

    // Step 1: packing status selection
    private val _selectedPackingStatus = MutableStateFlow<PackingStatus?>(null)
    val selectedPackingStatus: StateFlow<PackingStatus?> = _selectedPackingStatus.asStateFlow()

    // Step 2a: batches for "Complete" path (no complete packing session yet)
    private val _completeBatches = MutableStateFlow<List<ProductionBatchWithPackingDto>>(emptyList())
    val completeBatches: StateFlow<List<ProductionBatchWithPackingDto>> = _completeBatches.asStateFlow()

    // Step 2b: batches with partial packing sessions (left column)
    private val _partialBatches = MutableStateFlow<List<ProductionBatchWithPackingDto>>(emptyList())
    val partialBatches: StateFlow<List<ProductionBatchWithPackingDto>> = _partialBatches.asStateFlow()

    // Step 2b: batches with no packing sessions at all (right column)
    private val _unpackedBatches = MutableStateFlow<List<ProductionBatchWithPackingDto>>(emptyList())
    val unpackedBatches: StateFlow<List<ProductionBatchWithPackingDto>> = _unpackedBatches.asStateFlow()

    private val _batchesLoading = MutableStateFlow(false)
    val batchesLoading: StateFlow<Boolean> = _batchesLoading.asStateFlow()

    // Selected batch from step 2
    private val _selectedBatch = MutableStateFlow<ProductionBatchWithPackingDto?>(null)
    val selectedBatch: StateFlow<ProductionBatchWithPackingDto?> = _selectedBatch.asStateFlow()

    // Step 3: packing output
    private val _boxesMade = MutableStateFlow("")
    val boxesMade: StateFlow<String> = _boxesMade.asStateFlow()

    private val _packingDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val packingDate: StateFlow<String> = _packingDate.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    private var isOnline: Boolean = true

    init {
        realtimeManager.connect()
        viewModelScope.launch {
            realtimeManager.tableChanged.collect { table ->
                if (table == "production_batches" || table == "packing_sessions") {
                    refreshBatchLists()
                }
            }
        }
    }

    fun setOnlineStatus(online: Boolean) { isOnline = online }

    fun onPackingStatusSelected(status: PackingStatus) {
        _selectedPackingStatus.value = status
    }

    fun onBatchSelected(batch: ProductionBatchWithPackingDto) {
        _selectedBatch.value = batch
    }

    fun onBoxesMadeChanged(value: String) { _boxesMade.value = value }
    fun onPackingDateChanged(value: String) { _packingDate.value = value }

    fun nextStep() {
        val step = _currentWizardStep.value
        if (step < 3) {
            if (step == 1) {
                // Load batches appropriate to the selected packing status
                loadBatchesForSelectedStatus()
            }
            _currentWizardStep.value = step + 1
        }
    }

    fun previousStep() {
        if (_currentWizardStep.value > 1) _currentWizardStep.value--
    }

    private fun loadBatchesForSelectedStatus() {
        val status = _selectedPackingStatus.value ?: return
        viewModelScope.launch {
            _batchesLoading.value = true
            when (status) {
                PackingStatus.Complete -> {
                    val result = repository.getProductionBatchesForCompletePacking()
                    _completeBatches.value = result.getOrDefault(emptyList())
                }
                PackingStatus.Partial -> {
                    val result = repository.getProductionBatchesForPartialPacking()
                    val (partial, unpacked) = result.getOrDefault(Pair(emptyList(), emptyList()))
                    _partialBatches.value = partial
                    _unpackedBatches.value = unpacked
                }
            }
            _selectedBatch.value = null
            _batchesLoading.value = false
        }
    }

    private fun refreshBatchLists() {
        if (_currentWizardStep.value == 2) {
            loadBatchesForSelectedStatus()
        }
    }

    fun submit() {
        if (_submitState.value is SubmitState.Loading) return

        val batch = _selectedBatch.value
        if (batch == null) {
            _submitState.value = SubmitState.Error("Select a production batch")
            return
        }
        val packingStatus = _selectedPackingStatus.value
        if (packingStatus == null) {
            _submitState.value = SubmitState.Error("Select a packing status")
            return
        }
        val boxes = _boxesMade.value.toIntOrNull()
        if (boxes == null || boxes <= 0) {
            _submitState.value = SubmitState.Error("Enter a valid box count (> 0)")
            return
        }
        val unitsPacked = boxes * 15
        val statusStr = if (packingStatus == PackingStatus.Complete) "complete" else "partial"
        Log.d(TAG, "submit: statusStr='$statusStr' batchCode=${batch.batchCode} productionBatchId=${batch.id} boxes=$boxes")

        _submitState.value = SubmitState.Loading
        viewModelScope.launch {
            val result = repository.submitPacking(
                batchCode = batch.batchCode,
                flavorId = batch.flavorId,
                boxesPacked = boxes,
                unitsPacked = unitsPacked,
                packingDate = _packingDate.value,
                workerId = WorkerIdentityStore.workerId,
                isOnline = isOnline,
                productionBatchId = batch.id,
                status = statusStr,
            )
            result.onSuccess {
                val batchLabel = batch.batchNumber?.let { "Batch $it" } ?: ""
                val flavorLabel = batch.flavor?.name ?: ""
                _submitState.value = SubmitState.Success(
                    "Packed $boxes boxes for $batchLabel — $flavorLabel ($statusStr)"
                )
                clear()
            }
            result.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "Submission failed")
            }
        }
    }

    fun clear() {
        _selectedPackingStatus.value = null
        _selectedBatch.value = null
        _completeBatches.value = emptyList()
        _partialBatches.value = emptyList()
        _unpackedBatches.value = emptyList()
        _boxesMade.value = ""
        _packingDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
