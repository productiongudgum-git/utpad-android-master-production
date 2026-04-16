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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceDto
import com.example.gudgum_prod_flow.ui.components.SuccessOverlay
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import com.example.gudgum_prod_flow.ui.theme.UtpadBackground
import com.example.gudgum_prod_flow.ui.theme.UtpadError
import com.example.gudgum_prod_flow.ui.theme.UtpadOutline
import com.example.gudgum_prod_flow.ui.theme.UtpadPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadSuccess
import com.example.gudgum_prod_flow.ui.theme.UtpadSurface
import com.example.gudgum_prod_flow.ui.theme.UtpadTextPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadTextSecondary
import com.example.gudgum_prod_flow.ui.theme.UtpadWarning
import com.example.gudgum_prod_flow.ui.viewmodels.DispatchScreenState
import com.example.gudgum_prod_flow.ui.viewmodels.DispatchViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.SubmitState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Helpers ────────────────────────────────────────────────────────────────

private fun daysSince(isoString: String?): Int {
    if (isoString == null) return 0
    return try {
        val zdt = java.time.ZonedDateTime.parse(isoString.trim())
        val now = java.time.ZonedDateTime.now()
        java.time.temporal.ChronoUnit.DAYS.between(zdt, now).toInt().coerceAtLeast(0)
    } catch (_: Exception) { 0 }
}

private fun InvoiceDto.totalBoxes(): Int =
    items.sumOf { item ->
        if (item.quantityBoxes != null && item.quantityBoxes > 0) item.quantityBoxes
        else Math.ceil(item.quantityUnits / 15.0).toInt()
    }

