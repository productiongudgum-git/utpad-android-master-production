package com.example.gudgum_prod_flow.ui.viewmodels

import android.app.Application
import android.util.Log
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.local.entity.CachedIngredientEntity
import com.example.gudgum_prod_flow.data.remote.dto.GgInwardingRequest
import com.example.gudgum_prod_flow.data.remote.dto.SubmitReturnEventRequest
import com.example.gudgum_prod_flow.data.remote.SupabaseRealtimeManager
import com.example.gudgum_prod_flow.data.remote.dto.DispatchedBatchDto
import com.example.gudgum_prod_flow.data.repository.DispatchRepository
import com.example.gudgum_prod_flow.data.repository.InwardingRepository
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

data class Vendor(val id: String, val name: String)

@HiltViewModel
class InwardingViewModel @Inject constructor(
    application: Application,
    private val repository: InwardingRepository,
    private val dispatchRepository: DispatchRepository,
    private val realtimeManager: SupabaseRealtimeManager,
) : AndroidViewModel(application) {
    private companion object {
        const val TAG = "InwardingViewModel"
    }

    private val _ingredients = MutableStateFlow<List<CachedIngredientEntity>>(emptyList())
    val ingredients: StateFlow<List<CachedIngredientEntity>> = _ingredients.asStateFlow()

    private val _selectedIngredient = MutableStateFlow<CachedIngredientEntity?>(null)
    val selectedIngredient: StateFlow<CachedIngredientEntity?> = _selectedIngredient.asStateFlow()

    private val _quantity = MutableStateFlow("")
    val quantity: StateFlow<String> = _quantity.asStateFlow()

    // Unit is auto-filled from selected ingredient's configured unit
    private val _selectedUnit = MutableStateFlow("kg")
    val selectedUnit: StateFlow<String> = _selectedUnit.asStateFlow()

    private val _inwardDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val inwardDate: StateFlow<String> = _inwardDate.asStateFlow()

    private val _expiryDate = MutableStateFlow("")
    val expiryDate: StateFlow<String> = _expiryDate.asStateFlow()

    private val _supplier = MutableStateFlow("")
    val supplier: StateFlow<String> = _supplier.asStateFlow()

    // Aliases expected by InwardingScreen
    val availableIngredients: StateFlow<List<CachedIngredientEntity>> = _ingredients

    private val _selectedVendor = MutableStateFlow<Vendor?>(null)
    val selectedVendor: StateFlow<Vendor?> = _selectedVendor.asStateFlow()

    private val _billNumber = MutableStateFlow("")
    val billNumber: StateFlow<String> = _billNumber.asStateFlow()

    private val _billPhotoUri = MutableStateFlow<String?>(null)
    val billPhotoUri: StateFlow<String?> = _billPhotoUri.asStateFlow()

    private val _vendors = MutableStateFlow<List<Vendor>>(emptyList())
    val vendors: StateFlow<List<Vendor>> = _vendors.asStateFlow()

    private val _addIngredientState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val addIngredientState: StateFlow<SubmitState> = _addIngredientState.asStateFlow()

    private val _addVendorState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val addVendorState: StateFlow<SubmitState> = _addVendorState.asStateFlow()

    val units: List<String> = listOf("kg", "L", "g", "ml", "pcs")

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _uploadingPhoto = MutableStateFlow(false)
    val uploadingPhoto: StateFlow<Boolean> = _uploadingPhoto.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    // Returns sub-screen state
    private val _returnsMode = MutableStateFlow(false)
    val returnsMode: StateFlow<Boolean> = _returnsMode.asStateFlow()

    private val _dispatchedBatches = MutableStateFlow<List<DispatchedBatchDto>>(emptyList())
    val dispatchedBatches: StateFlow<List<DispatchedBatchDto>> = _dispatchedBatches.asStateFlow()

    private val _selectedReturnBatch = MutableStateFlow("")
    val selectedReturnBatch: StateFlow<String> = _selectedReturnBatch.asStateFlow()

    private val _selectedReturnSkuId = MutableStateFlow("")
    val selectedReturnSkuId: StateFlow<String> = _selectedReturnSkuId.asStateFlow()

    private val _returnQty = MutableStateFlow("")
    val returnQty: StateFlow<String> = _returnQty.asStateFlow()

    private val _returnReason = MutableStateFlow("")
    val returnReason: StateFlow<String> = _returnReason.asStateFlow()

    private var isOnline: Boolean = true

    init {
        viewModelScope.launch {
            repository.getActiveIngredients().collect { list ->
                _ingredients.value = list
            }
        }
        viewModelScope.launch {
            val vendors = repository.getVendors()
            _vendors.value = vendors.map { Vendor(id = it.id, name = it.name) }
        }
        // Eagerly refresh from Supabase gg_* tables so dashboard data appears
        viewModelScope.launch {
            repository.refreshIngredients()
        }

        // --- Supabase Realtime: auto-refresh when dashboard makes changes ---
        realtimeManager.connect()
        viewModelScope.launch {
            realtimeManager.tableChanged.collect { table ->
                when (table) {
                    "gg_ingredients" -> {
                        repository.refreshIngredients()
                    }
                    "gg_vendors" -> {
                        val vendors = repository.getVendors()
                        _vendors.value = vendors.map { Vendor(id = it.id, name = it.name) }
                    }
                    "dispatch_events" -> {
                        if (isOnline) {
                            dispatchRepository.getDispatchedBatches().onSuccess { batches ->
                                _dispatchedBatches.value = batches
                            }
                        }
                    }
                }
            }
        }
    }

    fun setOnlineStatus(online: Boolean) { isOnline = online }

    fun refreshData() {
        viewModelScope.launch {
            repository.refreshIngredients()
            val vendors = repository.getVendors()
            _vendors.value = vendors.map { Vendor(id = it.id, name = it.name) }
            if (isOnline) {
                dispatchRepository.getDispatchedBatches().onSuccess { batches ->
                    _dispatchedBatches.value = batches
                }
            }
        }
    }

    fun setBillPhotoUri(uri: String?) { _billPhotoUri.value = uri }

    fun addIngredient(name: String, unit: String, vendorName: String) {
        viewModelScope.launch {
            _addIngredientState.value = SubmitState.Loading
            // Resolve vendor: use existing if name matches, else create a new one
            val existing = _vendors.value.firstOrNull { it.name.equals(vendorName.trim(), ignoreCase = true) }
            val vendorId: String
            val supplierName: String
            if (existing != null) {
                vendorId = existing.id
                supplierName = existing.name
            } else if (vendorName.isNotBlank()) {
                val result = repository.createVendor(vendorName.trim())
                if (result.isFailure) {
                    _addIngredientState.value = SubmitState.Error(
                        result.exceptionOrNull()?.message ?: "Failed to create vendor"
                    )
                    return@launch
                }
                val dto = result.getOrThrow()
                val newVendor = Vendor(id = dto.id, name = dto.name)
                _vendors.value = _vendors.value + newVendor
                vendorId = dto.id
                supplierName = dto.name
            } else {
                _addIngredientState.value = SubmitState.Error("Please specify a vendor")
                return@launch
            }

            repository.createIngredient(name.trim(), unit, vendorId)
                .onSuccess { entity ->
                    onIngredientSelected(entity)
                    onVendorSelected(Vendor(id = vendorId, name = supplierName))
                    _addIngredientState.value = SubmitState.Success("\"${entity.name}\" added")
                }
                .onFailure { e ->
                    _addIngredientState.value = SubmitState.Error(e.message ?: "Failed to add ingredient")
                }
        }
    }

    fun addVendor(name: String, contact: String? = null) {
        viewModelScope.launch {
            _addVendorState.value = SubmitState.Loading
            repository.createVendor(name.trim(), contact?.ifBlank { null })
                .onSuccess { dto ->
                    val newVendor = Vendor(id = dto.id, name = dto.name)
                    _vendors.value = _vendors.value + newVendor
                    onVendorSelected(newVendor)
                    _addVendorState.value = SubmitState.Success("\"${dto.name}\" added")
                }
                .onFailure { e ->
                    _addVendorState.value = SubmitState.Error(e.message ?: "Failed to add vendor")
                }
        }
    }

    fun clearAddIngredientState() { _addIngredientState.value = SubmitState.Idle }
    fun clearAddVendorState() { _addVendorState.value = SubmitState.Idle }

    fun onIngredientSelected(ingredient: CachedIngredientEntity) {
        _selectedIngredient.value = ingredient
        _selectedUnit.value = ingredient.unit // Auto-fill unit from ingredient master
    }

    fun onQuantityChanged(value: String) { _quantity.value = value }
    fun onInwardDateChanged(value: String) { _inwardDate.value = value }
    fun onExpiryDateChanged(value: String) { _expiryDate.value = value }
    fun onSupplierChanged(value: String) { _supplier.value = value }

    fun onVendorSelected(vendor: Vendor) {
        _selectedVendor.value = vendor
        _supplier.value = vendor.name
    }
    fun onUnitSelected(unit: String) { _selectedUnit.value = unit }
    fun onBillNumberChanged(value: String) { _billNumber.value = value }
    fun submit() = submitInward()

    fun nextStep() { if (_currentWizardStep.value < 3) _currentWizardStep.value++ }
    fun previousStep() { if (_currentWizardStep.value > 1) _currentWizardStep.value-- }

    // Switch between inward mode and returns mode
    fun toggleReturnsMode(active: Boolean) { _returnsMode.value = active }
    fun onReturnBatchSelected(batchCode: String, skuId: String) {
        _selectedReturnBatch.value = batchCode
        _selectedReturnSkuId.value = skuId
    }
    fun onReturnQtyChanged(value: String) { _returnQty.value = value }
    fun onReturnReasonChanged(value: String) { _returnReason.value = value }

    fun submitInward() {
        val ingredient = _selectedIngredient.value ?: run {
            _submitState.value = SubmitState.Error("Select an ingredient")
            return
        }
        val qty = _quantity.value.toDoubleOrNull()
        if (qty == null || qty <= 0) {
            _submitState.value = SubmitState.Error("Enter a valid quantity")
            return
        }

        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val vendor = _selectedVendor.value
            if (vendor == null) {
                _submitState.value = SubmitState.Error("Select a vendor")
                return@launch
            }

            // If the photo URI is a local content:// or file:// URI, upload it first.
            val rawUri = _billPhotoUri.value
            val photoUrl: String? = if (rawUri != null && !rawUri.startsWith("http")) {
                _uploadingPhoto.value = true
                val uploadResult = repository.uploadBillPhoto(getApplication(), Uri.parse(rawUri))
                _uploadingPhoto.value = false
                uploadResult.getOrElse { e ->
                    Log.w(TAG, "Bill photo upload failed; continuing without photo", e)
                    null
                }
            } else {
                rawUri
            }

            val result = repository.submitInwardEvent(
                request = GgInwardingRequest(
                    ingredientId = ingredient.id,
                    qty = qty,
                    unit = _selectedUnit.value,
                    vendorId = vendor.id,
                    inwardDate = _inwardDate.value,
                    expiryDate = _expiryDate.value.ifBlank { null },
                    lotRef = _billNumber.value.ifBlank { null },
                    workerId = WorkerIdentityStore.workerId,
                ),
                isOnline = isOnline,
            )
            result.onSuccess {
                resetInward()
                _submitState.value = SubmitState.Success(
                    if (isOnline) "${ingredient.name}: $qty ${_selectedUnit.value} inwarded"
                    else "Inward saved offline — will sync when connected"
                )
            }
            result.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "Submission failed")
            }
        }
    }

    fun submitReturn() {
        if (_selectedReturnBatch.value.isBlank()) {
            _submitState.value = SubmitState.Error("Select a batch")
            return
        }
        val qty = _returnQty.value.toIntOrNull()
        if (qty == null || qty <= 0) {
            _submitState.value = SubmitState.Error("Enter a valid quantity")
            return
        }

        viewModelScope.launch {
            _submitState.value = SubmitState.Loading
            val result = repository.submitReturnEvent(
                request = SubmitReturnEventRequest(
                    batchCode = _selectedReturnBatch.value,
                    skuId = _selectedReturnSkuId.value,
                    qtyReturned = qty,
                    reason = _returnReason.value.ifBlank { null },
                    returnDate = _inwardDate.value,
                    workerId = WorkerIdentityStore.workerId,
                ),
                isOnline = isOnline,
            )
            result.onSuccess {
                resetReturn()
                _submitState.value = SubmitState.Success("Return of $qty boxes logged for batch ${_selectedReturnBatch.value}")
            }
            result.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "Return submission failed")
            }
        }
    }

    fun resetInward() {
        _selectedIngredient.value = null
        _selectedVendor.value = null
        _quantity.value = ""
        _selectedUnit.value = "kg"
        _inwardDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        _expiryDate.value = ""
        _supplier.value = ""
        _billNumber.value = ""
        _billPhotoUri.value = null
        // Do NOT reset _submitState here — clearSubmitState() handles that after the overlay dismisses
        _currentWizardStep.value = 1
    }

    fun resetReturn() {
        _selectedReturnBatch.value = ""
        _selectedReturnSkuId.value = ""
        _returnQty.value = ""
        _returnReason.value = ""
        // Do NOT reset _submitState here — clearSubmitState() handles that after the overlay dismisses
    }

    fun reset() { resetInward(); resetReturn() }
    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
