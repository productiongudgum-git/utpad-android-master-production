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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.gudgum_prod_flow.ui.components.SearchableDropdown
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
    val selectedBatchSize by viewModel.selectedBatchSize.collectAsState()
    val recipe by viewModel.recipe.collectAsState()
    val expectedYield by viewModel.expectedYield.collectAsState()
    val manufacturingDate by viewModel.manufacturingDate.collectAsState()
    val actualOutput by viewModel.actualOutput.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val currentStep by viewModel.currentWizardStep.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(submitState) {
        when (val state = submitState) {
            is SubmitState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearSubmitState()
            }
            is SubmitState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearSubmitState()
            }
            else -> {}
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
                    .padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                OperationsModuleTabs(
                    currentRoute = AppRoute.Production,
                    allowedRoutes = allowedRoutes,
                    onNavigateToRoute = onNavigateToRoute,
                )

                WizardProgressBar(
                    currentStep = currentStep,
                    totalSteps = 3,
                    stepTitle = when(currentStep) {
                        1 -> "Flavor & Batch"
                        2 -> "Recipe & Yield"
                        else -> "Output & Date"
                    }
                )

                when (currentStep) {
                    1 -> {
                        // Flavor Profile + Batch Code card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Flavor Profile dropdown
                        Column {
                            Text(
                                text = "FLAVOR PROFILE",
                                style = MaterialTheme.typography.labelSmall,
                                color = UtpadTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val flavorsList by viewModel.flavors.collectAsState()
                            SearchableDropdown(
                                items = flavorsList,
                                selectedItem = selectedFlavor,
                                onItemSelected = { viewModel.onFlavorSelected(it) },
                                itemLabel = { it.name },
                                placeholder = "Select Flavor...",
                            )
                        }

                        // Batch Size selection cards
                        Column {
                            Text(
                                text = "BATCH SIZE",
                                style = MaterialTheme.typography.labelSmall,
                                color = UtpadTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                com.example.gudgum_prod_flow.ui.viewmodels.BATCH_SIZE_OPTIONS.forEach { config ->
                                    val isSelected = selectedBatchSize == config
                                    Surface(
                                        onClick = { viewModel.onBatchSizeSelected(config) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) UtpadPrimary.copy(alpha = 0.1f) else UtpadBackground,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) UtpadPrimary else UtpadOutline,
                                        ),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = config.label,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) UtpadPrimary else UtpadTextPrimary,
                                            )
                                            Text(
                                                text = "${config.boxes} boxes",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = UtpadTextSecondary,
                                            )
                                            Text(
                                                text = "${config.rawMaterialKg.toInt()} kg input",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = UtpadTextSecondary,
                                            )
                                            Text(
                                                text = "${config.expectedYieldKg} kg yield",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelected) UtpadPrimary else UtpadTextSecondary,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Batch Code (read-only)
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
                        }
                    }
                }
                } // End of step 1
                2 -> {
                // Recipe Ingredients section
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
                                text = "QTY (KG)",
                                style = MaterialTheme.typography.labelSmall,
                                color = UtpadTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        HorizontalDivider(color = UtpadOutline)

                        // Ingredient rows
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
                                    modifier = Modifier.weight(0.5f),
                                )
                            }
                            if (index < recipe.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = UtpadOutline)
                            }
                        }

                        // Expected Yield footer
                        HorizontalDivider(color = UtpadOutline)
                        Surface(
                            color = UtpadBackground,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Expected Yield",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = UtpadPrimary,
                                )
                                Text(
                                    text = expectedYield,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = UtpadPrimary,
                                )
                            }
                        }
                    }
                }
                } // End of step 2
                3 -> {
                // Manufacturing Date + Actual Output card
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
                                                val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                                viewModel.onManufacturingDateChanged(format.format(java.util.Date(millis)))
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
                                // Invisible surface to capture clicks
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
                                text = "ACTUAL OUTPUT (KG)",
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
                                suffix = { Text("Kg", color = UtpadTextSecondary) },
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
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "System allows +/-5% tolerance from expected yield.",
                                style = MaterialTheme.typography.labelSmall,
                                color = UtpadTextSecondary,
                            )
                        }
                    }
                }
                } // End of step 3
                } // End of when

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom action button
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shadowElevation = 20.dp,
                color = UtpadSurface,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { 
                                if (currentStep > 1) {
                                    viewModel.previousStep()
                                } else {
                                    viewModel.reset()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = UtpadTextPrimary
                            )
                        ) {
                            if (currentStep > 1) {
                                Text("Back", fontWeight = FontWeight.Bold)
                            } else {
                                Text("Reset", fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Button(
                            onClick = {
                                if (currentStep < 3) {
                                    viewModel.nextStep()
                                } else {
                                    viewModel.submit()
                                }
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
                            if (currentStep < 3) {
                                Text("Continue", fontWeight = FontWeight.Bold)
                            } else {
                                Text("Confirm & Save", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (AppRoute.Packing in allowedRoutes) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { onNavigateToRoute(AppRoute.Packing) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Continue to Packing", color = UtpadPrimary, fontWeight = FontWeight.Bold)
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
        
        // Progress Bar Line
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
