package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.local.entity.CachedFlavorEntity
import com.example.gudgum_prod_flow.data.local.entity.CachedRecipeLineEntity
import com.example.gudgum_prod_flow.data.remote.SupabaseRealtimeManager
import com.example.gudgum_prod_flow.data.repository.ProductionRepository
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import android.util.Log
import com.example.gudgum_prod_flow.util.BatchCodeGenerator
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

data class FlavorProfile(val id: String, val name: String, val code: String, val recipeId: String?)

data class RecipeIngredient(
    val ingredientId: String,
    val name: String,
    var plannedQty: String,
    var actualQty: String,
    val unit: String,
) {
    val quantity: String get() = actualQty
}

@HiltViewModel
class ProductionViewModel @Inject constructor(
    private val repository: ProductionRepository,
    private val realtimeManager: SupabaseRealtimeManager,
) : ViewModel() {

    companion object {
        private const val TAG = "ProductionViewModel"
    }

    private val _flavors = MutableStateFlow<List<FlavorProfile>>(emptyList())
    val flavors: StateFlow<List<FlavorProfile>> = _flavors.asStateFlow()

    private val _selectedFlavor = MutableStateFlow<FlavorProfile?>(null)
    val selectedFlavor: StateFlow<FlavorProfile?> = _selectedFlavor.asStateFlow()

    private val _batchCode = MutableStateFlow(BatchCodeGenerator.generate())
    val batchCode: StateFlow<String> = _batchCode.asStateFlow()

    // Previewed batch number shown in Step 1 once a flavor is selected.
    // null = not yet computed (flavor not selected or still loading).
    private val _previewBatchNumber = MutableStateFlow<Int?>(null)
    val previewBatchNumber: StateFlow<Int?> = _previewBatchNumber.asStateFlow()

    private val _manufacturingDate = MutableStateFlow(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    )
    val manufacturingDate: StateFlow<String> = _manufacturingDate.asStateFlow()

    private val _recipe = MutableStateFlow<List<RecipeIngredient>>(emptyList())
    val recipe: StateFlow<List<RecipeIngredient>> = _recipe.asStateFlow()

    private var baseRecipeIngredients: List<RecipeIngredient> = emptyList()

    // The actual recipe UUID returned from gg_recipes
    private var selectedRecipeId: String? = null

    // ── Unit conversion helpers ──────────────────────────────────────────────
    // Returns the multiplier to convert a quantity in `unit` → kg.
    private fun toKgFactor(unit: String): Double = when (unit.lowercase().trim()) {
        "g", "gram", "grams" -> 0.001
        "kg", "kgs", "kilogram", "kilograms" -> 1.0
        "ml", "milliliter", "millilitre", "milliliters", "millilitres" -> 0.001
        "l", "liter", "litre", "liters", "litres" -> 1.0
        else -> 1.0  // treat unknown units as kg
    }

    private fun ingredientToKg(ingredient: RecipeIngredient): Double =
        (ingredient.actualQty.toDoubleOrNull() ?: 0.0) * toKgFactor(ingredient.unit)

    // ── Derived totals (update in real time as quantities are edited) ────────
    // Total input weight in kg, using per-ingredient unit conversion.
    val totalInputWeight: StateFlow<Double> = _recipe
        .map { ingredients -> ingredients.sumOf { ingredientToKg(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    // Expected boxes = floor(totalInputWeightKg / 0.021)
    val expectedBoxesFromInput: StateFlow<Int> = _recipe
        .map { ingredients ->
            val totalKg = ingredients.sumOf { ingredientToKg(it) }
            (totalKg / 0.021).toInt()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _actualOutput = MutableStateFlow("")
    val actualOutput: StateFlow<String> = _actualOutput.asStateFlow()

    val flavorProfiles: List<FlavorProfile>
        get() = _flavors.value

    private val _submitState = MutableStateFlow<SubmitState>(SubmitState.Idle)
    val submitState: StateFlow<SubmitState> = _submitState.asStateFlow()

    private val _currentWizardStep = MutableStateFlow(1)
    val currentWizardStep: StateFlow<Int> = _currentWizardStep.asStateFlow()

    private var isOnline: Boolean = true

    init {
        viewModelScope.launch {
            repository.getActiveFlavors().collect { entities ->
                _flavors.value = entities.map {
                    FlavorProfile(
                        id = it.id,
                        name = it.name,
                        code = it.code,
                        recipeId = it.recipeId,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.refreshFlavors()
        }

        realtimeManager.connect()
        viewModelScope.launch {
            realtimeManager.tableChanged.collect { table ->
                when (table) {
                    "gg_flavors" -> repository.refreshFlavors()
                    "gg_recipes", "recipe_lines" -> {
                        val currentFlavor = _selectedFlavor.value
                        if (currentFlavor != null) {
                            val recipeKey = currentFlavor.recipeId ?: currentFlavor.id
                            val yieldResult = repository.refreshRecipeLines(recipeKey)
                            yieldResult.onSuccess { (recipeId, _) ->
                                selectedRecipeId = recipeId
                            }
                        }
                    }
                    // Refresh batch number preview if production_batches table changes
                    "production_batches" -> {
                        repository.refreshOpenBatches()
                        refreshBatchNumber()
                    }
                }
            }
        }
    }

    fun setOnlineStatus(online: Boolean) { isOnline = online }

    fun refreshData() {
        viewModelScope.launch { repository.refreshFlavors() }
    }

    // Queries the DB to compute what the next batch_number will be for the
    // current batchCode + selectedFlavor combination, and caches it for display.
    private fun refreshBatchNumber() {
        val flavor = _selectedFlavor.value ?: return
        val code = _batchCode.value
        Log.d(TAG, "refreshBatchNumber: batchCode=$code flavorId=${flavor.id} flavorName=${flavor.name}")
        viewModelScope.launch {
            val count = repository.countBatchesForCodeAndFlavor(code, flavor.id).getOrDefault(0)
            val next = count + 1
            Log.d(TAG, "refreshBatchNumber: existingCount=$count → previewBatchNumber=$next")
            _previewBatchNumber.value = next
        }
    }

    fun onFlavorSelected(flavor: FlavorProfile) {
        _selectedFlavor.value = flavor
        _recipe.value = emptyList()
        baseRecipeIngredients = emptyList()
        _previewBatchNumber.value = null  // show loading state while we query

        val recipeKey = flavor.recipeId ?: flavor.id
        viewModelScope.launch {
            if (isOnline) {
                val yieldResult = repository.refreshRecipeLines(recipeKey)
                yieldResult.onSuccess { (recipeId, _) ->
                    selectedRecipeId = recipeId
                }
            }
            repository.getRecipeLines(recipeKey).collect { lines ->
                val ingredients = lines.map {
                    RecipeIngredient(
                        ingredientId = it.ingredientId,
                        name = it.ingredientName,
                        plannedQty = it.plannedQty.toString(),
                        actualQty = it.plannedQty.toString(),
                        unit = it.unit,
                    )
                }
                baseRecipeIngredients = ingredients
                _recipe.value = ingredients
            }
        }
        // Fetch batch number preview in parallel
        refreshBatchNumber()
    }

    fun onActualQtyChanged(index: Int, value: String) {
        val updated = _recipe.value.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(actualQty = value)
            _recipe.value = updated
        }
    }

    fun onRecipeQuantityChanged(index: Int, value: String) = onActualQtyChanged(index, value)
    fun onActualOutputChanged(value: String) { _actualOutput.value = value }
    fun onManufacturingDateChanged(value: String) { _manufacturingDate.value = value }

    fun nextStep() { if (_currentWizardStep.value < 3) _currentWizardStep.value++ }
    fun previousStep() { if (_currentWizardStep.value > 1) _currentWizardStep.value-- }

    fun submit() {
        if (_submitState.value is SubmitState.Loading) return

        val flavor = _selectedFlavor.value
        val batchCode = _batchCode.value

        if (flavor == null) {
            _submitState.value = SubmitState.Error("Select a flavor/SKU first")
            return
        }
        if (batchCode.isBlank()) {
            _submitState.value = SubmitState.Error("Batch code unavailable. Restart the app.")
            return
        }
        if (_recipe.value.isEmpty()) {
            _submitState.value = SubmitState.Error("No recipe ingredients loaded")
            return
        }

        val invalidIngredient = _recipe.value.find {
            it.actualQty.toDoubleOrNull() == null || it.actualQty.toDouble() < 0
        }
        if (invalidIngredient != null) {
            _submitState.value = SubmitState.Error("Invalid quantity for ${invalidIngredient.name}")
            return
        }

        _submitState.value = SubmitState.Loading
        viewModelScope.launch {
            val recipeKey = selectedRecipeId ?: flavor.id
            val actualYieldKg = _actualOutput.value.toDoubleOrNull()

            // Convert all ingredient quantities to kg before computing totals
            val totalInputKg = _recipe.value.sumOf { ingredientToKg(it) }
            val expectedBoxes = (totalInputKg / 0.021).toInt()
            val expectedUnits = expectedBoxes * 15

            // Re-query batch count at submission time for accuracy
            val existingCount = repository.countBatchesForCodeAndFlavor(batchCode, flavor.id)
                .getOrDefault(0)
            val batchNumber = existingCount + 1

            val result = repository.submitBatch(
                batchCode = batchCode,
                skuId = flavor.id,
                skuCode = flavor.code,
                recipeId = recipeKey,
                productionDate = _manufacturingDate.value,
                workerId = WorkerIdentityStore.workerId,
                plannedYield = totalInputKg,
                actualYield = actualYieldKg,
                isOnline = isOnline,
                expectedBoxes = expectedBoxes,
                expectedUnits = expectedUnits,
                batchNumber = batchNumber,
            )

            result.onSuccess {
                reset()
                _submitState.value = SubmitState.Success(
                    if (isOnline) "Batch $batchCode #$batchNumber (${flavor.name}) submitted successfully"
                    else "Batch $batchCode saved offline — will sync when connected"
                )
            }
            result.onFailure { e ->
                _submitState.value = SubmitState.Error(e.message ?: "Submission failed")
            }
        }
    }

    fun reset() {
        _selectedFlavor.value = null
        _batchCode.value = BatchCodeGenerator.generate()
        _previewBatchNumber.value = null
        _recipe.value = emptyList()
        baseRecipeIngredients = emptyList()
        selectedRecipeId = null
        _actualOutput.value = ""
        _manufacturingDate.value = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        // Do NOT reset _submitState here — clearSubmitState() handles that after the overlay dismisses
        _currentWizardStep.value = 1
    }

    fun clearSubmitState() { _submitState.value = SubmitState.Idle }
}
