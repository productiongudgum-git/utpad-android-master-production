package com.example.gudgum_prod_flow.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.local.entity.CachedFlavorEntity
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchWithPackingDto
import com.example.gudgum_prod_flow.data.remote.SupabaseRealtimeManager
import com.example.gudgum_prod_flow.data.remote.dto.DEFAULT_UNITS_PER_BOX
import com.example.gudgum_prod_flow.data.repository.PackingRepository
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    // ── Step 1 ────────────────────────────────────────────────────────────────
    private val _selectedPackingStatus = MutableStateFlow<PackingStatus?>(null)
    val selectedPackingStatus: StateFlow<PackingStatus?> = _selectedPackingStatus.asStateFlow()

    // ── Step 2a (PATH A — Complete): all batches with no complete session ──────
    private val _completeBatches = MutableStateFlow<List<ProductionBatchWithPackingDto>>(emptyList())
    val completeBatches: StateFlow<List<ProductionBatchWithPackingDto>> = _completeBatches.asStateFlow()

    // ── Step 2b (PATH B — Partial): left column ────────────────────────────────
    // Batches that have at least one partial/null session and NO complete session.
    private val _partialBatches = MutableStateFlow<List<ProductionBatchWithPackingDto>>(emptyList())
    val partialBatches: StateFlow<List<ProductionBatchWithPackingDto>> = _partialBatches.asStateFlow()

    // ── Step 2b (PATH B — Partial): right column ──────────────────────────────
    // Batches with NO packing sessions at all.
    private val _unpackedBatches = MutableStateFlow<List<ProductionBatchWithPackingDto>>(emptyList())
    val unpackedBatches: StateFlow<List<ProductionBatchWithPackingDto>> = _unpackedBatches.asStateFlow()

    private val _batchesLoading = MutableStateFlow(false)
    val batchesLoading: StateFlow<Boolean> = _batchesLoading.asStateFlow()

    // ── Step 2 selection ──────────────────────────────────────────────────────
    private val _selectedBatch = MutableStateFlow<ProductionBatchWithPackingDto?>(null)
    val selectedBatch: StateFlow<ProductionBatchWithPackingDto?> = _selectedBatch.asStateFlow()

    /**
     * True when the selected batch came from the LEFT (partial/null) column in step 2b.
     * False when it came from the RIGHT (unpacked) column, or when on PATH A.
     *
     * Drives whether step 3 shows the "is packing now complete?" status question.
     */
    private val _isFromPartialList = MutableStateFlow(false)
    val isFromPartialList: StateFlow<Boolean> = _isFromPartialList.asStateFlow()

    // ── Step 3 status question ────────────────────────────────────────────────
    // Only relevant when isFromPartialList == true (PATH B, left column).
    // The worker explicitly chooses "complete" or "partial" for this session.
    private val _selectedFinalStatus = MutableStateFlow<PackingStatus?>(null)
    val selectedFinalStatus: StateFlow<PackingStatus?> = _selectedFinalStatus.asStateFlow()

    // ── Step 3 pack format ────────────────────────────────────────────────────
    // The box formats this batch's flavour can be packed into: the standard
    // format, plus any packing variants (e.g. Lemon 10s). One entry means the
    // flavour has no variants and the worker is never asked to choose.
    //
    // The chosen format decides which flavour the packing_sessions row is
    // written against, which is what keeps a 10-gum box out of the 15-gum
    // stock line — they are different goods and must not net together.
    private val _packFormats = MutableStateFlow<List<CachedFlavorEntity>>(emptyList())
    val packFormats: StateFlow<List<CachedFlavorEntity>> = _packFormats.asStateFlow()

    private val _selectedPackFormat = MutableStateFlow<CachedFlavorEntity?>(null)
    val selectedPackFormat: StateFlow<CachedFlavorEntity?> = _selectedPackFormat.asStateFlow()

    /** True only when there is a real choice to make — drives the step 3 picker. */
    val hasPackFormatChoice: StateFlow<Boolean> = _packFormats
        .map { it.size > 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ── Step 3 inputs ─────────────────────────────────────────────────────────
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

    /**
     * Called when the worker selects a batch in step 2.
     *
     * @param fromPartialList  true  → batch came from the LEFT (partial/null) column in step 2b;
     *                                 step 3 will show the "is packing now complete?" question.
     *                         false → batch came from the RIGHT (unpacked) column, or PATH A;
     *                                 step 3 goes straight to boxes + date (saves as partial).
     */
    fun onBatchSelected(batch: ProductionBatchWithPackingDto, fromPartialList: Boolean = false) {
        _selectedBatch.value = batch
        _isFromPartialList.value = fromPartialList
        _selectedFinalStatus.value = null
        loadPackFormatsFor(batch)
    }

    /**
     * Loads the box formats available for the selected batch and pre-selects the
     * standard one, so a flavour with no variants behaves exactly as it did
     * before: the worker sees no extra question and packs at the usual count.
     *
     * On a cache miss (fresh install, catalogue not synced yet) the list is left
     * empty and submit falls back to the batch's own flavour — never blocking a
     * packer who is offline in the factory.
     */
    private fun loadPackFormatsFor(batch: ProductionBatchWithPackingDto) {
        val flavorId = batch.flavorId
        if (flavorId.isNullOrBlank()) {
            _packFormats.value = emptyList()
            _selectedPackFormat.value = null
            return
        }
        viewModelScope.launch {
            val formats = runCatching { repository.getPackFormats(flavorId) }
                .onFailure { Log.w(TAG, "Pack format lookup failed: ${it.message}") }
                .getOrDefault(emptyList())
            _packFormats.value = formats
            // Standard format first (getPackFormatsFor orders base ahead of variants).
            _selectedPackFormat.value = formats.firstOrNull()
        }
    }

    fun onPackFormatSelected(format: CachedFlavorEntity) {
        _selectedPackFormat.value = format
    }

    /** Called from the step 3 status question (only shown when isFromPartialList == true). */
    fun onFinalStatusSelected(status: PackingStatus) {
        _selectedFinalStatus.value = status
    }

    fun onBoxesMadeChanged(value: String) { _boxesMade.value = value }
    fun onPackingDateChanged(value: String) { _packingDate.value = value }

    fun nextStep() {
        val step = _currentWizardStep.value
        if (step < 3) {
            if (step == 1) loadBatchesForSelectedStatus()
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
            // Clear selection state whenever the list is (re)loaded.
            _selectedBatch.value = null
            _isFromPartialList.value = false
            _selectedFinalStatus.value = null
            _packFormats.value = emptyList()
            _selectedPackFormat.value = null
            _batchesLoading.value = false
        }
    }

    private fun refreshBatchLists() {
        if (_currentWizardStep.value == 2) loadBatchesForSelectedStatus()
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

        // ── Determine which status string to save ─────────────────────────────
        // PATH A (step 1 = Complete)        → always "complete"
        // PATH B from RIGHT (unpacked col)  → always "partial" (no question asked)
        // PATH B from LEFT (partial col)    → worker chose in step 3 status question
        val statusStr: String = when {
            packingStatus == PackingStatus.Complete -> "complete"
            !_isFromPartialList.value -> "partial"
            else -> {
                val finalStatus = _selectedFinalStatus.value
                if (finalStatus == null) {
                    _submitState.value = SubmitState.Error("Select packing outcome for this session")
                    return
                }
                if (finalStatus == PackingStatus.Complete) "complete" else "partial"
            }
        }

        // ── Which box format was packed ───────────────────────────────────────
        // The session is written against the CHOSEN format's flavour, not the
        // batch's. For the standard format those are the same row; for a variant
        // it is the variant's own flavour, which is what gives the variant a
        // separate stock line and makes the packing-materials trigger deduct the
        // variant's own monocarton.
        //
        // production_batch_id still points at the parent batch, so one batch can
        // be split across formats and traceability back to the gum is unbroken.
        val format      = _selectedPackFormat.value
        val flavorId    = format?.id ?: batch.flavorId
        val unitsPerBox = format?.unitsPerBox ?: DEFAULT_UNITS_PER_BOX
        val unitsPacked = boxes * unitsPerBox

        Log.d(
            TAG,
            "submit: statusStr='$statusStr' batchCode=${batch.batchCode} " +
                "productionBatchId=${batch.id} boxes=$boxes unitsPerBox=$unitsPerBox " +
                "flavorId=$flavorId isVariant=${format?.isPackingVariant == true} " +
                "isFromPartialList=${_isFromPartialList.value}"
        )

        _submitState.value = SubmitState.Loading
        viewModelScope.launch {
            val result = repository.submitPacking(
                batchCode = batch.batchCode,
                flavorId = flavorId,
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
                val flavorLabel = format?.name ?: batch.flavor?.name ?: ""
                // Reset wizard state BEFORE setting Success so clear() doesn't overwrite the
                // success message (both run on the same coroutine frame / main-thread dispatch).
                clear()
                _submitState.value = SubmitState.Success(
                    "Packed $boxes boxes for $batchLabel — $flavorLabel ($statusStr)"
                )
            }
            result.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "Submission failed")
            }
        }
    }

    fun clear() {
        _selectedPackingStatus.value = null
        _selectedBatch.value = null
        _isFromPartialList.value = false
        _selectedFinalStatus.value = null
        _packFormats.value = emptyList()
        _selectedPackFormat.value = null
        _completeBatches.value = emptyList()
        _partialBatches.value = emptyList()
        _unpackedBatches.value = emptyList()
        _boxesMade.value = ""
        _packingDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        // Do NOT reset _submitState here — the UI dismisses it via clearSubmitState() after
        // showing the snackbar. Resetting here would wipe the Success/Error state before
        // the UI LaunchedEffect can react (same coroutine frame on main thread).
        _currentWizardStep.value = 1
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
