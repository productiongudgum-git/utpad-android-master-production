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

/** A display-friendly FIFO allocation row */
data class FifoDisplayLine(
    val inventoryId: String,
    val batchCode: String,
    val availableUnits: Int,
    val unitsToTake: Int,
)

@HiltViewModel
class DispatchViewModel @Inject constructor(
    private val repository: DispatchRepository,
    private val realtimeManager: SupabaseRealtimeManager,
) : ViewModel() {

    companion object { private const val TAG = "DispatchViewModel" }

    // ── Step 1: Invoice selection ──────────────────────────────────
    private val _invoices = MutableStateFlow<List<InvoiceDto>>(emptyList())
    val invoices: StateFlow<List<InvoiceDto>> = _invoices.asStateFlow()

    private val _invoicesLoading = MutableStateFlow(false)
    val invoicesLoading: StateFlow<Boolean> = _invoicesLoading.asStateFlow()

    private val _selectedInvoice = MutableStateFlow<InvoiceDto?>(null)
    val selectedInvoice: StateFlow<InvoiceDto?> = _selectedInvoice.asStateFlow()

    // ── Step 2: Invoice items (auto-populated) ─────────────────────
    private val _invoiceItems = MutableStateFlow<List<InvoiceItemDto>>(emptyList())
    val invoiceItems: StateFlow<List<InvoiceItemDto>> = _invoiceItems.asStateFlow()

    // ── Step 3: Flavour selection ──────────────────────────────────
    private val _selectedItem = MutableStateFlow<InvoiceItemDto?>(null)
    val selectedItem: StateFlow<InvoiceItemDto?> = _selectedItem.asStateFlow()

    // ── Step 4: Boxes + FIFO allocation ────────────────────────────
    private val _boxesToDispatch = MutableStateFlow("")
    val boxesToDispatch: StateFlow<String> = _boxesToDispatch.asStateFlow()

    private val _fifoLines = MutableStateFlow<List<FifoDisplayLine>>(emptyList())
    val fifoLines: StateFlow<List<FifoDisplayLine>> = _fifoLines.asStateFlow()

    private val _inventoryForFlavor = MutableStateFlow<List<ProductionBatchFifoDto>>(emptyList())

    private val _fifoError = MutableStateFlow<String?>(null)
    val fifoError: StateFlow<String?> = _fifoError.asStateFlow()

    // ── Step 5: Confirm ────────────────────────────────────────────
    private val _isPacked = MutableStateFlow(false)
    val isPacked: StateFlow<Boolean> = _isPacked.asStateFlow()

    private val _isDispatched = MutableStateFlow(false)
    val isDispatched: StateFlow<Boolean> = _isDispatched.asStateFlow()

    private val _dispatchDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val dispatchDate: StateFlow<String> = _dispatchDate.asStateFlow()

    // ── FIX 1: pre-packed detection — skip steps 2-4 ───────────────
    private val _invoiceAlreadyPacked = MutableStateFlow(false)
    val invoiceAlreadyPacked: StateFlow<Boolean> = _invoiceAlreadyPacked.asStateFlow()

    // ── FIX 1: already-dispatched boxes per flavor for this invoice (delta calc) ──
    private val _alreadyDispatchedPerFlavor = MutableStateFlow<Map<String, Int>>(emptyMap())

    // ── FIX 2: stock availability check before Step 5 submit ───────
    private val _stockCheckError = MutableStateFlow<String?>(null)
    val stockCheckError: StateFlow<String?> = _stockCheckError.asStateFlow()

    private val _stockCheckLoading = MutableStateFlow(false)
    val stockCheckLoading: StateFlow<Boolean> = _stockCheckLoading.asStateFlow()

    // ── Tracking table: all invoices for status view ───────────────
    private val _allInvoices = MutableStateFlow<List<InvoiceDto>>(emptyList())
    val allInvoices: StateFlow<List<InvoiceDto>> = _allInvoices.asStateFlow()

    // ── Common ─────────────────────────────────────────────────────
    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

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
            val result = repository.getActiveInvoices()
            result.onSuccess { list ->
                _invoices.value = list
                _allInvoices.value = list
            }
            _invoicesLoading.value = false
        }
    }

    // ── Step 1 ─────────────────────────────────────────────────────

    fun onInvoiceSelected(invoice: InvoiceDto) {
        _selectedInvoice.value = invoice
        _selectedItem.value = null
        _fifoLines.value = emptyList()
        _boxesToDispatch.value = ""
        _stockCheckError.value = null

        // FIX 2: merge duplicate flavors by summing their boxes before building item list
        _invoiceItems.value = invoice.items
            .groupBy { it.flavorId }
            .map { (flavorId, group) ->
                InvoiceItemDto(
                    id = "",
                    invoiceId = invoice.id,
                    flavorId = flavorId,
                    quantityUnits = group.sumOf { it.quantityUnits },
                    quantityBoxes = group.mapNotNull { it.quantityBoxes }.takeIf { it.isNotEmpty() }?.sum(),
                    flavor = FlavorJoinDto(
                        id = flavorId,
                        name = group.first().flavorName,
                        code = "",
                    ),
                )
            }

        // FIX 1: if already packed, pre-tick the checkbox so Step 5 validation passes
        _invoiceAlreadyPacked.value = invoice.isPacked
        _isPacked.value = invoice.isPacked

        // FIX 1: fetch already-dispatched boxes per flavor so delta can be computed in onItemSelected()
        _alreadyDispatchedPerFlavor.value = emptyMap()
        viewModelScope.launch {
            repository.getAlreadyDispatchedPerFlavor(invoice.id)
                .onSuccess { map -> _alreadyDispatchedPerFlavor.value = map }
                .onFailure { Log.w(TAG, "getAlreadyDispatchedPerFlavor failed: ${it.message}") }
        }
    }

    // ── Step 3 ─────────────────────────────────────────────────────

    fun onItemSelected(item: InvoiceItemDto) {
        _selectedItem.value = item
        _fifoLines.value = emptyList()

        // FIX 1: only allocate FIFO for the delta (new boxes beyond what's already dispatched)
        val alreadyDispatched = _alreadyDispatchedPerFlavor.value[item.flavorId] ?: 0
        val delta = maxOf(0, item.resolvedBoxes - alreadyDispatched)
        _boxesToDispatch.value = delta.toString()

        // Load inventory for this flavor
        viewModelScope.launch {
            val result = repository.getInventoryByFlavor(item.flavorId)
            result.onSuccess { inventory ->
                _inventoryForFlavor.value = inventory
                computeFifo()
            }
            result.onFailure {
                _inventoryForFlavor.value = emptyList()
                _fifoError.value = "Could not load inventory for this flavour"
            }
        }
    }

    // ── Step 4 ─────────────────────────────────────────────────────

    fun onBoxesChanged(value: String) {
        _boxesToDispatch.value = value
        computeFifo()
    }

    /** FIFO allocation: sort batches by oldest packing session_date ASC, allocate from available boxes (packed - dispatched) */
    private fun computeFifo() {
        val needed = _boxesToDispatch.value.toIntOrNull() ?: 0
        if (needed <= 0) {
            _fifoLines.value = emptyList()
            _fifoError.value = null
            return
        }

        val inventory = _inventoryForFlavor.value.sortedBy { it.productionDate }
        val lines = mutableListOf<FifoDisplayLine>()
        var remaining = needed

        for (item in inventory) {
            if (remaining <= 0) break
            val take = minOf(remaining, item.expectedBoxes)
            lines.add(
                FifoDisplayLine(
                    inventoryId = item.id,
                    batchCode = item.batchCode,
                    availableUnits = item.expectedBoxes,
                    unitsToTake = take,
                )
            )
            remaining -= take
        }

        _fifoLines.value = lines
        _fifoError.value = if (remaining > 0) {
            "Insufficient stock! Short by $remaining boxes."
        } else {
            null
        }
    }

    // ── Step 5 ─────────────────────────────────────────────────────

    fun onPackedToggle(value: Boolean) {
        _isPacked.value = value
        if (!value) _isDispatched.value = false
    }
    fun onDispatchedToggle(value: Boolean) { _isDispatched.value = value }
    fun onDispatchDateChanged(value: String) { _dispatchDate.value = value }

    // ── FIX 2: stock availability check ────────────────────────────

    /** Checks total available stock vs total required boxes for all items in the invoice. */
    private fun checkStockForInvoice() {
        val items = _invoiceItems.value
        if (items.isEmpty()) {
            _stockCheckError.value = null
            return
        }
        _stockCheckLoading.value = true
        _stockCheckError.value = null
        viewModelScope.launch {
            var totalRequired = 0
            var totalAvailable = 0
            var fetchFailed = false
            for (item in items) {
                // FIX 1: require only the delta, not the full invoice amount
                val alreadyDispatched = _alreadyDispatchedPerFlavor.value[item.flavorId] ?: 0
                totalRequired += maxOf(0, item.resolvedBoxes - alreadyDispatched)
                repository.getInventoryByFlavor(item.flavorId)
                    .onSuccess { inventory -> totalAvailable += inventory.sumOf { it.expectedBoxes } }
                    .onFailure { fetchFailed = true }
            }
            _stockCheckLoading.value = false
            _stockCheckError.value = when {
                fetchFailed -> "Could not verify stock availability. Please go back and try again."
                totalAvailable < totalRequired ->
                    "Insufficient stock. Required: $totalRequired boxes, Available: $totalAvailable boxes. Cannot dispatch until stock is available."
                else -> null
            }
        }
    }

    // ── Navigation ─────────────────────────────────────────────────

    fun nextStep() {
        val step = _currentWizardStep.value
        when {
            // FIX 1: invoice already packed — jump from Step 1 directly to Step 5
            // Stock was verified at packing time; skip the check entirely.
            step == 1 && _invoiceAlreadyPacked.value -> {
                _currentWizardStep.value = 5
            }
            step < 5 -> {
                _currentWizardStep.value = step + 1
                // FIX 2: trigger stock check only for the normal (unpacked) FIFO flow
                if (step + 1 == 5 && !_invoiceAlreadyPacked.value) checkStockForInvoice()
            }
        }
    }

    fun previousStep() {
        val step = _currentWizardStep.value
        when {
            // FIX 1: pre-packed invoice — Back from Step 5 returns to Step 1
            step == 5 && _invoiceAlreadyPacked.value -> _currentWizardStep.value = 1
            step > 1 -> _currentWizardStep.value = step - 1
        }
    }

    // ── Submit ─────────────────────────────────────────────────────

    fun submit() {
        if (_submitState.value is SubmitState.Loading) return

        val invoice = _selectedInvoice.value
        if (invoice == null) {
            Log.d(TAG, "submit() blocked: no invoice selected")
            _submitState.value = SubmitState.Error("Select an invoice")
            return
        }
        // Validate against the wizard checkbox (_isPacked), not the stale DB value (invoice.isPacked)
        if (!_isPacked.value) {
            Log.d(TAG, "submit() blocked: isPacked checkbox is false for invoice ${invoice.invoiceNumber}")
            _submitState.value = SubmitState.Error("This invoice has not been packed yet. Please mark it as packed before dispatching.")
            return
        }
        // FIX 2: block if stock check found insufficient stock (normal flow only)
        // Pre-packed invoices skip the stock check — stock was verified at packing time.
        if (!_invoiceAlreadyPacked.value) {
            val stockErr = _stockCheckError.value
            if (stockErr != null) {
                Log.d(TAG, "submit() blocked: stockCheckError = $stockErr")
                _submitState.value = SubmitState.Error(stockErr)
                return
            }
        }

        // FIX 1: pre-packed path — steps 2-4 were skipped, just update dispatch status
        if (_invoiceAlreadyPacked.value) {
            Log.d(TAG, "submit() pre-packed path — invoice=${invoice.invoiceNumber}, isDispatched=${_isDispatched.value}")
            _submitState.value = SubmitState.Loading
            viewModelScope.launch {
                val result = repository.updateInvoiceStatus(
                    invoiceId = invoice.id,
                    isPacked = true,
                    isDispatched = _isDispatched.value,
                )
                result.onSuccess {
                    Log.d(TAG, "submit() pre-packed success for ${invoice.invoiceNumber}")
                    _submitState.value = SubmitState.Success("Invoice ${invoice.invoiceNumber} dispatched")
                    reset()
                    loadInvoices()
                }
                result.onFailure { e ->
                    Log.e(TAG, "submit() pre-packed failed — ${e::class.simpleName}: ${e.message}", e)
                    _submitState.value = SubmitState.Error(e.message ?: "Dispatch failed")
                }
            }
            return
        }

        // Normal FIFO path
        val item = _selectedItem.value
        if (item == null) {
            Log.d(TAG, "submit() blocked: no flavour selected")
            _submitState.value = SubmitState.Error("Select a flavour")
            return
        }
        val lines = _fifoLines.value
        if (lines.isEmpty()) {
            Log.d(TAG, "submit() blocked: fifoLines is empty — inventory may not have loaded yet")
            _submitState.value = SubmitState.Error("No FIFO allocation computed")
            return
        }
        if (_fifoError.value != null) {
            Log.d(TAG, "submit() blocked: fifoError = ${_fifoError.value}")
            _submitState.value = SubmitState.Error(_fifoError.value!!)
            return
        }

        Log.d(TAG, "submit() starting — invoice=${invoice.invoiceNumber}, flavour=${item.flavorId}, " +
                "boxes=${_boxesToDispatch.value}, lines=${lines.size}, " +
                "isPacked=${_isPacked.value}, isDispatched=${_isDispatched.value}, " +
                "workerId=${WorkerIdentityStore.workerId}, online=$isOnline")

        _submitState.value = SubmitState.Loading
        viewModelScope.launch {
            val allocations = lines.map { line ->
                FifoAllocation(
                    inventoryId = line.inventoryId,
                    batchCode = line.batchCode,
                    availableUnits = line.availableUnits,
                    unitsToTake = line.unitsToTake,
                )
            }
            Log.d(TAG, "submit() allocations: ${allocations.map { "${it.batchCode}=${it.unitsToTake}boxes" }}")

            val result = repository.submitFifoDispatch(
                invoiceId = invoice.id,
                invoiceNumber = invoice.invoiceNumber,
                customerName = invoice.customerName,
                flavorId = item.flavorId,
                allocations = allocations,
                isPacked = _isPacked.value,
                isDispatched = _isDispatched.value,
                dispatchDate = _dispatchDate.value,
                workerId = WorkerIdentityStore.workerId,
                isOnline = isOnline,
            )

            result.onSuccess {
                val totalBoxes = lines.sumOf { it.unitsToTake }
                Log.d(TAG, "submit() success — $totalBoxes boxes dispatched for ${invoice.invoiceNumber}")
                _submitState.value = SubmitState.Success(
                    if (isOnline) "Dispatched $totalBoxes boxes for invoice ${invoice.invoiceNumber}"
                    else "Dispatch saved offline — will sync when connected"
                )
                reset()
                loadInvoices()
            }
            result.onFailure { e ->
                Log.e(TAG, "submit() failed — ${e::class.simpleName}: ${e.message}", e)
                _submitState.value = SubmitState.Error(e.message ?: "Dispatch failed")
            }
        }
    }

    // ── Tracking: toggle packed/dispatched from the table ──────────

    fun toggleInvoicePacked(invoice: InvoiceDto) {
        viewModelScope.launch {
            val newValue = !invoice.isPacked
            repository.updateInvoiceStatus(invoice.id, isPacked = newValue)
            loadInvoices()
        }
    }

    fun toggleInvoiceDispatched(invoice: InvoiceDto) {
        viewModelScope.launch {
            val newValue = !invoice.isDispatched
            repository.updateInvoiceStatus(invoice.id, isDispatched = newValue)
            loadInvoices()
        }
    }

    fun reset() {
        _selectedInvoice.value = null
        _invoiceItems.value = emptyList()
        _selectedItem.value = null
        _boxesToDispatch.value = ""
        _fifoLines.value = emptyList()
        _fifoError.value = null
        _inventoryForFlavor.value = emptyList()
        _isPacked.value = false
        _isDispatched.value = false
        _dispatchDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
        _invoiceAlreadyPacked.value = false
        _alreadyDispatchedPerFlavor.value = emptyMap()
        _stockCheckError.value = null
        _stockCheckLoading.value = false
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