// ── Main screen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchScreen(
    allowedRoutes: Set<String>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    viewModel: DispatchViewModel = hiltViewModel(),
) {
    val screenState by viewModel.screenState.collectAsState()

    // Overview lists
    val redInvoices by viewModel.redInvoices.collectAsState()
    val yellowInvoices by viewModel.yellowInvoices.collectAsState()
    val invoicesLoading by viewModel.invoicesLoading.collectAsState()
    val yellowDispatchLoading by viewModel.yellowDispatchLoading.collectAsState()

    // Wizard state
    val invoices by viewModel.invoices.collectAsState()
    val selectedInvoice by viewModel.selectedInvoice.collectAsState()
    val invoiceItems by viewModel.invoiceItems.collectAsState()
    val selectedItem by viewModel.selectedItem.collectAsState()
    val boxesToDispatch by viewModel.boxesToDispatch.collectAsState()
    val fifoLines by viewModel.fifoLines.collectAsState()
    val fifoError by viewModel.fifoError.collectAsState()
    val isPacked by viewModel.isPacked.collectAsState()
    val isDispatched by viewModel.isDispatched.collectAsState()
    val dispatchDate by viewModel.dispatchDate.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val currentStep by viewModel.currentWizardStep.collectAsState()
    val invoiceAlreadyPacked by viewModel.invoiceAlreadyPacked.collectAsState()
    val stockCheckError by viewModel.stockCheckError.collectAsState()
    val stockCheckLoading by viewModel.stockCheckLoading.collectAsState()

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
                    Text(
                        text = when (screenState) {
                            DispatchScreenState.Overview -> "Dispatch"
                            DispatchScreenState.RedWizard -> "Dispatch Wizard"
                            is DispatchScreenState.YellowConfirm -> "Confirm Dispatch"
                        },
                        fontWeight = FontWeight.Bold,
                        color = UtpadTextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (screenState) {
                            DispatchScreenState.Overview -> onBack()
                            DispatchScreenState.RedWizard -> {
                                if (currentStep > 1) viewModel.previousStep()
                                else viewModel.backToOverview()
                            }
                            is DispatchScreenState.YellowConfirm -> viewModel.backToOverview()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = UtpadTextPrimary,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = UtpadPrimary)
                    }
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
            when (val state = screenState) {

                // ── Step 0: 2-column kanban overview ──────────────────────────
                DispatchScreenState.Overview -> {
                    DispatchOverviewContent(
                        allowedRoutes = allowedRoutes,
                        onNavigateToRoute = onNavigateToRoute,
                        redInvoices = redInvoices,
                        yellowInvoices = yellowInvoices,
                        isLoading = invoicesLoading,
                        onRedCardTap = { viewModel.openRedWizard(it) },
                        onYellowCardTap = { viewModel.openYellowConfirm(it) },
                    )
                }

                // ── Steps 1-5: existing FIFO dispatch wizard ───────────────────
                DispatchScreenState.RedWizard -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 132.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
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
                            },
                        )

                        when (currentStep) {
                            // ── Step 1: Invoice Selection ──────────────────────────
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
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (invoicesLoading) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.height(20.dp).width(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = UtpadPrimary,
                                                )
                                                Text("Loading invoices...", color = UtpadTextSecondary, style = MaterialTheme.typography.bodySmall)
                                            }
                                        } else if (invoices.isEmpty()) {
                                            Text(
                                                text = "No pending invoices found.",
                                                color = UtpadTextSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.height(300.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                items(invoices) { invoice ->
                                                    val isSelected = selectedInvoice?.id == invoice.id
                                                    val totalBoxes = invoice.totalBoxes()
                                                    Surface(
                                                        onClick = { viewModel.onInvoiceSelected(invoice) },
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
                                                                    text = invoice.invoiceNumber,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isSelected) UtpadPrimary else UtpadTextPrimary,
                                                                )
                                                                Text(
                                                                    text = invoice.customerName,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = if (isSelected) UtpadPrimary.copy(alpha = 0.7f) else UtpadTextSecondary,
                                                                )
                                                            }
                                                            Column(horizontalAlignment = Alignment.End) {
                                                                Text(
                                                                    text = "$totalBoxes boxes",
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
                            }

                            // ── Step 2: Customer Info ──────────────────────────────
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
                                            fontWeight = FontWeight.SemiBold,
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
                                            fontWeight = FontWeight.SemiBold,
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
                                                        text = "${item.resolvedBoxes} boxes",
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

                            // ── Step 3: Flavour Selection ──────────────────────────
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
                                            fontWeight = FontWeight.SemiBold,
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
                                                        text = "${item.resolvedBoxes} boxes",
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

                            // ── Step 4: Units & FIFO Allocation ───────────────────
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
                                            fontWeight = FontWeight.SemiBold,
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

                                        if (fifoLines.isNotEmpty()) {
                                            HorizontalDivider(color = UtpadOutline)
                                            Text(
                                                text = "FIFO ALLOCATION (oldest first)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = UtpadTextSecondary,
                                                fontWeight = FontWeight.SemiBold,
                                            )

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

                            // ── Step 5: Confirm & Status ───────────────────────────
                            5 -> {
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
                                            fontWeight = FontWeight.SemiBold,
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
                                        if (invoiceAlreadyPacked) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = UtpadSuccess.copy(alpha = 0.12f),
                                            ) {
                                                Text(
                                                    text = "Already packed — confirm dispatch only",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = UtpadSuccess,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                )
                                            }
                                        } else {
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
                                        }

                                        HorizontalDivider(color = UtpadOutline)

                                        Column {
                                            Text(
                                                text = "DISPATCH DATE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = UtpadTextSecondary,
                                                fontWeight = FontWeight.SemiBold,
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
                                                    onClick = { showDatePicker = true },
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
                                                    },
                                                ) {
                                                    DatePicker(state = datePickerState)
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = UtpadOutline)

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Checkbox(
                                                checked = isPacked,
                                                onCheckedChange = { viewModel.onPackedToggle(it) },
                                                colors = CheckboxDefaults.colors(checkedColor = UtpadPrimary),
                                            )
                                            Column {
                                                Text(
                                                    text = "Mark Invoice as Packed",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = UtpadTextPrimary,
                                                )
                                                if (!isPacked) {
                                                    Text(
                                                        text = "Required before submission",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = UtpadError,
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Checkbox(
                                                checked = isDispatched,
                                                onCheckedChange = { viewModel.onDispatchedToggle(it) },
                                                enabled = isPacked,
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = UtpadSuccess,
                                                    disabledCheckedColor = UtpadOutline,
                                                    disabledUncheckedColor = UtpadOutline,
                                                ),
                                            )
                                            Text(
                                                text = "Mark Invoice as Dispatched",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isPacked) UtpadTextPrimary else UtpadTextSecondary,
                                            )
                                        }

                                        if (stockCheckLoading) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.height(16.dp).width(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = UtpadPrimary,
                                                )
                                                Text(
                                                    text = "Checking stock availability…",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = UtpadTextSecondary,
                                                )
                                            }
                                        }
                                        if (stockCheckError != null) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = UtpadError.copy(alpha = 0.1f),
                                            ) {
                                                Text(
                                                    text = stockCheckError!!,
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
                        }
                    }

                    // Success overlay — dismisses after 2 s and resets wizard to overview.
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
                                        if (currentStep > 1) viewModel.previousStep()
                                        else viewModel.backToOverview()
                                    },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = UtpadTextPrimary,
                                    ),
                                ) {
                                    Text("Back", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        if (currentStep < 5) viewModel.nextStep() else viewModel.submit()
                                    },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = UtpadPrimary,
                                        contentColor = Color.White,
                                    ),
                                    enabled = submitState !is SubmitState.Loading
                                        && !stockCheckLoading
                                        && (currentStep < 5 || stockCheckError == null),
                                ) {
                                    Text(
                                        if (currentStep < 5) "Continue" else "Confirm Dispatch",
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Yellow confirmation screen ─────────────────────────────
                is DispatchScreenState.YellowConfirm -> {
                    YellowConfirmContent(
                        invoice = state.invoice,
                        isLoading = yellowDispatchLoading,
                        allowedRoutes = allowedRoutes,
                        onNavigateToRoute = onNavigateToRoute,
                        onConfirm = { viewModel.confirmYellowDispatch(state.invoice) },
                    )
                }
            }
        }
    }
}

