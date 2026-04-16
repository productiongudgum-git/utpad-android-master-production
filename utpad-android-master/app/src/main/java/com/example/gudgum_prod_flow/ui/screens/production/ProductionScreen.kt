package com.example.gudgum_prod_flow.ui.screens.production

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gudgum_prod_flow.ui.components.SuccessOverlay
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import com.example.gudgum_prod_flow.ui.viewmodels.ProductionViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.SubmitState
import com.example.gudgum_prod_flow.ui.theme.UtpadPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadSuccess
import com.example.gudgum_prod_flow.ui.theme.UtpadOutline
import com.example.gudgum_prod_flow.ui.theme.UtpadTextPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadTextSecondary
import com.example.gudgum_prod_flow.ui.theme.UtpadBackground
import com.example.gudgum_prod_flow.ui.theme.UtpadSurface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionScreen(
    allowedRoutes: Set<String>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    viewModel: ProductionViewModel = hiltViewModel(),
) {
    val selectedFlavor by viewModel.selectedFlavor.collectAsState()
    val batchCode by viewModel.batchCode.collectAsState()
    val previewBatchNumber by viewModel.previewBatchNumber.collectAsState()
    val recipe by viewModel.recipe.collectAsState()
    val totalInputWeight by viewModel.totalInputWeight.collectAsState()
    val expectedBoxesFromInput by viewModel.expectedBoxesFromInput.collectAsState()
    val manufacturingDate by viewModel.manufacturingDate.collectAsState()
    val actualOutput by viewModel.actualOutput.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val currentStep by viewModel.currentWizardStep.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(submitState) {
        if (submitState is SubmitState.Error) {
            snackbarHostState.showSnackbar((submitState as SubmitState.Error).message)
            viewModel.clearSubmitState()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Production Wizard", fontWeight = FontWeight.Bold, color = UtpadTextPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = UtpadTextPrimary
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = UtpadPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = UtpadBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    // Step 2 bottom bar is taller (totals + buttons), so give more clearance
                    .padding(bottom = if (currentStep == 2) 180.dp else 100.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
            ) {
                OperationsModuleTabs(
                    currentRoute = AppRoute.Production,
                    allowedRoutes = allowedRoutes,
                    onNavigateToRoute = onNavigateToRoute,
                )

                WizardProgressBar(
                    currentStep = currentStep,
                    totalSteps = 3,
                    stepTitle = when (currentStep) {
                        1 -> "Select Flavor"
                        2 -> "Recipe Ingredients"
                        else -> "Output & Date"
                    }
                )

                when (currentStep) {
                    // ── Step 1: Select Flavor ──
                    1 -> {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                // Flavor Profile — scrollable card list
                                Column {
                                    Text(
                                        text = "FLAVOR PROFILE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UtpadTextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val flavorsList by viewModel.flavors.collectAsState()
                                    LazyColumn(
                                        modifier = Modifier.height(260.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(flavorsList) { flavor ->
                                            val isSelected = selectedFlavor?.id == flavor.id
                                            Surface(
                                                onClick = { viewModel.onFlavorSelected(flavor) },
                                                shape = RoundedCornerShape(16.dp),
                                                color = if (isSelected) UtpadPrimary.copy(alpha = 0.1f) else UtpadBackground,
                                                border = BorderStroke(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) UtpadPrimary else UtpadOutline,
                                                ),
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = flavor.name,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) UtpadPrimary else UtpadTextPrimary,
                                                        )
                                                        Text(
                                                            text = flavor.code,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = if (isSelected) UtpadPrimary.copy(alpha = 0.7f) else UtpadTextSecondary,
                                                        )
                                                    }
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Filled.CheckCircle,
                                                            contentDescription = null,
                                                            tint = UtpadPrimary,
                                                            modifier = Modifier.size(20.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Batch Code + Batch Number (read-only, auto-generated)
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "BATCH CODE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = UtpadTextSecondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = UtpadBackground,
                                        ) {
                                            Text(
                                                text = "Autogenerated",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = UtpadPrimary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = batchCode,
                                        onValueChange = {},
                                        readOnly = true,
                                        placeholder = { Text("Generated from today's date", color = UtpadTextSecondary) },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            disabledTextColor = UtpadTextPrimary,
                                            unfocusedBorderColor = UtpadOutline,
                                            unfocusedContainerColor = UtpadSurface,
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                    )

                                    // Batch number preview — updates once a flavor is selected
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = when {
                                            previewBatchNumber != null -> UtpadPrimary.copy(alpha = 0.08f)
                                            else -> UtpadBackground
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "BATCH NUMBER",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = UtpadTextSecondary,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = when {
                                                    selectedFlavor == null -> "Select a flavor first"
                                                    previewBatchNumber == null -> "Loading..."
                                                    else -> "Batch $previewBatchNumber"
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (previewBatchNumber != null) UtpadPrimary else UtpadTextSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } // End step 1

                    // ── Step 2: Recipe Ingredients (editable quantities) ──
                    2 -> {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        ) {
                            Column {
                                // Header row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "INGREDIENT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UtpadTextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "QUANTITY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UtpadTextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                HorizontalDivider(color = UtpadOutline)

                                // Ingredient rows — each field shows the original recipe unit
                                recipe.forEachIndexed { index, ingredient ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = ingredient.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = UtpadTextPrimary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        OutlinedTextField(
                                            value = ingredient.quantity,
                                            onValueChange = { viewModel.onRecipeQuantityChanged(index, it) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            suffix = {
                                                Text(
                                                    ingredient.unit,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = UtpadTextSecondary,
                                                )
                                            },
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                textAlign = TextAlign.End,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = UtpadPrimary,
                                                unfocusedBorderColor = UtpadOutline,
                                                focusedContainerColor = UtpadBackground,
                                                unfocusedContainerColor = UtpadSurface,
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(0.55f),
                                        )
                                    }
                                    if (index < recipe.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = UtpadOutline
                                        )
                                    }
                                }

                            }
                        }
                    } // End step 2

                    // ── Step 3: Manufacturing Date + Actual Yield ──
                    3 -> {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                // Manufacturing Date
                                Column {
                                    Text(
                                        text = "MANUFACTURING DATE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UtpadTextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    var showDatePicker by remember { mutableStateOf(false) }
                                    if (showDatePicker) {
                                        val datePickerState = rememberDatePickerState()
                                        DatePickerDialog(
                                            onDismissRequest = { showDatePicker = false },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    datePickerState.selectedDateMillis?.let { millis ->
                                                        val format = java.text.SimpleDateFormat(
                                                            "yyyy-MM-dd",
                                                            java.util.Locale.getDefault()
                                                        )
                                                        viewModel.onManufacturingDateChanged(
                                                            format.format(java.util.Date(millis))
                                                        )
                                                    }
                                                    showDatePicker = false
                                                }) {
                                                    Text("OK", color = UtpadPrimary)
                                                }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showDatePicker = false }) {
                                                    Text("Cancel", color = UtpadTextSecondary)
                                                }
                                            }
                                        ) {
                                            DatePicker(state = datePickerState)
                                        }
                                    }

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = manufacturingDate,
                                            onValueChange = {},
                                            readOnly = true,
                                            placeholder = { Text("Select Date", color = UtpadTextSecondary) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = UtpadPrimary,
                                                unfocusedBorderColor = UtpadOutline,
                                                focusedContainerColor = UtpadBackground,
                                                unfocusedContainerColor = UtpadSurface,
                                            ),
                                            shape = RoundedCornerShape(16.dp),
                                        )
                                        Surface(
                                            modifier = Modifier.matchParentSize(),
                                            color = androidx.compose.ui.graphics.Color.Transparent,
                                            onClick = { showDatePicker = true }
                                        ) {}
                                    }
                                }

                                // Actual Production Output
                                Column {
                                    Text(
                                        text = "ACTUAL YIELD (KG)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UtpadTextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = actualOutput,
                                        onValueChange = { viewModel.onActualOutputChanged(it) },
                                        placeholder = { Text("0.00", color = UtpadTextSecondary) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        suffix = { Text("kg", color = UtpadTextSecondary) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = UtpadPrimary,
                                            unfocusedBorderColor = UtpadOutline,
                                            focusedContainerColor = UtpadBackground,
                                            unfocusedContainerColor = UtpadSurface,
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                }
                            }
                        }
                    } // End step 3
                } // End when
            }

            // Success overlay — shown after a successful submission.
            // Dismisses after 2 seconds and resets the wizard.
            if (submitState is SubmitState.Success) {
                SuccessOverlay(onDismiss = {
                    viewModel.clearSubmitState()
                    viewModel.reset()
                })
            }

            // Bottom action bar — also shows step 2 totals pinned above buttons
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shadowElevation = 20.dp,
                color = UtpadSurface,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column {
                    // Pinned totals summary for step 2
                    if (currentStep == 2) {
                        HorizontalDivider(color = UtpadOutline)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Total Input Weight",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = UtpadPrimary,
                                )
                                Text(
                                    text = "%.3f kg".format(totalInputWeight),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = UtpadPrimary,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Expected Boxes",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = UtpadTextSecondary,
                                )
                                Text(
                                    text = "$expectedBoxesFromInput boxes",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = UtpadTextSecondary,
                                )
                            }
                        }
                        HorizontalDivider(color = UtpadOutline)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                if (currentStep > 1) viewModel.previousStep() else viewModel.reset()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = UtpadTextPrimary
                            )
                        ) {
                            Text(
                                if (currentStep > 1) "Back" else "Reset",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                if (currentStep < 3) viewModel.nextStep() else viewModel.submit()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = UtpadPrimary,
                                contentColor = androidx.compose.ui.graphics.Color.White
                            ),
                            enabled = submitState !is SubmitState.Loading,
                        ) {
                            Text(
                                if (currentStep < 3) "Continue" else "Confirm & Save",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = UtpadTextSecondary,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun WizardProgressBar(
    currentStep: Int,
    totalSteps: Int,
    stepTitle: String,
    modifier: Modifier = Modifier
) {
    val progress = currentStep.toFloat() / totalSteps.toFloat()
    val percentage = (progress * 100).toInt()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "STEP $currentStep OF $totalSteps",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = UtpadTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                color = UtpadTextSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(UtpadOutline, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .height(6.dp)
                    .background(UtpadPrimary, RoundedCornerShape(3.dp))
            )
        }
    }
}
