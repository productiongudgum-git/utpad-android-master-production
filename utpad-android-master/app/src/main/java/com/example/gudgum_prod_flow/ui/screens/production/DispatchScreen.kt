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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    val invoices by viewModel.invoices.collectAsState()
    val invoicesLoading by viewModel.invoicesLoading.collectAsState()
    val selectedInvoice by viewModel.selectedInvoice.collectAsState()
    val invoiceItems by viewModel.invoiceItems.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()
    val boxesToDispatch by viewModel.boxesToDispatch.collectAsState()
    val fifoLines by viewModel.fifoLines.collectAsState()
    val fifoError by viewModel.fifoError.collectAsState()
    val isPacked by viewModel.isPacked.collectAsState()
    val isDispatched by viewModel.isDispatched.collectAsState()
    val dispatchDate by viewModel.dispatchDate.collectAsState()
    val allInvoices by viewModel.allInvoices.collectAsState()
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
                    totalSteps = 5,
                    stepTitle = when (currentStep) {
                        1 -> "Select Invoice"
                        2 -> "Customer Info"
                        3 -> "Select Flavour"
                        4 -> "Boxes & FIFO"
                        else -> "Confirm"
                    }
                )

                when (currentStep) {
                    // ── Step 1: Invoice Selection ──
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
                                    text = "INVOICE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UtpadTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (invoicesLoading) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.height(20.dp).width(20.dp),
                                            strokeWidth = 2.dp,
                                            color = UtpadPrimary
                                        )
                                        Text("Loading invoices...", color = UtpadTextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                } else {
                                    SearchableDropdown(
                                        items = invoices,
                                        selectedItem = selectedInvoice,
                                        onItemSelected = { viewModel.onInvoiceSelected(it) },
                                        itemLabel = { "${it.invoiceNumber} — ${it.customerName}" },
                                        placeholder = "Search invoice...",
                                    )
                                    Text(
                                        text = "Select an unpacked/undispatched invoice to begin.",
                                        color = UtpadTextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }

                    // ── Step 2: Customer Info (auto-populated) ──
                    2 -> {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "CUSTOMER",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UtpadTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = selectedInvoice?.customerName ?: "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = UtpadTextPrimary,
                                )

                                HorizontalDivider(color = UtpadOutline)

                                Text(
                                    text = "INVOICE ITEMS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UtpadTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (invoiceItems.isEmpty()) {
                                    Text(
                                        text = "Loading items...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = UtpadTextSecondary,
                                    )
                                } else {
                                    invoiceItems.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                text = item.flavor?.name ?: item.flavorId,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = UtpadTextPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = "${item.quantityUnits / 15} boxes",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = UtpadPrimary,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Step 3: Flavour Selection ──
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
                                Text(
                                    text = "SELECT FLAVOUR TO DISPATCH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UtpadTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                invoiceItems.forEach { item ->
                                    val isSelected = selectedItem?.id == item.id
                                    Surface(
                                        onClick = { viewModel.onItemSelected(item) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) UtpadPrimary.copy(alpha = 0.1f) else UtpadBackground,
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) UtpadPrimary else UtpadOutline,
                                        ),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column {
                                                Text(
                                                    text = item.flavor?.name ?: item.flavorId,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) UtpadPrimary else UtpadTextPrimary,
                                                )
                                            }
                                            Text(
                                                text = "${item.quantityUnits / 15} boxes",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) UtpadPrimary else UtpadTextSecondary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Step 4: Units & FIFO Allocation ──
                    4 -> {
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
                                    text = "BOXES TO DISPATCH",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UtpadTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                OutlinedTextField(
                                    value = boxesToDispatch,
                                    onValueChange = viewModel::onBoxesChanged,
                                    placeholder = { Text("0", color = UtpadTextSecondary) },
                                    suffix = { Text("boxes", color = UtpadTextSecondary) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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

                                // FIFO Allocation table
                                if (fifoLines.isNotEmpty()) {
                                    HorizontalDivider(color = UtpadOutline)
                                    Text(
                                        text = "FIFO ALLOCATION (oldest first)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UtpadTextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    // Header
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Batch", style = MaterialTheme.typography.labelSmall, color = UtpadTextSecondary, modifier = Modifier.weight(1f))
                                        Text("Available", style = MaterialTheme.typography.labelSmall, color = UtpadTextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                        Text("Take", style = MaterialTheme.typography.labelSmall, color = UtpadTextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                    }

                                    fifoLines.forEach { line ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = line.batchCode,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = UtpadTextPrimary,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = "${line.availableUnits}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = UtpadTextSecondary,
                                                modifier = Modifier.weight(1f),
                                                textAlign = TextAlign.Center,
                                            )
                                            Text(
                                                text = "${line.unitsToTake}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = UtpadPrimary,
                                                modifier = Modifier.weight(1f),
                                                textAlign = TextAlign.End,
                                            )
                                        }
                                    }
                                }

                                // Error
                                if (fifoError != null) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = UtpadError.copy(alpha = 0.1f),
                                    ) {
                                        Text(
                                            text = fifoError!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = UtpadError,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Step 5: Confirm & Status ──
                    5 -> {
                        // Summary card
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "DISPATCH SUMMARY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = UtpadTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = "Invoice: ${selectedInvoice?.invoiceNumber ?: "—"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UtpadTextPrimary,
                                )
                                Text(
                                    text = "Customer: ${selectedInvoice?.customerName ?: "—"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UtpadTextPrimary,
                                )
                                Text(
                                    text = "Flavour: ${selectedItem?.flavor?.name ?: "—"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UtpadTextPrimary,
                                )
                                Text(
                                    text = "Boxes: $boxesToDispatch across ${fifoLines.size} batch(es)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = UtpadPrimary,
                                )

                                HorizontalDivider(color = UtpadOutline)

                                // Dispatch date
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
                                            color = Color.Transparent,
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

                                HorizontalDivider(color = UtpadOutline)

                                // Packed checkbox
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Checkbox(
                                        checked = isPacked,
                                        onCheckedChange = { viewModel.onPackedToggle(it) },
                                        colors = CheckboxDefaults.colors(checkedColor = UtpadPrimary),
                                    )
                                    Text(
                                        text = "Mark Invoice as Packed",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = UtpadTextPrimary,
                                    )
                                }

                                // Dispatched checkbox
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Checkbox(
                                        checked = isDispatched,
                                        onCheckedChange = { viewModel.onDispatchedToggle(it) },
                                        colors = CheckboxDefaults.colors(checkedColor = UtpadSuccess),
                                    )
                                    Text(
                                        text = "Mark Invoice as Dispatched",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = UtpadTextPrimary,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Dispatch Tracking Table ──
                if (allInvoices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "DISPATCH TRACKING",
                                style = MaterialTheme.typography.labelSmall,
                                color = UtpadTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("Invoice", style = MaterialTheme.typography.labelSmall, color = UtpadTextSecondary, modifier = Modifier.weight(1.2f))
                                Text("Customer", style = MaterialTheme.typography.labelSmall, color = UtpadTextSecondary, modifier = Modifier.weight(1.2f))
                                Text("Packed", style = MaterialTheme.typography.labelSmall, color = UtpadTextSecondary, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                                Text("Sent", style = MaterialTheme.typography.labelSmall, color = UtpadTextSecondary, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = UtpadOutline)

                            allInvoices.forEach { inv ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = inv.invoiceNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = UtpadTextPrimary,
                                        modifier = Modifier.weight(1.2f),
                                    )
                                    Text(
                                        text = inv.customerName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = UtpadTextSecondary,
                                        modifier = Modifier.weight(1.2f),
                                    )
                                    // Packed toggle
                                    Surface(
                                        onClick = { viewModel.toggleInvoicePacked(inv) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (inv.isPacked) UtpadSuccess.copy(alpha = 0.15f) else UtpadOutline.copy(alpha = 0.3f),
                                        modifier = Modifier.weight(0.8f),
                                    ) {
                                        Text(
                                            text = if (inv.isPacked) "Yes" else "No",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (inv.isPacked) UtpadSuccess else UtpadTextSecondary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    // Dispatched toggle
                                    Surface(
                                        onClick = { viewModel.toggleInvoiceDispatched(inv) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (inv.isDispatched) UtpadSuccess.copy(alpha = 0.15f) else UtpadOutline.copy(alpha = 0.3f),
                                        modifier = Modifier.weight(0.8f),
                                    ) {
                                        Text(
                                            text = if (inv.isDispatched) "Yes" else "No",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (inv.isDispatched) UtpadSuccess else UtpadTextSecondary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                                        )
                                    }
                                }
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
                                if (currentStep < 5) viewModel.nextStep() else viewModel.submit()
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = UtpadPrimary,
                                contentColor = Color.White
                            ),
                            enabled = submitState !is SubmitState.Loading,
                        ) {
                            Text(
                                if (currentStep < 5) "Continue" else "Confirm Dispatch",
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