// ── 2-column kanban overview ───────────────────────────────────────────────

@Composable
private fun DispatchOverviewContent(
    allowedRoutes: Set<String>,
    onNavigateToRoute: (String) -> Unit,
    redInvoices: List<InvoiceDto>,
    yellowInvoices: List<InvoiceDto>,
    isLoading: Boolean,
    onRedCardTap: (InvoiceDto) -> Unit,
    onYellowCardTap: (InvoiceDto) -> Unit,
) {
    val redColor = UtpadError
    val yellowColor = UtpadWarning

    val sortedRed = remember(redInvoices) {
        redInvoices.sortedWith(
            compareByDescending<InvoiceDto> { daysSince(it.createdAt) > 5 }
                .thenByDescending { it.createdAt ?: "" },
        )
    }
    val sortedYellow = remember(yellowInvoices) {
        yellowInvoices.sortedWith(
            compareByDescending<InvoiceDto> { daysSince(it.packedAt ?: it.createdAt) > 3 }
                .thenByDescending { it.createdAt ?: "" },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OperationsModuleTabs(
            currentRoute = AppRoute.Dispatch,
            allowedRoutes = allowedRoutes,
            onNavigateToRoute = onNavigateToRoute,
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = UtpadPrimary)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ── RED column ─────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        ColumnHeader(
                            label = "NOT PACKED",
                            count = sortedRed.size,
                            color = redColor,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (sortedRed.isEmpty()) {
                        item {
                            EmptyColumnHint("All invoices packed")
                        }
                    } else {
                        items(sortedRed, key = { it.id }) { invoice ->
                            RedInvoiceCard(invoice = invoice, onClick = { onRedCardTap(invoice) })
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // ── YELLOW column ──────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        ColumnHeader(
                            label = "PACKED",
                            count = sortedYellow.size,
                            color = yellowColor,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (sortedYellow.isEmpty()) {
                        item {
                            EmptyColumnHint("Nothing packed yet")
                        }
                    } else {
                        items(sortedYellow, key = { it.id }) { invoice ->
                            YellowInvoiceCard(invoice = invoice, onClick = { onYellowCardTap(invoice) })
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ColumnHeader(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = color,
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyColumnHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = UtpadTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RedInvoiceCard(invoice: InvoiceDto, onClick: () -> Unit) {
    val days = daysSince(invoice.createdAt)
    val isOverdue = days > 5
    val totalBoxes = invoice.totalBoxes()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = UtpadSurface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isOverdue) 2.dp else 1.dp,
            color = if (isOverdue) UtpadError else UtpadOutline,
        ),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isOverdue) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = UtpadError.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = "⚠️ $days days overdue",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = UtpadError,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = invoice.invoiceNumber,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = UtpadTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = invoice.customerName,
                style = MaterialTheme.typography.bodySmall,
                color = UtpadTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$totalBoxes boxes",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = UtpadError,
                )
                Text(
                    text = if (days == 0) "today" else "${days}d ago",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun YellowInvoiceCard(invoice: InvoiceDto, onClick: () -> Unit) {
    val packedRef = invoice.packedAt ?: invoice.createdAt
    val days = daysSince(packedRef)
    val isOverdue = days > 3
    val totalBoxes = invoice.totalBoxes()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = UtpadSurface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isOverdue) 2.dp else 1.dp,
            color = if (isOverdue) UtpadWarning else UtpadOutline,
        ),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isOverdue) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = UtpadWarning.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "⚠️ $days days since packed",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = UtpadWarning,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = invoice.invoiceNumber,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = UtpadTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = invoice.customerName,
                style = MaterialTheme.typography.bodySmall,
                color = UtpadTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$totalBoxes boxes",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = UtpadWarning,
                )
                Text(
                    text = if (days == 0) "packed today" else "packed ${days}d ago",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadTextSecondary,
                )
            }
        }
    }
}

