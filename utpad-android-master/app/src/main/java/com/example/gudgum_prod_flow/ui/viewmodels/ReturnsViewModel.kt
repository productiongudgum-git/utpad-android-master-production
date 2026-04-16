package com.example.gudgum_prod_flow.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.remote.dto.DispatchEventFifoDto
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceDto
import com.example.gudgum_prod_flow.data.repository.ReturnsRepository
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

/**
 * One row in the batch selection list — combines a dispatch_event with the
 * resolved flavor name (resolved from invoice.items).
 * Multiple dispatch_events for the same (batchCode, flavorId) are summed.
 */
data class InvoiceDispatchLine(
    val batchCode: String,
    val flavorId: String?,
    val flavorName: String,
    val boxesDispatched: Int,
)

@HiltViewModel
class ReturnsViewModel @Inject constructor(
    private val repository: ReturnsRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "ReturnsViewModel"
    }

    // ── Step 1: Search ─────────────────────────────────────────────
    private val _invoiceNumberInput = MutableStateFlow("")
    val invoiceNumberInput: StateFlow<String> = _invoiceNumberInput.asStateFlow()

    private val _searchLoading = MutableStateFlow(false)
    val searchLoading: StateFlow<Boolean> = _searchLoading.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _foundInvoice = MutableStateFlow<InvoiceDto?>(null)
    val foundInvoice: StateFlow<InvoiceDto?> = _foundInvoice.asStateFlow()

    // ── Step 2: Batch selection ────────────────────────────────────
    /** Raw dispatch events — kept for total-dispatched calculation in submitReturn. */
    private val _rawDispatchEvents = MutableStateFlow<List<DispatchEventFifoDto>>(emptyList())

    /** Aggregated (batchCode + flavorId) display lines for the selection list. */
    private val _dispatchLines = MutableStateFlow<List<InvoiceDispatchLine>>(emptyList())
    val dispatchLines: StateFlow<List<InvoiceDispatchLine>> = _dispatchLines.asStateFlow()

    private val _selectedLine = MutableStateFlow<InvoiceDispatchLine?>(null)
    val selectedLine: StateFlow<InvoiceDispatchLine?> = _selectedLine.asStateFlow()

    // ── Step 3: Return details ─────────────────────────────────────
    private val _boxesToReturn = MutableStateFlow("")
    val boxesToReturn: StateFlow<String> = _boxesToReturn.asStateFlow()

    private val _returnReason = MutableStateFlow("")
    val returnReason: StateFlow<String> = _returnReason.asStateFlow()

    private val _returnDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
    )
    val returnDate: StateFlow<String> = _returnDate.asStateFlow()

    private val _detailsError = MutableStateFlow<String?>(null)
    val detailsError: StateFlow<String?> = _detailsError.asStateFlow()

    // ── Common ─────────────────────────────────────────────────────
    private val _currentStep = MutableStateFlow(1)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    // ── Step 1 actions ─────────────────────────────────────────────

    fun onInvoiceNumberChanged(value: String) {
        _invoiceNumberInput.value = value
        _searchError.value = null
    }

    fun searchInvoice() {
        val number = _invoiceNumberInput.value.trim()
        if (number.isBlank()) {
            _searchError.value = "Enter an invoice number"
            return
        }
        _searchLoading.value = true
        _searchError.value = null
        _foundInvoice.value = null
        _rawDispatchEvents.value = emptyList()
        _dispatchLines.value = emptyList()
        _selectedLine.value = null

        viewModelScope.launch {
            // Find invoice
            val invoiceResult = repository.searchInvoice(number)
            invoiceResult.onFailure {
                Log.e(TAG, "searchInvoice failed: ${it.message}", it)
                _searchError.value = "Search failed: ${it.message}"
                _searchLoading.value = false
                return@launch
            }

            val invoice = invoiceResult.getOrNull()
            if (invoice == null) {
                _searchError.value = "Invoice not found or not dispatched yet"
                _searchLoading.value = false
                return@launch
            }
            _foundInvoice.value = invoice

            // Fetch dispatch events for this invoice
            val eventsResult = repository.getDispatchEventsForInvoice(invoice.id)
            eventsResult.onSuccess { events ->
                _rawDispatchEvents.value = events

                // Build flavorId → flavorName lookup from invoice.items
                val flavorMap: Map<String, String> = invoice.items
                    .groupBy { it.flavorId }
                    .mapValues { (_, items) -> items.first().flavorName }

                // Aggregate: sum boxesDispatched per (batchCode, flavorId) pair
                _dispatchLines.value = events
                    .groupBy { Pair(it.batchCode, it.flavorId) }
                    .map { (key, group) ->
                        val (batchCode, flavorId) = key
                        InvoiceDispatchLine(
                            batchCode = batchCode,
                            flavorId = flavorId,
                            flavorName = flavorMap[flavorId ?: ""] ?: flavorId ?: "Unknown",
                            boxesDispatched = group.sumOf { it.boxesDispatched },
                        )
                    }
                    .sortedBy { it.batchCode }
            }
            eventsResult.onFailure {
                Log.w(TAG, "getDispatchEventsForInvoice non-fatal: ${it.message}")
            }

            _searchLoading.value = false
            _currentStep.value = 2
        }
    }

    // ── Step 2 actions ─────────────────────────────────────────────

    fun onLineSelected(line: InvoiceDispatchLine) {
        _selectedLine.value = line
        // Pre-fill boxes with the full dispatched amount
        _boxesToReturn.value = line.boxesDispatched.toString()
        _detailsError.value = null
    }

    fun nextFromSelectBatch() {
        if (_selectedLine.value == null) return
        _currentStep.value = 3
    }

    // ── Step 3 actions ─────────────────────────────────────────────

    fun onBoxesToReturnChanged(value: String) {
        _boxesToReturn.value = value
        _detailsError.value = null
    }

    fun onReturnReasonChanged(value: String) {
        _returnReason.value = value
    }

    fun onReturnDateChanged(value: String) {
        _returnDate.value = value
    }

    /** Validates step 3 and advances to step 4. Returns false if validation fails. */
    fun nextFromDetails(): Boolean {
        val boxes = _boxesToReturn.value.toIntOrNull()
        val max = _selectedLine.value?.boxesDispatched ?: 0
        return when {
            boxes == null || boxes <= 0 -> {
                _detailsError.value = "Enter a valid number of boxes (must be > 0)"
                false
            }
            boxes > max -> {
                _detailsError.value = "Cannot return more than $max boxes (dispatched from this batch)"
                false
            }
            else -> {
                _detailsError.value = null
                _currentStep.value = 4
                true
            }
        }
    }

    // ── Step 4: Submit ─────────────────────────────────────────────

    fun submit() {
        if (_submitState.value is SubmitState.Loading) return
        val invoice = _foundInvoice.value ?: return
        val line = _selectedLine.value ?: return
        val boxes = _boxesToReturn.value.toIntOrNull() ?: return

        Log.d(TAG, "submit() — invoice=${invoice.invoiceNumber}, batch=${line.batchCode}, boxes=$boxes")
        _submitState.value = SubmitState.Loading

        viewModelScope.launch {
            val result = repository.submitReturn(
                invoice = invoice,
                batchCode = line.batchCode,
                flavorId = line.flavorId,
                boxesToReturn = boxes,
                reason = _returnReason.value,
                returnDate = _returnDate.value,
                allDispatchEvents = _rawDispatchEvents.value,
                workerId = WorkerIdentityStore.workerId,
            )
            result.onSuccess {
                Log.d(TAG, "submit success for invoice ${invoice.invoiceNumber}")
                _submitState.value = SubmitState.Success(
                    "Return of $boxes boxes recorded for invoice ${invoice.invoiceNumber}",
                )
            }
            result.onFailure { e ->
                Log.e(TAG, "submit failed: ${e.message}", e)
                _submitState.value = SubmitState.Error(e.message ?: "Submit failed")
            }
        }
    }

    // ── Navigation ─────────────────────────────────────────────────

    fun previousStep() {
        val step = _currentStep.value
        if (step > 1) _currentStep.value = step - 1
    }

    fun reset() {
        _invoiceNumberInput.value = ""
        _searchError.value = null
        _foundInvoice.value = null
        _rawDispatchEvents.value = emptyList()
        _dispatchLines.value = emptyList()
        _selectedLine.value = null
        _boxesToReturn.value = ""
        _returnReason.value = ""
        _returnDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _detailsError.value = null
        _submitState.value = SubmitState.Idle
        _currentStep.value = 1
    }

    fun clearSubmitState() {
        _submitState.value = SubmitState.Idle
    }
}
