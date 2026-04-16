package com.example.gudgum_prod_flow.ui.screens.returns

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceDto
import com.example.gudgum_prod_flow.ui.components.SuccessOverlay
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import com.example.gudgum_prod_flow.ui.screens.production.OperationsModuleTabs
import com.example.gudgum_prod_flow.ui.theme.UtpadBackground
import com.example.gudgum_prod_flow.ui.theme.UtpadError
import com.example.gudgum_prod_flow.ui.theme.UtpadOutline
import com.example.gudgum_prod_flow.ui.theme.UtpadPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadSuccess
import com.example.gudgum_prod_flow.ui.theme.UtpadSurface
import com.example.gudgum_prod_flow.ui.theme.UtpadTextPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadTextSecondary
import com.example.gudgum_prod_flow.ui.viewmodels.InvoiceDispatchLine
import com.example.gudgum_prod_flow.ui.viewmodels.ReturnsViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.SubmitState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnsScreen(
    allowedRoutes: Set<String>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    viewModel: ReturnsViewModel = hiltViewModel(),
) {
    val invoiceNumberInput by viewModel.invoiceNumberInput.collectAsState()
    val searchLoading by viewModel.searchLoading.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val foundInvoice by viewModel.foundInvoice.collectAsState()
    val dispatchLines by viewModel.dispatchLines.collectAsState()
    val selectedLine by viewModel.selectedLine.collectAsState()
    val boxesToReturn by viewModel.boxesToReturn.collectAsState()
    val returnReason by viewModel.returnReason.collectAsState()
    val returnDate by viewModel.returnDate.collectAsState()
    val detailsError by viewModel.detailsError.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val submitState by viewModel.submitState.collectAsState()

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
                title = { Text("Returns", fontWeight = FontWeight.Bold, color = UtpadTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 1) viewModel.previousStep() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = UtpadTextPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) { Text("Logout", color = UtpadPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = UtpadBackground),
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
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
            ) {
                OperationsModuleTabs(
                    currentRoute = AppRoute.Returns,
                    allowedRoutes = allowedRoutes,
                    onNavigateToRoute = onNavigateToRoute,
                )

                ReturnsWizardProgressBar(
                    currentStep = currentStep,
                    totalSteps = 4,
                    stepTitle = when (currentStep) {
                        1 -> "Search Invoice"
                        2 -> "Select Batch"
                        3 -> "Return Details"
                        else -> "Review & Submit"
                    },
                )

                when (currentStep) {
                    1 -> Step1SearchContent(
                        invoiceNumber = invoiceNumberInput,
                        onInvoiceNumberChanged = viewModel::onInvoiceNumberChanged,
                        searchError = searchError,
                    )
                    2 -> Step2SelectBatchContent(
                        invoice = foundInvoice,
                        dispatchLines = dispatchLines,
                        selectedLine = selectedLine,
                        onLineSelected = viewModel::onLineSelected,
                    )
                    3 -> Step3DetailsContent(
                        selectedLine = selectedLine,
                        boxesToReturn = boxesToReturn,
                        onBoxesToReturnChanged = viewModel::onBoxesToReturnChanged,
                        returnReason = returnReason,
                        onReturnReasonChanged = viewModel::onReturnReasonChanged,
                        returnDate = returnDate,
                        onReturnDateChanged = viewModel::onReturnDateChanged,
                        error = detailsError,
                    )
                    4 -> Step4ReviewContent(
                        invoice = foundInvoice,
                        selectedLine = selectedLine,
                        boxesToReturn = boxesToReturn,
                        returnReason = returnReason,
                        returnDate = returnDate,
                    )
                }
            }

            // Success overlay — auto-dismisses and resets to step 1
            if (submitState is SubmitState.Success) {
                SuccessOverlay(onDismiss = {
                    viewModel.clearSubmitState()
                    viewModel.reset()
                })
            }

            // Bottom action bar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shadowElevation = 20.dp,
                color = UtpadSurface,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
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
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = UtpadTextPrimary,
                            ),
                        ) {
                            Text(
                                text = if (currentStep > 1) "Back" else "Reset",
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Button(
                            onClick = {
                                when (currentStep) {
                                    1 -> viewModel.searchInvoice()
                                    2 -> viewModel.nextFromSelectBatch()
                                    3 -> viewModel.nextFromDetails()
                                    4 -> viewModel.submit()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UtpadPrimary,
                                contentColor = Color.White,
                            ),
                            enabled = when (currentStep) {
                                1 -> !searchLoading
                                2 -> selectedLine != null
                                4 -> submitState !is SubmitState.Loading
                                else -> true
                            },
                        ) {
                            if (currentStep == 1 && searchLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                            } else {
                                Text(
                                    text = when (currentStep) {
                                        1 -> "Search"
                                        4 -> "Submit Return"
                                        else -> "Continue"
                                    },
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Step 1: Search Invoice ─────────────────────────────────────────────────

@Composable
private fun Step1SearchContent(
    invoiceNumber: String,
    onInvoiceNumberChanged: (String) -> Unit,
    searchError: String?,
) {
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
                text = "INVOICE NUMBER",
                style = MaterialTheme.typography.labelSmall,
                color = UtpadTextSecondary,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = invoiceNumber,
                onValueChange = onInvoiceNumberChanged,
                placeholder = { Text("e.g. INV-001", color = UtpadTextSecondary) },
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

            Text(
                text = "Search for a dispatched invoice to start a return. Use the invoice number exactly as it appears on the dispatch record.",
                style = MaterialTheme.typography.bodySmall,
                color = UtpadTextSecondary,
            )

            if (searchError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = UtpadError.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = searchError,
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

// ── Step 2: Select Batch Code ──────────────────────────────────────────────

@Composable
private fun Step2SelectBatchContent(
    invoice: InvoiceDto?,
    dispatchLines: List<InvoiceDispatchLine>,
    selectedLine: InvoiceDispatchLine?,
    onLineSelected: (InvoiceDispatchLine) -> Unit,
) {
    // Invoice summary card
    if (invoice != null) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = UtpadPrimary.copy(alpha = 0.06f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "INVOICE FOUND",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = invoice.invoiceNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = UtpadTextPrimary,
                    )
                    Text(
                        text = "${dispatchLines.sumOf { it.boxesDispatched }} boxes dispatched",
                        style = MaterialTheme.typography.bodySmall,
                        color = UtpadPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = invoice.customerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = UtpadTextSecondary,
                )
            }
        }
    }

    // Batch selection card
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
                text = "SELECT BATCH CODE",
                style = MaterialTheme.typography.labelSmall,
                color = UtpadTextSecondary,
                fontWeight = FontWeight.SemiBold,
            )

            if (dispatchLines.isEmpty()) {
                Text(
                    text = "No dispatch records found for this invoice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = UtpadTextSecondary,
                )
            } else {
                dispatchLines.forEach { line ->
                    val isSelected = selectedLine?.batchCode == line.batchCode &&
                        selectedLine?.flavorId == line.flavorId
                    Surface(
                        onClick = { onLineSelected(line) },
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
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = line.batchCode,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) UtpadPrimary else UtpadTextPrimary,
                                )
                                Text(
                                    text = line.flavorName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) UtpadPrimary.copy(alpha = 0.7f) else UtpadTextSecondary,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "${line.boxesDispatched} boxes",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) UtpadPrimary else UtpadTextSecondary,
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = UtpadPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Step 3: Enter Return Details ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step3DetailsContent(
    selectedLine: InvoiceDispatchLine?,
    boxesToReturn: String,
    onBoxesToReturnChanged: (String) -> Unit,
    returnReason: String,
    onReturnReasonChanged: (String) -> Unit,
    returnDate: String,
    onReturnDateChanged: (String) -> Unit,
    error: String?,
) {
    // Selected batch info (read-only summary at top)
    if (selectedLine != null) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = UtpadPrimary.copy(alpha = 0.08f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = selectedLine.batchCode,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = UtpadTextPrimary,
                    )
                    Text(
                        text = selectedLine.flavorName,
                        style = MaterialTheme.typography.bodySmall,
                        color = UtpadTextSecondary,
                    )
                }
                Text(
                    text = "${selectedLine.boxesDispatched} dispatched",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = UtpadPrimary,
                )
            }
        }
    }

    // Details form card
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = UtpadSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Boxes to return
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "BOXES TO RETURN",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = boxesToReturn,
                    onValueChange = onBoxesToReturnChanged,
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
                if (selectedLine != null) {
                    Text(
                        text = "Max: ${selectedLine.boxesDispatched} boxes from this batch",
                        style = MaterialTheme.typography.labelSmall,
                        color = UtpadTextSecondary,
                    )
                }
            }

            HorizontalDivider(color = UtpadOutline)

            // Reason
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "REASON FOR RETURN",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = returnReason,
                    onValueChange = onReturnReasonChanged,
                    placeholder = { Text("e.g. Damaged goods, customer request...", color = UtpadTextSecondary) },
                    minLines = 2,
                    maxLines = 4,
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

            HorizontalDivider(color = UtpadOutline)

            // Return date
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "RETURN DATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                var showDatePicker by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = returnDate,
                        onValueChange = {},
                        readOnly = true,
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
                        color = Color.Transparent,
                        onClick = { showDatePicker = true },
                    ) {}
                }
                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState()
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val millis = datePickerState.selectedDateMillis
                                if (millis != null) {
                                    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    onReturnDateChanged(fmt.format(Date(millis)))
                                }
                                showDatePicker = false
                            }) { Text("OK", color = UtpadPrimary) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text("Cancel", color = UtpadTextSecondary)
                            }
                        },
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
            }

            // Validation error
            if (error != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = UtpadError.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = error,
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

// ── Step 4: Review & Submit ────────────────────────────────────────────────

@Composable
private fun Step4ReviewContent(
    invoice: InvoiceDto?,
    selectedLine: InvoiceDispatchLine?,
    boxesToReturn: String,
    returnReason: String,
    returnDate: String,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = UtpadSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "RETURN SUMMARY",
                style = MaterialTheme.typography.labelSmall,
                color = UtpadTextSecondary,
                fontWeight = FontWeight.SemiBold,
            )

            ReviewRow(label = "Invoice", value = invoice?.invoiceNumber ?: "—")
            ReviewRow(label = "Customer", value = invoice?.customerName ?: "—")

            HorizontalDivider(color = UtpadOutline)

            ReviewRow(label = "Batch Code", value = selectedLine?.batchCode ?: "—")
            ReviewRow(label = "Flavour", value = selectedLine?.flavorName ?: "—")
            ReviewRow(
                label = "Boxes Returning",
                value = "${boxesToReturn} boxes",
                valueColor = UtpadError,
                valueBold = true,
            )

            HorizontalDivider(color = UtpadOutline)

            ReviewRow(
                label = "Reason",
                value = returnReason.ifBlank { "Not specified" },
            )
            ReviewRow(label = "Return Date", value = returnDate)
        }
    }

    // Warning surface
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = UtpadError.copy(alpha = 0.06f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Confirming will record the return and add these boxes back to inventory as available stock.",
            style = MaterialTheme.typography.bodySmall,
            color = UtpadError,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun ReviewRow(
    label: String,
    value: String,
    valueColor: Color = UtpadTextPrimary,
    valueBold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = UtpadTextSecondary,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (valueBold) FontWeight.Bold else FontWeight.Normal,
            color = valueColor,
            modifier = Modifier.weight(0.6f),
        )
    }
}

// ── Progress bar ───────────────────────────────────────────────────────────

@Composable
private fun ReturnsWizardProgressBar(
    currentStep: Int,
    totalSteps: Int,
    stepTitle: String,
    modifier: Modifier = Modifier,
) {
    val progress = currentStep.toFloat() / totalSteps.toFloat()
    val percentage = (progress * 100).toInt()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "STEP $currentStep OF $totalSteps",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = UtpadTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                color = UtpadTextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(UtpadOutline, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progress)
                    .height(6.dp)
                    .background(UtpadPrimary, RoundedCornerShape(3.dp)),
            )
        }
    }
}
