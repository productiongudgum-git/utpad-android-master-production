package com.example.gudgum_prod_flow.ui.screens.production

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gudgum_prod_flow.data.remote.dto.InvoiceDto
import com.example.gudgum_prod_flow.data.repository.FifoAllocationResult
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

private fun InvoiceDto.totalPackedBoxes(): Int = items.sumOf { it.packedBoxes }

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

    val redInvoices by viewModel.redInvoices.collectAsState()
    val yellowInvoices by viewModel.yellowInvoices.collectAsState()
    val blueInvoices by viewModel.blueInvoices.collectAsState()
    val invoicesLoading by viewModel.invoicesLoading.collectAsState()

    val selectedInvoice by viewModel.selectedInvoice.collectAsState()
    val multiFifoAllocations by viewModel.multiFifoAllocations.collectAsState()
    val multiFifoLoading by viewModel.multiFifoLoading.collectAsState()
    val isPacked by viewModel.isPacked.collectAsState()
    val isDispatched by viewModel.isDispatched.collectAsState()
    val dispatchDate by viewModel.dispatchDate.collectAsState()
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
                    Text(
                        text = when (screenState) {
                            DispatchScreenState.Overview -> "Dispatch"
                            DispatchScreenState.FifoWizard -> "Pack Wizard"
                            DispatchScreenState.BlueConfirm -> "Confirm Dispatch"
                        },
                        fontWeight = FontWeight.Bold,
                        color = UtpadTextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (screenState) {
                            DispatchScreenState.Overview -> onBack()
                            DispatchScreenState.FifoWizard -> {
                                if (currentStep > 1) viewModel.previousStep()
                                else viewModel.backToOverview()
                            }
                            DispatchScreenState.BlueConfirm -> viewModel.backToOverview()
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
            when (screenState) {

                // ── 3-column kanban overview ───────────────────────────────
                DispatchScreenState.Overview -> {
                    DispatchOverviewContent(
                        allowedRoutes = allowedRoutes,
                        onNavigateToRoute = onNavigateToRoute,
                        redInvoices = redInvoices,
                        yellowInvoices = yellowInvoices,
                        blueInvoices = blueInvoices,
                        isLoading = invoicesLoading,
                        onRedCardTap = { viewModel.openFifoWizard(it) },
                        onYellowCardTap = { viewModel.openFifoWizard(it) },
                        onBlueCardTap = { viewModel.openBlueConfirm(it) },
                    )
                }

                // ── 2-step FIFO wizard (RED and YELLOW invoices) ───────────
                DispatchScreenState.FifoWizard -> {
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
                            totalSteps = 2,
                            stepTitle = when (currentStep) {
                                1 -> "Stock Allocation"
                                else -> "Confirm & Pack"
                            },
                        )

                        when (currentStep) {

                            // ── Step 1: Multi-flavor FIFO table ───────────────
                            1 -> {
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
                                            text = "INVOICE: ${selectedInvoice?.invoiceNumber ?: "—"}",
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
                                            text = "FIFO STOCK ALLOCATION",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = UtpadTextSecondary,
                                            fontWeight = FontWeight.SemiBold,
                                        )

                                        if (multiFifoLoading) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = UtpadPrimary,
                                                )
                                                Text(
                                                    text = "Loading stock…",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = UtpadTextSecondary,
                                                )
                                            }
                                        } else if (multiFifoAllocations.isEmpty()) {
                                            Text(
                                                text = "No pending flavors to pack for this invoice.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = UtpadTextSecondary,
                                            )
                                        } else {
                                            multiFifoAllocations.forEach { result ->
                                                FlavorAllocationCard(result)
                                            }

                                            val noStockCount = multiFifoAllocations.count { it.allocations.isEmpty() }
                                            val partialCount = multiFifoAllocations.count { !it.isSufficient && it.allocations.isNotEmpty() }

                                            if (noStockCount > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = UtpadError.copy(alpha = 0.08f),
                                                ) {
                                                    Text(
                                                        text = "$noStockCount flavor(s) have zero stock and will be skipped.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = UtpadError,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(12.dp),
                                                    )
                                                }
                                            }
                                            if (partialCount > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = UtpadWarning.copy(alpha = 0.10f),
                                                ) {
                                                    Text(
                                                        text = "$partialCount flavor(s) have partial stock. Available boxes will be packed; invoice moves to PARTIAL (yellow).",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = UtpadWarning,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(12.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Step 2: Confirm & pack ────────────────────────
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
                                            text = "PACKING SUMMARY",
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

                                        HorizontalDivider(color = UtpadOutline)

                                        // All flavors with allocations
                                        val withStock = multiFifoAllocations.filter { it.allocations.isNotEmpty() }
                                        val noStock = multiFifoAllocations.filter { it.allocations.isEmpty() }
                                        val allSufficient = multiFifoAllocations.isNotEmpty() && multiFifoAllocations.all { it.isSufficient }

                                        if (withStock.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = UtpadSuccess.copy(alpha = 0.08f),
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    val totalBoxes = withStock.sumOf { r -> r.allocations.sumOf { it.unitsToTake } }
                                                    Text(
                                                        text = "Will pack: $totalBoxes boxes",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = UtpadSuccess,
                                                    )
                                                    withStock.forEach { r ->
                                                        val boxes = r.allocations.sumOf { it.unitsToTake }
                                                        val suffix = if (r.isSufficient) "✓" else "⚠️ ${boxes}/${r.boxesNeeded}"
                                                        Text(
                                                            text = "• ${r.flavorName} — $boxes boxes $suffix",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = if (r.isSufficient) UtpadSuccess else UtpadWarning,
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (noStock.isNotEmpty()) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = UtpadError.copy(alpha = 0.08f),
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(
                                                        text = "Will skip (no stock):",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = UtpadError,
                                                    )
                                                    noStock.forEach { r ->
                                                        Text(
                                                            text = "• ${r.flavorName} — need ${r.boxesNeeded} boxes",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = UtpadError,
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        HorizontalDivider(color = UtpadOutline)

                                        // Mark as Packed checkbox (required)
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

                                        // Mark as Dispatched checkbox (optional, requires all sufficient)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            val canDispatch = isPacked && allSufficient
                                            Checkbox(
                                                checked = isDispatched,
                                                onCheckedChange = { viewModel.onDispatchedToggle(it) },
                                                enabled = canDispatch,
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = UtpadSuccess,
                                                    disabledCheckedColor = UtpadOutline,
                                                    disabledUncheckedColor = UtpadOutline,
                                                ),
                                            )
                                            Column {
                                                Text(
                                                    text = "Mark Invoice as Dispatched",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (canDispatch) UtpadTextPrimary else UtpadTextSecondary,
                                                )
                                                Text(
                                                    text = when {
                                                        !isPacked -> "Tick Packed first"
                                                        !allSufficient -> "Unavailable — partial stock (invoice will stay yellow)"
                                                        else -> "Optional — invoice moves to BLUE if unchecked"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = when {
                                                        !isPacked -> UtpadTextSecondary
                                                        !allSufficient -> UtpadWarning
                                                        else -> UtpadTextSecondary
                                                    },
                                                )
                                            }
                                        }

                                        // Dispatch date picker (shown only when dispatched is ticked)
                                        if (isDispatched) {
                                            HorizontalDivider(color = UtpadOutline)
                                            DispatchDatePicker(
                                                date = dispatchDate,
                                                onDateSelected = { viewModel.onDispatchDateChanged(it) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

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
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = UtpadTextPrimary),
                                ) {
                                    Text("Back", fontWeight = FontWeight.Bold)
                                }

                                val canProceed = when (currentStep) {
                                    1 -> !multiFifoLoading && multiFifoAllocations.any { it.allocations.isNotEmpty() }
                                    else -> !multiFifoLoading && isPacked && submitState !is SubmitState.Loading
                                }

                                Button(
                                    onClick = {
                                        if (currentStep < 2) viewModel.nextStep() else viewModel.submit()
                                    },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = UtpadPrimary,
                                        contentColor = Color.White,
                                    ),
                                    enabled = canProceed,
                                ) {
                                    if (submitState is SubmitState.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White,
                                        )
                                    } else {
                                        Text(
                                            if (currentStep < 2) "Proceed" else "Confirm",
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Blue confirm: fully packed, dispatch only ──────────────
                DispatchScreenState.BlueConfirm -> {
                    BlueConfirmContent(
                        invoice = selectedInvoice,
                        submitState = submitState,
                        dispatchDate = dispatchDate,
                        allowedRoutes = allowedRoutes,
                        onNavigateToRoute = onNavigateToRoute,
                        onDateChange = { viewModel.onDispatchDateChanged(it) },
                        onConfirm = { viewModel.submit() },
                    )

                    if (submitState is SubmitState.Success) {
                        SuccessOverlay(onDismiss = {
                            viewModel.clearSubmitState()
                            viewModel.reset()
                        })
                    }
                }
            }
        }
    }
}

// ── Blue confirm screen: fully packed invoice, awaiting dispatch ───────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlueConfirmContent(
    invoice: InvoiceDto?,
    submitState: SubmitState,
    dispatchDate: String,
    allowedRoutes: Set<String>,
    onNavigateToRoute: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
        ) {
            OperationsModuleTabs(
                currentRoute = AppRoute.Dispatch,
                allowedRoutes = allowedRoutes,
                onNavigateToRoute = onNavigateToRoute,
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = UtpadPrimary.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Fully packed · ready to dispatch",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = UtpadPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

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
                        Text("Invoice", style = MaterialTheme.typography.bodySmall, color = UtpadTextSecondary)
                        Text(
                            text = invoice?.invoiceNumber ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = UtpadTextPrimary,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Customer", style = MaterialTheme.typography.bodySmall, color = UtpadTextSecondary)
                        Text(
                            text = invoice?.customerName ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = UtpadTextPrimary,
                        )
                    }

                    HorizontalDivider(color = UtpadOutline)

                    Text(
                        text = "PACKED ITEMS",
                        style = MaterialTheme.typography.labelSmall,
                        color = UtpadTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (invoice == null || invoice.items.isEmpty()) {
                        Text("No items", style = MaterialTheme.typography.bodySmall, color = UtpadTextSecondary)
                    } else {
                        invoice.items
                            .groupBy { it.flavorId }
                            .forEach { (_, group) ->
                                val needed = group.mapNotNull { it.quantityBoxes }.takeIf { it.isNotEmpty() }?.sum()
                                    ?: Math.ceil(group.sumOf { it.quantityUnits } / 15.0).toInt()
                                val packed = group.sumOf { it.packedBoxes }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = group.first().flavorName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = UtpadTextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "$packed/$needed boxes ✓",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = UtpadPrimary,
                                    )
                                }
                            }
                    }

                    HorizontalDivider(color = UtpadOutline)

                    Text(
                        text = "DISPATCH DATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = UtpadTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    DispatchDatePicker(date = dispatchDate, onDateSelected = onDateChange)
                }
            }
        }

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
                    enabled = submitState !is SubmitState.Loading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UtpadPrimary,
                        contentColor = Color.White,
                    ),
                ) {
                    if (submitState is SubmitState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text("Confirm Dispatch", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Dispatch date picker (shared) ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DispatchDatePicker(date: String, onDateSelected: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date,
            onValueChange = {},
            placeholder = { Text("Select date", color = UtpadTextSecondary) },
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
            onClick = { showPicker = true },
        ) {}
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        onDateSelected(fmt.format(Date(millis)))
                    }
                    showPicker = false
                }) { Text("OK", color = UtpadPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel", color = UtpadTextSecondary)
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// ── Flavor allocation card (Step 1 row) ───────────────────────────────────

@Composable
private fun FlavorAllocationCard(result: FifoAllocationResult) {
    val (borderColor, bgColor, statusLabel) = when {
        result.isSufficient -> Triple(UtpadSuccess, UtpadSuccess.copy(alpha = 0.06f), "✅")
        result.allocations.isNotEmpty() -> Triple(UtpadWarning, UtpadWarning.copy(alpha = 0.06f), "⚠️")
        else -> Triple(UtpadError, UtpadError.copy(alpha = 0.06f), "❌")
    }
    val textColor = when {
        result.isSufficient -> UtpadSuccess
        result.allocations.isNotEmpty() -> UtpadWarning
        else -> UtpadError
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$statusLabel ${result.flavorName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                )
                Text(
                    text = "${result.availableBoxes} avail / ${result.boxesNeeded} needed",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadTextSecondary,
                )
            }

            if (result.allocations.isNotEmpty()) {
                HorizontalDivider(color = borderColor.copy(alpha = 0.3f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Batch", style = MaterialTheme.typography.labelSmall, color = UtpadTextSecondary, modifier = Modifier.weight(2f))
                    Text("Avail", style = MaterialTheme.typography.labelSmall, color = UtpadTextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("Take", style = MaterialTheme.typography.labelSmall, color = UtpadTextSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
                result.allocations.forEach { alloc ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = alloc.batchCode,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = UtpadTextPrimary,
                            modifier = Modifier.weight(2f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${alloc.availableUnits}",
                            style = MaterialTheme.typography.bodySmall,
                            color = UtpadTextSecondary,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "${alloc.unitsToTake}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                        )
                    }
                }
                if (!result.isSufficient) {
                    Text(
                        text = "Will pack ${result.availableBoxes}/${result.boxesNeeded} — invoice stays PARTIAL",
                        style = MaterialTheme.typography.bodySmall,
                        color = UtpadWarning,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Text(
                    text = "No stock available — flavor will be skipped",
                    style = MaterialTheme.typography.bodySmall,
                    color = UtpadError,
                )
            }
        }
    }
}

// ── 3-column kanban overview ───────────────────────────────────────────────

@Composable
private fun DispatchOverviewContent(
    allowedRoutes: Set<String>,
    onNavigateToRoute: (String) -> Unit,
    redInvoices: List<InvoiceDto>,
    yellowInvoices: List<InvoiceDto>,
    blueInvoices: List<InvoiceDto>,
    isLoading: Boolean,
    onRedCardTap: (InvoiceDto) -> Unit,
    onYellowCardTap: (InvoiceDto) -> Unit,
    onBlueCardTap: (InvoiceDto) -> Unit,
) {
    val sortedRed = remember(redInvoices) {
        redInvoices.sortedWith(
            compareByDescending<InvoiceDto> { daysSince(it.createdAt) > 5 }
                .thenByDescending { it.createdAt ?: "" },
        )
    }
    val sortedYellow = remember(yellowInvoices) {
        yellowInvoices.sortedWith(
            compareByDescending<InvoiceDto> { daysSince(it.createdAt) > 3 }
                .thenByDescending { it.createdAt ?: "" },
        )
    }
    val sortedBlue = remember(blueInvoices) {
        blueInvoices.sortedWith(
            compareByDescending<InvoiceDto> { daysSince(it.packedAt ?: it.createdAt) > 2 }
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
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // ── RED: nothing packed ────────────────────────────────
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        ColumnHeader(label = "UNPACKED", count = sortedRed.size, color = UtpadError)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (sortedRed.isEmpty()) {
                        item { EmptyColumnHint("All packed") }
                    } else {
                        items(sortedRed, key = { it.id }) { invoice ->
                            RedInvoiceCard(invoice = invoice, onClick = { onRedCardTap(invoice) })
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // ── YELLOW: partially packed ───────────────────────────
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        ColumnHeader(label = "PARTIAL", count = sortedYellow.size, color = UtpadWarning)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (sortedYellow.isEmpty()) {
                        item { EmptyColumnHint("None partial") }
                    } else {
                        items(sortedYellow, key = { it.id }) { invoice ->
                            YellowInvoiceCard(invoice = invoice, onClick = { onYellowCardTap(invoice) })
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // ── BLUE: fully packed, awaiting dispatch ──────────────
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item {
                        ColumnHeader(label = "PACKED", count = sortedBlue.size, color = UtpadPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    if (sortedBlue.isEmpty()) {
                        item { EmptyColumnHint("None ready") }
                    } else {
                        items(sortedBlue, key = { it.id }) { invoice ->
                            BlueInvoiceCard(invoice = invoice, onClick = { onBlueCardTap(invoice) })
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
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Surface(shape = RoundedCornerShape(6.dp), color = color) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyColumnHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
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
        shape = RoundedCornerShape(14.dp),
        color = UtpadSurface,
        border = BorderStroke(
            width = if (isOverdue) 2.dp else 1.dp,
            color = if (isOverdue) UtpadError else UtpadOutline,
        ),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (isOverdue) {
                Surface(shape = RoundedCornerShape(6.dp), color = UtpadError.copy(alpha = 0.1f)) {
                    Text(
                        text = "⚠️ ${days}d",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = UtpadError,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = invoice.invoiceNumber,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = UtpadTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = invoice.customerName,
                style = MaterialTheme.typography.labelSmall,
                color = UtpadTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$totalBoxes bx",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = UtpadError,
                )
                Text(
                    text = if (days == 0) "today" else "${days}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun YellowInvoiceCard(invoice: InvoiceDto, onClick: () -> Unit) {
    val days = daysSince(invoice.createdAt)
    val isOverdue = days > 3
    val totalBoxes = invoice.totalBoxes()
    val packedBoxes = invoice.totalPackedBoxes()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = UtpadSurface,
        border = BorderStroke(
            width = if (isOverdue) 2.dp else 1.dp,
            color = if (isOverdue) UtpadWarning else UtpadOutline,
        ),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (isOverdue) {
                Surface(shape = RoundedCornerShape(6.dp), color = UtpadWarning.copy(alpha = 0.15f)) {
                    Text(
                        text = "⚠️ ${days}d",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = UtpadWarning,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = invoice.invoiceNumber,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = UtpadTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = invoice.customerName,
                style = MaterialTheme.typography.labelSmall,
                color = UtpadTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$packedBoxes/$totalBoxes bx",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = UtpadWarning,
            )
        }
    }
}

@Composable
private fun BlueInvoiceCard(invoice: InvoiceDto, onClick: () -> Unit) {
    val packedRef = invoice.packedAt ?: invoice.createdAt
    val days = daysSince(packedRef)
    val isOverdue = days > 2
    val totalBoxes = invoice.totalBoxes()

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = UtpadSurface,
        border = BorderStroke(
            width = if (isOverdue) 2.dp else 1.dp,
            color = if (isOverdue) UtpadPrimary else UtpadOutline,
        ),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (isOverdue) {
                Surface(shape = RoundedCornerShape(6.dp), color = UtpadPrimary.copy(alpha = 0.12f)) {
                    Text(
                        text = "⏳ ${days}d",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = UtpadPrimary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = invoice.invoiceNumber,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = UtpadTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = invoice.customerName,
                style = MaterialTheme.typography.labelSmall,
                color = UtpadTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$totalBoxes bx",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = UtpadPrimary,
                )
                Text(
                    text = if (days == 0) "today" else "${days}d",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadTextSecondary,
                )
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
