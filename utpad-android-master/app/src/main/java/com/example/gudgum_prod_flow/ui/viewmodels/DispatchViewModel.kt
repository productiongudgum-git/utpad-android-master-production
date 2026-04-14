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

        // Parse items from invoice's JSON items column (no separate table)
        _invoiceItems.value = invoice.items.map { jsonItem ->
            InvoiceItemDto(
                id = "",
                invoiceId = invoice.id,
                flavorId = jsonItem.flavorId,
                quantityUnits = jsonItem.quantityUnits,
                flavor = FlavorJoinDto(
                    id = jsonItem.flavorId,
                    name = jsonItem.flavorName,
                    code = "",
                ),
            )
        }
    }

    // ── Step 3 ─────────────────────────────────────────────────────

    fun onItemSelected(item: InvoiceItemDto) {
        _selectedItem.value = item
        _fifoLines.value = emptyList()
        _boxesToDispatch.value = (item.quantityUnits / 15).toString()

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

    /** FIFO allocation: sort batches by production_date ASC (oldest first), allocate from expected_boxes */
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

    // ── Navigation ─────────────────────────────────────────────────

    fun nextStep() { if (_currentWizardStep.value < 5) _currentWizardStep.value++ }
    fun previousStep() { if (_currentWizardStep.value > 1) _currentWizardStep.value-- }

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
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
