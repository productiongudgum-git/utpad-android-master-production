package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.remote.dto.GgCustomerDto
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchDto
import com.example.gudgum_prod_flow.data.repository.DispatchRepository
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import com.example.gudgum_prod_flow.util.BatchCodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Represents a batch+flavor pair for dispatch selection */
data class DispatchBatchOption(
    val batchCode: String,
    val flavorId: String?,
    val flavorName: String?,
    val productionDate: String,
) {
    val displayLabel: String
        get() = if (flavorName != null) "$batchCode — $flavorName" else batchCode
}

@HiltViewModel
class DispatchViewModel @Inject constructor(
    private val repository: DispatchRepository,
    private val realtimeManager: com.example.gudgum_prod_flow.data.remote.SupabaseRealtimeManager,
) : ViewModel() {

    // Batch+flavor options from production batches
    private val _batchOptions = MutableStateFlow<List<DispatchBatchOption>>(emptyList())
    val batchOptions: StateFlow<List<DispatchBatchOption>> = _batchOptions.asStateFlow()

    // Legacy for backward compat
    private val _batchCodes = MutableStateFlow<List<String>>(emptyList())
    val batchCodes: StateFlow<List<String>> = _batchCodes.asStateFlow()

    private val _batchCodesLoading = MutableStateFlow(false)
    val batchCodesLoading: StateFlow<Boolean> = _batchCodesLoading.asStateFlow()

    private val _batchCode = MutableStateFlow("")
    val batchCode: StateFlow<String> = _batchCode.asStateFlow()

    private val _selectedBatchOption = MutableStateFlow<DispatchBatchOption?>(null)
    val selectedBatchOption: StateFlow<DispatchBatchOption?> = _selectedBatchOption.asStateFlow()

    private val _qtyDispatched = MutableStateFlow("")
    val qtyDispatched: StateFlow<String> = _qtyDispatched.asStateFlow()

    private val _invoiceNumber = MutableStateFlow("")
    val invoiceNumber: StateFlow<String> = _invoiceNumber.asStateFlow()

    private val _dispatchDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val dispatchDate: StateFlow<String> = _dispatchDate.asStateFlow()

    private val _customers = MutableStateFlow<List<GgCustomerDto>>(emptyList())
    val customers: StateFlow<List<GgCustomerDto>> = _customers.asStateFlow()

    private val _selectedCustomerId = MutableStateFlow("")
    val selectedCustomerId: StateFlow<String> = _selectedCustomerId.asStateFlow()

    private val _selectedCustomerName = MutableStateFlow("")
    val selectedCustomerName: StateFlow<String> = _selectedCustomerName.asStateFlow()

    private val _customersLoading = MutableStateFlow(false)
    val customersLoading: StateFlow<Boolean> = _customersLoading.asStateFlow()

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    private var isOnline: Boolean = true

    init {
        loadBatchCodes()
        loadCustomers()

        realtimeManager.connect()
        viewModelScope.launch {
            realtimeManager.tableChanged.collect { table ->
                when (table) {
                    "production_batches", "dispatch_events" -> loadBatchCodes()
                    "gg_customers" -> loadCustomers()
                }
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
                DispatchBatchOption(
                    batchCode = batch.batchCode,
                    flavorId = batch.flavorId,
                    flavorName = batch.flavor?.name,
                    productionDate = batch.productionDate,
                )
            }
            _batchOptions.value = options
            _batchCodes.value = options.map { it.batchCode }.distinct()

            if (_selectedBatchOption.value == null && options.isNotEmpty()) {
                onBatchOptionSelected(options.first())
            }

            _batchCodesLoading.value = false
        }
    }

    fun loadCustomers() {
        viewModelScope.launch {
            _customersLoading.value = true
            val result = repository.getCustomers()
            result.onSuccess { list -> _customers.value = list }
            _customersLoading.value = false
        }
    }

    fun onBatchOptionSelected(option: DispatchBatchOption) {
        _selectedBatchOption.value = option
        _batchCode.value = option.batchCode
    }

    fun onBatchCodeSelected(code: String) {
        _batchCode.value = code
        val option = _batchOptions.value.firstOrNull { it.batchCode == code }
        _selectedBatchOption.value = option
    }

    fun onQtyDispatchedChanged(value: String) { _qtyDispatched.value = value }
    fun onInvoiceNumberChanged(value: String) { _invoiceNumber.value = value }
    fun onDispatchDateChanged(value: String) { _dispatchDate.value = value }

    fun onCustomerSelected(id: String, name: String) {
        _selectedCustomerId.value = id
        _selectedCustomerName.value = name
    }

    fun nextStep() { if (_currentWizardStep.value < 3) _currentWizardStep.value++ }
    fun previousStep() { if (_currentWizardStep.value > 1) _currentWizardStep.value-- }

    fun submit() {
        val selected = _selectedBatchOption.value
        val code = selected?.batchCode ?: _batchCode.value.trim()
        if (code.isBlank()) {
            _submitState.value = SubmitState.Error("Select a batch code")
            return
        }
        val qty = _qtyDispatched.value.toIntOrNull()
        if (qty == null || qty <= 0) {
            _submitState.value = SubmitState.Error("Enter a valid number of boxes (> 0)")
            return
        }
        val customerName = _selectedCustomerName.value
        if (customerName.isBlank()) {
            _submitState.value = SubmitState.Error("Select a customer")
            return
        }
        val invoice = _invoiceNumber.value.trim().ifBlank {
            // Auto-generate invoice number if not provided
            "INV-${code}-${_dispatchDate.value.replace("-", "")}"
        }

        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val result = repository.submitDispatch(
                batchCode = code,
                skuId = selected?.flavorId ?: "",
                customerName = customerName,
                invoiceNumber = invoice,
                quantityDispatched = qty,
                dispatchDate = _dispatchDate.value,
                workerId = WorkerIdentityStore.workerId,
                isOnline = isOnline,
            )
            result.onSuccess {
                _submitState.value = SubmitState.Success(
                    if (isOnline) "Dispatched $qty boxes for batch $code to $customerName"
                    else "Dispatch saved offline — will sync when connected"
                )
                reset()
            }
            result.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "Dispatch failed")
            }
        }
    }

    fun reset() {
        _selectedBatchOption.value = _batchOptions.value.firstOrNull()
        _batchCode.value = _selectedBatchOption.value?.batchCode ?: ""
        _qtyDispatched.value = ""
        _invoiceNumber.value = ""
        _dispatchDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _selectedCustomerId.value = ""
        _selectedCustomerName.value = ""
        _submitState.value = SubmitState.Idle
        _currentWizardStep.value = 1
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
