package com.example.gudgum_prod_flow.ui.screens.production

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gudgum_prod_flow.ui.components.BarcodeScannerButton
import com.example.gudgum_prod_flow.ui.components.SearchableDropdown
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import com.example.gudgum_prod_flow.ui.theme.UtpadBackground
import com.example.gudgum_prod_flow.ui.theme.UtpadError
import com.example.gudgum_prod_flow.ui.theme.UtpadOutline
import com.example.gudgum_prod_flow.ui.theme.UtpadPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadSuccess
import com.example.gudgum_prod_flow.ui.theme.UtpadSurface
import com.example.gudgum_prod_flow.ui.theme.UtpadTextPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadTextSecondary
import com.example.gudgum_prod_flow.ui.viewmodels.DispatchViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.SubmitState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchScreen(
    allowedRoutes: Set<String>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    viewModel: DispatchViewModel = hiltViewModel(),
) {
    val batchCode by viewModel.batchCode.collectAsState()
    val batchCodes by viewModel.batchCodes.collectAsState()
    val batchCodesLoading by viewModel.batchCodesLoading.collectAsState()
    val qtyDispatched by viewModel.qtyDispatched.collectAsState()
    val dispatchDate by viewModel.dispatchDate.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val selectedCustomerId by viewModel.selectedCustomerId.collectAsState()
    val selectedCustomerName by viewModel.selectedCustomerName.collectAsState()
    val customersLoading by viewModel.customersLoading.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val currentStep by viewModel.currentWizardStep.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                    Text("Dispatch Wizard", fontWeight = FontWeight.Bold, color = UtpadTextPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = UtpadTextPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = UtpadPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = UtpadBackground)
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
                    .padding(bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                OperationsModuleTabs(
                    currentRoute = AppRoute.Dispatch,
                    allowedRoutes = allowedRoutes,
                    onNavigateToRoute = onNavigateToRoute,
                )

                WizardProgressBar(
                    currentStep = currentStep,
                    totalSteps = 3,
                    stepTitle = when (currentStep) {
                        1 -> "Batch Code"
                        2 -> "Quantity"
                        else -> "Customer & Date"
                    }
                )

                when (currentStep) {
                    // ── Step 1: Batch Code dropdown ──
                    1 -> {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(
                                    text = "BATCH CODE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UtpadTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (batchCodesLoading) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.height(20.dp).width(20.dp),
                                            strokeWidth = 2.dp,
                                            color = UtpadPrimary
                                        )
                                        Text("Loading batch codes...", color = UtpadTextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SearchableDropdown(
                                            items = batchCodes,
                                            selectedItem = batchCodes.firstOrNull { it == batchCode },
                                            selectedLabel = batchCode.ifBlank { null },
                                            onItemSelected = viewModel::onBatchCodeSelected,
                                            itemLabel = { it },
                                            placeholder = "Search or select batch code...",
                                            label = "Batch Code",
                                            modifier = Modifier
                                                .weight(1f),
                                        )
                                        BarcodeScannerButton(
                                            prompt = "Scan dispatch batch code",
                                            onBarcodeScanned = { scannedCode ->
                                                viewModel.onBatchCodeSelected(scannedCode.trim())
                                            },
                                            onScanError = { error ->
                                                if (error != "Scan cancelled") {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(error)
                                                    }
                                                }
                                            },
                                        )
                                    }
                                    Text(
                                        text = "Search from the live batch list or scan a barcode to fill the batch code instantly.",
                                        color = UtpadTextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }

                    // ── Step 2: Units Dispatched ──
                    2 -> {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(
                                    text = "UNITS DISPATCHED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UtpadTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedTextField(
                                    value = qtyDispatched,
                                    onValueChange = viewModel::onQtyDispatchedChanged,
                                    placeholder = { Text("0", color = UtpadTextSecondary) },
                                    suffix = { Text("units", color = UtpadTextSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
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

                    // ── Step 3: Customer & Dispatch Date ──
                    3 -> {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                // Customer Dropdown
                                Column {
                                    Text(
                                        text = "CUSTOMER",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UtpadTextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (customersLoading) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp, color = UtpadPrimary)
                                            Text("Loading customers...", color = UtpadTextSecondary, style = MaterialTheme.typography.bodySmall)
                                        }
                                    } else {
                                        var customerExpanded by remember { mutableStateOf(false) }
                                        ExposedDropdownMenuBox(
                                            expanded = customerExpanded,
                                            onExpandedChange = { customerExpanded = it },
                                        ) {
                                            OutlinedTextField(
                                                value = selectedCustomerName,
                                                onValueChange = {},
                                                readOnly = true,
                                                placeholder = { Text("Select customer...", color = UtpadTextSecondary) },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = customerExpanded) },
                                                modifier = Modifier
                                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                    .fillMaxWidth(),
                                                singleLine = true,
                                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = UtpadPrimary,
                                                    unfocusedBorderColor = UtpadOutline,
                                                    focusedContainerColor = UtpadBackground,
                                                    unfocusedContainerColor = UtpadSurface,
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                            )
                                            DropdownMenu(
                                                expanded = customerExpanded,
                                                onDismissRequest = { customerExpanded = false },
                                            ) {
                                                if (customers.isEmpty()) {
                                                    DropdownMenuItem(
                                                        text = { Text("No customers found", color = UtpadTextSecondary) },
                                                        onClick = { customerExpanded = false },
                                                        enabled = false,
                                                    )
                                                } else {
                                                    customers.forEach { customer ->
                                                        DropdownMenuItem(
                                                            text = { Text(customer.name) },
                                                            onClick = {
                                                                viewModel.onCustomerSelected(customer.id, customer.name)
                                                                customerExpanded = false
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Dispatch Date
                                Column {
                                    Text(
                                        text = "DISPATCH DATE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UtpadTextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    var showDatePicker by remember { mutableStateOf(false) }

                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = dispatchDate,
                                            onValueChange = {},
                                            placeholder = { Text("Select Date", color = UtpadTextSecondary) },
                                            singleLine = true,
                                            readOnly = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
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

                                    if (showDatePicker) {
                                        val datePickerState = rememberDatePickerState()
                                        DatePickerDialog(
                                            onDismissRequest = { showDatePicker = false },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    val selectedMillis = datePickerState.selectedDateMillis
                                                    if (selectedMillis != null) {
                                                        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                        viewModel.onDispatchDateChanged(formatter.format(Date(selectedMillis)))
                                                    }
                                                    showDatePicker = false
                                                }) { Text("OK", color = UtpadPrimary) }
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
                                }
                            }
                        }

                        // Review Card
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = UtpadBackground),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "DISPATCH SUMMARY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UtpadTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Batch: $batchCode",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = UtpadTextPrimary,
                                )
                                Text(
                                    text = "Customer: ${selectedCustomerName.ifBlank { "—" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = UtpadTextPrimary,
                                )
                                Text(
                                    text = "Quantity: $qtyDispatched units on $dispatchDate",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = UtpadTextPrimary,
                                )
                            }
                        }
                    }
                }
            }

            // Bottom action bar
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
                        OutlinedButton(
                            onClick = {
                                if (currentStep > 1) viewModel.previousStep() else viewModel.reset()
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = UtpadTextPrimary
                            )
                        ) {
                            Text(if (currentStep > 1) "Back" else "Reset", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (currentStep < 3) viewModel.nextStep() else viewModel.submit()
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = UtpadPrimary,
                                contentColor = androidx.compose.ui.graphics.Color.White
                            ),
                            enabled = submitState !is SubmitState.Loading,
                        ) {
                            Text(
                                if (currentStep < 3) "Continue" else "Confirm Dispatch",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (AppRoute.Inwarding in allowedRoutes) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { onNavigateToRoute(AppRoute.Inwarding) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Back to Inwarding", color = UtpadPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
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
