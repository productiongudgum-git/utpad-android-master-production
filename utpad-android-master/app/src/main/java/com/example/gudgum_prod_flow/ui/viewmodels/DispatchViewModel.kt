package com.example.gudgum_prod_flow.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.remote.dto.FlavorJoinDto
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceDto
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceItemDto
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchFifoDto
import com.example.gudgum_prod_flow.data.remote.SupabaseRealtimeManager
import com.example.gudgum_prod_flow.data.repository.DispatchRepository
import com.example.gudgum_prod_flow.data.repository.FifoAllocation
import com.example.gudgum_prod_flow.data.repository.FifoAllocationResult
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Top-level screen state for the Dispatch module. */
sealed class DispatchScreenState {
    /** 3-column kanban overview: RED / YELLOW / BLUE. */
    object Overview : DispatchScreenState()
    /** 2-step FIFO wizard — for RED (nothing packed) and YELLOW (partially packed) invoices. */
    object FifoWizard : DispatchScreenState()
    /** Simple dispatch-confirm screen — for BLUE (fully packed, awaiting dispatch) invoices. */
    object BlueConfirm : DispatchScreenState()
}

@HiltViewModel
class DispatchViewModel @Inject constructor(
    private val repository: DispatchRepository,
    private val realtimeManager: SupabaseRealtimeManager,
) : ViewModel() {

    companion object { private const val TAG = "DispatchViewModel" }

    // ── Screen-level state ─────────────────────────────────────────
    private val _screenState = MutableStateFlow<DispatchScreenState>(DispatchScreenState.Overview)
    val screenState: StateFlow<DispatchScreenState> = _screenState.asStateFlow()

    // ── Overview lists ─────────────────────────────────────────────
    // RED  : ALL flavors have packed_boxes = 0
    // YELLOW: SOME flavors have packed_boxes < quantity_boxes
    // BLUE : ALL flavors have packed_boxes >= quantity_boxes AND is_dispatched=false
    private val _redInvoices = MutableStateFlow<List<InvoiceDto>>(emptyList())
    val redInvoices: StateFlow<List<InvoiceDto>> = _redInvoices.asStateFlow()

    private val _yellowInvoices = MutableStateFlow<List<InvoiceDto>>(emptyList())
    val yellowInvoices: StateFlow<List<InvoiceDto>> = _yellowInvoices.asStateFlow()

    private val _blueInvoices = MutableStateFlow<List<InvoiceDto>>(emptyList())
    val blueInvoices: StateFlow<List<InvoiceDto>> = _blueInvoices.asStateFlow()

    // ── Invoice / items ────────────────────────────────────────────
    private val _invoices = MutableStateFlow<List<InvoiceDto>>(emptyList())
    val invoices: StateFlow<List<InvoiceDto>> = _invoices.asStateFlow()

    private val _invoicesLoading = MutableStateFlow(false)
    val invoicesLoading: StateFlow<Boolean> = _invoicesLoading.asStateFlow()

    private val _selectedInvoice = MutableStateFlow<InvoiceDto?>(null)
    val selectedInvoice: StateFlow<InvoiceDto?> = _selectedInvoice.asStateFlow()

    private val _invoiceItems = MutableStateFlow<List<InvoiceItemDto>>(emptyList())
    val invoiceItems: StateFlow<List<InvoiceItemDto>> = _invoiceItems.asStateFlow()

    // ── Multi-flavor FIFO allocation (Step 1 of wizard) ───────────
    private val _multiFifoAllocations = MutableStateFlow<List<FifoAllocationResult>>(emptyList())
    val multiFifoAllocations: StateFlow<List<FifoAllocationResult>> = _multiFifoAllocations.asStateFlow()

    private val _multiFifoLoading = MutableStateFlow(false)
    val multiFifoLoading: StateFlow<Boolean> = _multiFifoLoading.asStateFlow()

    // ── Confirmation flags ─────────────────────────────────────────
    private val _isPacked = MutableStateFlow(false)
    val isPacked: StateFlow<Boolean> = _isPacked.asStateFlow()

    private val _isDispatched = MutableStateFlow(false)
    val isDispatched: StateFlow<Boolean> = _isDispatched.asStateFlow()

    private val _dispatchDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val dispatchDate: StateFlow<String> = _dispatchDate.asStateFlow()

    // ── Common ─────────────────────────────────────────────────────
    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    private val _allInvoices = MutableStateFlow<List<InvoiceDto>>(emptyList())
    val allInvoices: StateFlow<List<InvoiceDto>> = _allInvoices.asStateFlow()

    private var isOnline: Boolean = true

    // ── Column classification ──────────────────────────────────────

    private enum class InvoiceColumn { RED, YELLOW, BLUE }

    /**
     * Classifies an invoice into RED / YELLOW / BLUE based purely on
     * packed_boxes per flavor in items jsonb (no dispatch_events lookup).
     *
     * RED    = all flavors have packed_boxes == 0
     * YELLOW = at least one flavor has 0 < packed_boxes < quantity_boxes
     * BLUE   = all flavors have packed_boxes >= quantity_boxes (and not dispatched)
     */
    private fun invoiceColumn(invoice: InvoiceDto): InvoiceColumn {
        val items = invoice.items
        if (items.isEmpty()) return InvoiceColumn.RED
        val allFullyPacked = items.all { item ->
            val needed = if (item.quantityBoxes != null && item.quantityBoxes > 0)
                item.quantityBoxes
            else Math.ceil(item.quantityUnits / 15.0).toInt()
            item.packedBoxes >= needed
        }
        val anyPacked = items.any { it.packedBoxes > 0 }
        return when {
            allFullyPacked -> InvoiceColumn.BLUE
            anyPacked -> InvoiceColumn.YELLOW
            else -> InvoiceColumn.RED
        }
    }

    init {
        loadInvoices()
        realtimeManager.connect()
        viewModelScope.launch {
            realtimeManager.tableChanged.collect { table ->
                when (table) {
                    "gg_invoices", "gg_invoice_items", "dispatch_events" -> loadInvoices()
                }
            }
        }
    }

    fun setOnlineStatus(online: Boolean) { isOnline = online }

    fun loadInvoices() {
        viewModelScope.launch {
            _invoicesLoading.value = true
            repository.getActiveInvoices().onSuccess { list ->
                _invoices.value = list
                _allInvoices.value = list
                _redInvoices.value = list.filter { invoiceColumn(it) == InvoiceColumn.RED }
                _yellowInvoices.value = list.filter { invoiceColumn(it) == InvoiceColumn.YELLOW }
                _blueInvoices.value = list.filter { invoiceColumn(it) == InvoiceColumn.BLUE }
            }
            _invoicesLoading.value = false
        }
    }

    // ── Screen navigation ──────────────────────────────────────────

    /**
     * Open the 2-step FIFO wizard for a RED or YELLOW invoice.
     * FIFO remaining is calculated from packed_boxes in items jsonb.
     */
    fun openFifoWizard(invoice: InvoiceDto) {
        _isPacked.value = false
        _isDispatched.value = false
        prepareInvoice(invoice)
        _currentWizardStep.value = 1
        _screenState.value = DispatchScreenState.FifoWizard
        loadMultiFifoForInvoice()
    }

    /**
     * Open the simple dispatch-confirm screen for a BLUE invoice.
     * No FIFO needed — inventory was already reduced during packing.
     */
    fun openBlueConfirm(invoice: InvoiceDto) {
        prepareInvoice(invoice)
        _isPacked.value = true
        _isDispatched.value = false
        _screenState.value = DispatchScreenState.BlueConfirm
    }

    fun backToOverview() {
        _screenState.value = DispatchScreenState.Overview
        reset()
    }

    // ── Invoice preparation ────────────────────────────────────────

    private fun prepareInvoice(invoice: InvoiceDto) {
        _selectedInvoice.value = invoice
        _multiFifoAllocations.value = emptyList()
        _invoiceItems.value = invoice.items
            .groupBy { it.flavorId }
            .map { (flavorId, group) ->
                InvoiceItemDto(
                    id = "",
                    invoiceId = invoice.id,
                    flavorId = flavorId,
                    quantityUnits = group.sumOf { it.quantityUnits },
                    quantityBoxes = group.mapNotNull { it.quantityBoxes }.takeIf { it.isNotEmpty() }?.sum(),
                    packedBoxes = group.sumOf { it.packedBoxes },
                    flavor = FlavorJoinDto(id = flavorId, name = group.first().flavorName, code = ""),
                )
            }
    }

    // ── Multi-flavor FIFO loading ──────────────────────────────────

    /**
     * Loads FIFO allocations for all flavors that still have remaining boxes to pack.
     * remaining_to_pack = quantity_boxes - packed_boxes (from items jsonb).
     */
    private fun loadMultiFifoForInvoice() {
        val items = _invoiceItems.value
        if (items.isEmpty()) return

        _multiFifoLoading.value = true
        viewModelScope.launch {
            val itemsWithRemaining = items.filter { it.remainingBoxes > 0 }

            if (itemsWithRemaining.isEmpty()) {
                _multiFifoAllocations.value = emptyList()
                _multiFifoLoading.value = false
                return@launch
            }

            val results = try {
                coroutineScope {
                    itemsWithRemaining.map { item ->
                        async {
                            val inventory = repository.getInventoryByFlavor(item.flavorId)
                                .getOrElse { emptyList() }
                            val totalAvailable = inventory.sumOf { it.expectedBoxes }
                            val toAllocate = minOf(item.remainingBoxes, totalAvailable)
                            FifoAllocationResult(
                                flavorId = item.flavorId,
                                flavorName = item.flavor?.name ?: item.flavorId,
                                boxesNeeded = item.remainingBoxes,
                                availableBoxes = totalAvailable,
                                allocations = computeFifoFor(toAllocate, inventory),
                            )
                        }
                    }.awaitAll()
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadMultiFifoForInvoice failed: ${e.message}", e)
                emptyList()
            }

            _multiFifoAllocations.value = results
            _multiFifoLoading.value = false
            Log.d(TAG, "FIFO loaded for ${results.size} flavors — " +
                results.joinToString { "${it.flavorName}: ${it.availableBoxes}/${it.boxesNeeded}" })
        }
    }

    private fun computeFifoFor(needed: Int, inventory: List<ProductionBatchFifoDto>): List<FifoAllocation> {
        val lines = mutableListOf<FifoAllocation>()
        var remaining = needed
        for (batch in inventory.sortedBy { it.productionDate }) {
            if (remaining <= 0) break
            val take = minOf(remaining, batch.expectedBoxes)
            lines.add(FifoAllocation(batch.id, batch.batchCode, batch.expectedBoxes, take))
            remaining -= take
        }
        return lines
    }

    // ── Step 2: Confirm toggles ────────────────────────────────────

    fun onPackedToggle(value: Boolean) {
        _isPacked.value = value
        if (!value) _isDispatched.value = false
    }

    /** Dispatched toggle: only allows true when ALL FIFO allocations are sufficient. */
    fun onDispatchedToggle(value: Boolean) {
        val allSufficient = _multiFifoAllocations.value.isNotEmpty() &&
            _multiFifoAllocations.value.all { it.isSufficient }
        _isDispatched.value = if (value && !allSufficient) false else value
    }

    fun onDispatchDateChanged(value: String) { _dispatchDate.value = value }

    // ── Wizard navigation ──────────────────────────────────────────

    fun nextStep() {
        if (_currentWizardStep.value < 2) {
            _currentWizardStep.value = 2
            // Auto-tick dispatched only when every flavor has full stock
            val allocs = _multiFifoAllocations.value
            if (allocs.isNotEmpty() && allocs.all { it.isSufficient }) {
                _isDispatched.value = true
            }
        }
    }

    fun previousStep() {
        if (_currentWizardStep.value > 1) _currentWizardStep.value = 1
    }

    // ── Submit ─────────────────────────────────────────────────────

    fun submit() {
        if (_submitState.value is SubmitState.Loading) return

        val invoice = _selectedInvoice.value ?: run {
            _submitState.value = SubmitState.Error("No invoice selected")
            return
        }

        if (_screenState.value == DispatchScreenState.BlueConfirm) {
            submitBlueDispatch(invoice)
            return
        }

        if (!_isPacked.value) {
            _submitState.value = SubmitState.Error("Tick 'Mark as Packed' before submitting")
            return
        }

        val allAllocations = _multiFifoAllocations.value
        if (allAllocations.none { it.allocations.isNotEmpty() }) {
            _submitState.value = SubmitState.Error("No stock available to pack")
            return
        }

        _submitState.value = SubmitState.Loading
        viewModelScope.launch {
            // Accumulate packed_boxes per flavor for the invoice items jsonb update
            val allocatedPerFlavor: Map<String, Int> = allAllocations.associate { r ->
                r.flavorId to r.allocations.sumOf { it.unitsToTake }
            }
            val updatedItems = invoice.items.map { item ->
                val extra = allocatedPerFlavor[item.flavorId] ?: 0
                item.copy(packedBoxes = item.packedBoxes + extra)
            }

            // Invoice is fully packed only when every flavor reaches its target
            val allFullyPacked = updatedItems.isNotEmpty() && updatedItems.all { item ->
                val needed = if (item.quantityBoxes != null && item.quantityBoxes > 0)
                    item.quantityBoxes
                else Math.ceil(item.quantityUnits / 15.0).toInt()
                item.packedBoxes >= needed
            }

            // isDispatched only meaningful when fully packed
            val finalIsPacked = allFullyPacked
            val finalIsDispatched = _isDispatched.value && finalIsPacked

            Log.d(TAG, "submit() ${invoice.invoiceNumber} allFullyPacked=$allFullyPacked " +
                "finalIsPacked=$finalIsPacked finalDispatched=$finalIsDispatched online=$isOnline")

            val result = repository.submitFifoPackDispatch(
                invoiceId = invoice.id,
                invoiceNumber = invoice.invoiceNumber,
                customerName = invoice.customerName,
                allocations = allAllocations,
                isPacked = finalIsPacked,
                isDispatched = finalIsDispatched,
                dispatchDate = _dispatchDate.value,
                workerId = WorkerIdentityStore.workerId,
                isOnline = isOnline,
                updatedItems = updatedItems,
            )

            result.onSuccess {
                val totalBoxes = allAllocations.sumOf { r -> r.allocations.sumOf { it.unitsToTake } }
                val msg = when {
                    !isOnline -> "Saved offline — will sync when connected"
                    finalIsDispatched -> "Invoice dispatched — $totalBoxes boxes"
                    finalIsPacked -> "Fully packed — $totalBoxes boxes (awaiting dispatch)"
                    else -> "$totalBoxes boxes packed (partial — more stock needed)"
                }
                Log.d(TAG, "submit() success: $msg")
                _submitState.value = SubmitState.Success(msg)
                reset()
                loadInvoices()
            }
            result.onFailure { e ->
                Log.e(TAG, "submit() failed: ${e.message}", e)
                _submitState.value = SubmitState.Error(e.message ?: "Submit failed")
            }
        }
    }

    private fun submitBlueDispatch(invoice: InvoiceDto) {
        _submitState.value = SubmitState.Loading
        viewModelScope.launch {
            val result = repository.submitBlueDispatch(
                invoiceId = invoice.id,
                dispatchDate = _dispatchDate.value,
                workerId = WorkerIdentityStore.workerId,
                isOnline = isOnline,
            )
            result.onSuccess {
                Log.d(TAG, "submitBlueDispatch success for ${invoice.invoiceNumber}")
                _submitState.value = SubmitState.Success("Invoice ${invoice.invoiceNumber} dispatched")
                reset()
                loadInvoices()
            }
            result.onFailure { e ->
                Log.e(TAG, "submitBlueDispatch failed: ${e.message}", e)
                _submitState.value = SubmitState.Error(e.message ?: "Dispatch failed")
            }
        }
    }

    // ── Tracking: toggle from summary table ────────────────────────

    fun toggleInvoicePacked(invoice: InvoiceDto) {
        viewModelScope.launch {
            repository.updateInvoiceStatus(invoice.id, isPacked = !invoice.isPacked)
            loadInvoices()
        }
    }

    fun toggleInvoiceDispatched(invoice: InvoiceDto) {
        viewModelScope.launch {
            repository.updateInvoiceStatus(invoice.id, isDispatched = !invoice.isDispatched)
            loadInvoices()
        }
    }

    fun reset() {
        _selectedInvoice.value = null
        _invoiceItems.value = emptyList()
        _multiFifoAllocations.value = emptyList()
        _multiFifoLoading.value = false
        _isPacked.value = false
        _isDispatched.value = false
        _dispatchDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
