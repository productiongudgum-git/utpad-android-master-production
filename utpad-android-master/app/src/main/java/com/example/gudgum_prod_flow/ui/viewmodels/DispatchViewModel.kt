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
    /** Default: 2-column kanban overview of all undispatched invoices. */
    object Overview : DispatchScreenState()
    /** 2-step FIFO wizard — red invoices + yellow Case 2 (partially dispatched). */
    object FifoWizard : DispatchScreenState()
    /** Simple confirm screen — yellow Case 1 (never dispatched before). */
    object YellowConfirm : DispatchScreenState()
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
    private val _redInvoices = MutableStateFlow<List<InvoiceDto>>(emptyList())
    val redInvoices: StateFlow<List<InvoiceDto>> = _redInvoices.asStateFlow()

    private val _yellowInvoices = MutableStateFlow<List<InvoiceDto>>(emptyList())
    val yellowInvoices: StateFlow<List<InvoiceDto>> = _yellowInvoices.asStateFlow()

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

    // ── Step 2: Confirm ────────────────────────────────────────────
    private val _isPacked = MutableStateFlow(false)
    val isPacked: StateFlow<Boolean> = _isPacked.asStateFlow()

    private val _isDispatched = MutableStateFlow(false)
    val isDispatched: StateFlow<Boolean> = _isDispatched.asStateFlow()

    private val _dispatchDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val dispatchDate: StateFlow<String> = _dispatchDate.asStateFlow()

    /** True when a yellow (already-packed) invoice is open — pre-ticks isPacked. */
    private val _invoiceAlreadyPacked = MutableStateFlow(false)
    val invoiceAlreadyPacked: StateFlow<Boolean> = _invoiceAlreadyPacked.asStateFlow()

    private val _alreadyDispatchedPerFlavor = MutableStateFlow<Map<String, Int>>(emptyMap())

    // ── Common ─────────────────────────────────────────────────────
    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    private val _allInvoices = MutableStateFlow<List<InvoiceDto>>(emptyList())
    val allInvoices: StateFlow<List<InvoiceDto>> = _allInvoices.asStateFlow()

    private var isOnline: Boolean = true

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
                _redInvoices.value = list.filter { !it.isPacked }
                _yellowInvoices.value = list.filter { it.isPacked }
            }
            _invoicesLoading.value = false
        }
    }

    // ── Screen navigation ──────────────────────────────────────────

    /** Tap a red (not-packed) card: load FIFO for ALL flavors, enter wizard. */
    fun openRedWizard(invoice: InvoiceDto) {
        _invoiceAlreadyPacked.value = false
        _isPacked.value = false
        _isDispatched.value = false
        prepareInvoice(invoice)
        _currentWizardStep.value = 1
        _screenState.value = DispatchScreenState.FifoWizard
        loadMultiFifoForInvoice(isYellowFlow = false)
    }

    /**
     * Tap a yellow (packed) card.
     * Checks dispatch_events to determine which case applies:
     *   Case 1 (count = 0): never dispatched → simple confirm screen, FIFO computed silently.
     *   Case 2 (count > 0): partially dispatched → FIFO wizard for remaining flavors only.
     */
    fun openYellowWizard(invoice: InvoiceDto) {
        _invoiceAlreadyPacked.value = true
        _isPacked.value = true
        _isDispatched.value = false
        prepareInvoice(invoice)
        _multiFifoLoading.value = true  // show spinner immediately while we check
        _screenState.value = DispatchScreenState.YellowConfirm  // navigate now, may redirect

        viewModelScope.launch {
            val alreadyDispatchedMap = repository.getAlreadyDispatchedPerFlavor(invoice.id)
                .getOrElse { emptyMap() }
            _alreadyDispatchedPerFlavor.value = alreadyDispatchedMap

            val hasAnyPriorDispatch = alreadyDispatchedMap.values.any { it > 0 }

            if (!hasAnyPriorDispatch) {
                // Case 1: never dispatched — auto-dispatch all, no user input needed
                _isDispatched.value = true
                loadMultiFifoForInvoice(isYellowFlow = false)
                // screen stays at YellowConfirm
            } else {
                // Case 2: partially dispatched — show FIFO wizard for remaining flavors
                _currentWizardStep.value = 1
                _screenState.value = DispatchScreenState.FifoWizard
                loadMultiFifoForInvoice(isYellowFlow = true)
            }
        }
    }

    /** Navigate back to the 2-column overview and clear wizard state. */
    fun backToOverview() {
        _screenState.value = DispatchScreenState.Overview
        reset()
    }

    // ── Invoice preparation ────────────────────────────────────────

    private fun prepareInvoice(invoice: InvoiceDto) {
        _selectedInvoice.value = invoice
        _multiFifoAllocations.value = emptyList()
        _alreadyDispatchedPerFlavor.value = emptyMap()
        _invoiceItems.value = invoice.items
            .groupBy { it.flavorId }
            .map { (flavorId, group) ->
                InvoiceItemDto(
                    id = "",
                    invoiceId = invoice.id,
                    flavorId = flavorId,
                    quantityUnits = group.sumOf { it.quantityUnits },
                    quantityBoxes = group.mapNotNull { it.quantityBoxes }.takeIf { it.isNotEmpty() }?.sum(),
                    flavor = FlavorJoinDto(id = flavorId, name = group.first().flavorName, code = ""),
                )
            }
    }

    // ── Multi-flavor FIFO loading ──────────────────────────────────

    private fun loadMultiFifoForInvoice(isYellowFlow: Boolean) {
        val invoice = _selectedInvoice.value ?: return
        val items = _invoiceItems.value
        if (items.isEmpty()) return

        _multiFifoLoading.value = true
        viewModelScope.launch {
            // Fetch already-dispatched per flavor for this invoice
            val alreadyDispatchedMap = repository.getAlreadyDispatchedPerFlavor(invoice.id)
                .getOrElse { emptyMap() }
            _alreadyDispatchedPerFlavor.value = alreadyDispatchedMap

            // For yellow flow: skip flavors already marked dispatched in items jsonb,
            // or where all boxes have been dispatched via dispatch_events
            val itemsToProcess = if (isYellowFlow) {
                items.filter { item ->
                    val jsonDispatched = invoice.items
                        .firstOrNull { it.flavorId == item.flavorId }?.dispatched ?: false
                    val alreadyDispatched = alreadyDispatchedMap[item.flavorId] ?: 0
                    !jsonDispatched && alreadyDispatched < item.resolvedBoxes
                }
            } else items

            if (itemsToProcess.isEmpty()) {
                _multiFifoAllocations.value = emptyList()
                _multiFifoLoading.value = false
                return@launch
            }

            // Load FIFO for all flavors concurrently
            val results = try {
                coroutineScope {
                    itemsToProcess.map { item ->
                        async {
                            val alreadyDispatched = alreadyDispatchedMap[item.flavorId] ?: 0
                            val delta = maxOf(0, item.resolvedBoxes - alreadyDispatched)
                            val inventory = repository.getInventoryByFlavor(item.flavorId)
                                .getOrElse { emptyList() }
                            val totalAvailable = inventory.sumOf { it.expectedBoxes }
                            FifoAllocationResult(
                                flavorId = item.flavorId,
                                flavorName = item.flavor?.name ?: item.flavorId,
                                boxesNeeded = delta,
                                availableBoxes = totalAvailable,
                                allocations = computeFifoFor(delta, inventory),
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
            Log.d(TAG, "Loaded FIFO for ${results.size} flavors — " +
                results.joinToString { "${it.flavorName}:${it.availableBoxes}/${it.boxesNeeded}" })
        }
    }

    private fun computeFifoFor(needed: Int, inventory: List<ProductionBatchFifoDto>): List<FifoAllocation> {
        val lines = mutableListOf<FifoAllocation>()
        var remaining = needed
        for (item in inventory.sortedBy { it.productionDate }) {
            if (remaining <= 0) break
            val take = minOf(remaining, item.expectedBoxes)
            lines.add(FifoAllocation(item.id, item.batchCode, item.expectedBoxes, take))
            remaining -= take
        }
        return lines
    }

    // ── Step 2: Confirm toggles ────────────────────────────────────

    fun onPackedToggle(value: Boolean) {
        _isPacked.value = value
        if (!value) _isDispatched.value = false
    }
    fun onDispatchedToggle(value: Boolean) { _isDispatched.value = value }
    fun onDispatchDateChanged(value: String) { _dispatchDate.value = value }

    // ── Navigation ─────────────────────────────────────────────────

    fun nextStep() {
        if (_currentWizardStep.value < 2) _currentWizardStep.value = 2
    }

    fun previousStep() {
        if (_currentWizardStep.value > 1) _currentWizardStep.value = 1
    }

    // ── Submit ─────────────────────────────────────────────────────

    fun submit() {
        if (_submitState.value is SubmitState.Loading) return

        val invoice = _selectedInvoice.value
        if (invoice == null) {
            _submitState.value = SubmitState.Error("No invoice selected")
            return
        }
        if (!_isPacked.value) {
            _submitState.value = SubmitState.Error("Mark invoice as packed before dispatching")
            return
        }

        val allAllocations = _multiFifoAllocations.value
        val sufficientAllocations = allAllocations.filter { it.isSufficient }

        if (sufficientAllocations.isEmpty()) {
            _submitState.value = SubmitState.Error("No flavors have sufficient stock to dispatch")
            return
        }

        _submitState.value = SubmitState.Loading
        viewModelScope.launch {
            // Mark dispatched=true in items jsonb for each successfully dispatched flavor
            val dispatchedFlavorIds = sufficientAllocations.map { it.flavorId }.toSet()
            val updatedItems = invoice.items.map { item ->
                if (item.flavorId in dispatchedFlavorIds) item.copy(dispatched = true) else item
            }

            // All flavors dispatched → full dispatch; some insufficient → partial (stays yellow)
            val allFlavorsDispatched = allAllocations.all { it.isSufficient }
            val finalIsDispatched = _isDispatched.value && allFlavorsDispatched
            val finalIsPacked = _isPacked.value || allFlavorsDispatched

            Log.d(TAG, "submit() invoice=${invoice.invoiceNumber} " +
                "sufficient=${sufficientAllocations.size}/${allAllocations.size} " +
                "finalDispatched=$finalIsDispatched online=$isOnline")

            val result = repository.submitMultipleFlavorsDispatch(
                invoiceId = invoice.id,
                invoiceNumber = invoice.invoiceNumber,
                customerName = invoice.customerName,
                allocations = sufficientAllocations,
                isPacked = finalIsPacked,
                isDispatched = finalIsDispatched,
                dispatchDate = _dispatchDate.value,
                workerId = WorkerIdentityStore.workerId,
                isOnline = isOnline,
                updatedItems = updatedItems,
            )

            result.onSuccess {
                val totalBoxes = sufficientAllocations.sumOf { r -> r.allocations.sumOf { it.unitsToTake } }
                val msg = when {
                    !isOnline -> "Dispatch saved offline — will sync when connected"
                    allFlavorsDispatched -> "All $totalBoxes boxes dispatched for ${invoice.invoiceNumber}"
                    else -> "$totalBoxes boxes dispatched (partial) — ${allAllocations.size - sufficientAllocations.size} flavor(s) pending stock"
                }
                Log.d(TAG, "submit() success: $msg")
                _submitState.value = SubmitState.Success(msg)
                reset()
                loadInvoices()
            }
            result.onFailure { e ->
                Log.e(TAG, "submit() failed: ${e.message}", e)
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
        _invoiceAlreadyPacked.value = false
        _alreadyDispatchedPerFlavor.value = emptyMap()
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