// ── Yellow confirmation screen ─────────────────────────────────────────────

@Composable
private fun YellowConfirmContent(
    invoice: InvoiceDto,
    isLoading: Boolean,
    allowedRoutes: Set<String>,
    onNavigateToRoute: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
        ) {
            OperationsModuleTabs(
                currentRoute = AppRoute.Dispatch,
                allowedRoutes = allowedRoutes,
                onNavigateToRoute = onNavigateToRoute,
            )

            // Packed badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = UtpadWarning.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Packed · ready to dispatch",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = UtpadWarning,
                    )
                }
            }

            // Invoice details card
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
                        text = "INVOICE DETAILS",
                        style = MaterialTheme.typography.labelSmall,
                        color = UtpadTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Invoice",
                            style = MaterialTheme.typography.bodySmall,
                            color = UtpadTextSecondary,
                        )
                        Text(
                            text = invoice.invoiceNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = UtpadTextPrimary,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Customer",
                            style = MaterialTheme.typography.bodySmall,
                            color = UtpadTextSecondary,
                        )
                        Text(
                            text = invoice.customerName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = UtpadTextPrimary,
                        )
                    }

                    HorizontalDivider(color = UtpadOutline)

                    Text(
                        text = "ITEMS",
                        style = MaterialTheme.typography.labelSmall,
                        color = UtpadTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (invoice.items.isEmpty()) {
                        Text(
                            text = "No items",
                            style = MaterialTheme.typography.bodySmall,
                            color = UtpadTextSecondary,
                        )
                    } else {
                        // Merge by flavor (same logic as ViewModel)
                        val mergedItems = invoice.items
                            .groupBy { it.flavorId }
                            .map { (_, group) ->
                                val boxes = group.mapNotNull { it.quantityBoxes }.takeIf { it.isNotEmpty() }?.sum()
                                    ?: Math.ceil(group.sumOf { it.quantityUnits } / 15.0).toInt()
                                Pair(group.first().flavorName, boxes)
                            }

                        mergedItems.forEach { (name, boxes) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UtpadTextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "$boxes boxes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = UtpadWarning,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Confirm Dispatch button pinned to bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shadowElevation = 20.dp,
            color = UtpadSurface,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Button(
                    onClick = onConfirm,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UtpadWarning,
                        contentColor = Color.White,
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text("Confirm Dispatch", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Wizard progress bar ────────────────────────────────────────────────────

@Composable
private fun WizardProgressBar(
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
