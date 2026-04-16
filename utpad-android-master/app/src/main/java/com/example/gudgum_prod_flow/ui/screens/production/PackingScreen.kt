package com.example.gudgum_prod_flow.ui.screens.production

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gudgum_prod_flow.data.remote.dto.ProductionBatchWithPackingDto
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import com.example.gudgum_prod_flow.ui.theme.UtpadBackground
import com.example.gudgum_prod_flow.ui.theme.UtpadOutline
import com.example.gudgum_prod_flow.ui.theme.UtpadPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadSurface
import com.example.gudgum_prod_flow.ui.theme.UtpadTextPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadTextSecondary
import com.example.gudgum_prod_flow.ui.viewmodels.PackingStatus
import com.example.gudgum_prod_flow.ui.viewmodels.PackingViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.SubmitState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackingScreen(
    allowedRoutes: Set<String>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    viewModel: PackingViewModel = hiltViewModel(),
) {
    val selectedPackingStatus by viewModel.selectedPackingStatus.collectAsState()
    val completeBatches by viewModel.completeBatches.collectAsState()
    val partialBatches by viewModel.partialBatches.collectAsState()
    val unpackedBatches by viewModel.unpackedBatches.collectAsState()
    val batchesLoading by viewModel.batchesLoading.collectAsState()
    val selectedBatch by viewModel.selectedBatch.collectAsState()
    val isFromPartialList by viewModel.isFromPartialList.collectAsState()
    val selectedFinalStatus by viewModel.selectedFinalStatus.collectAsState()
    val boxesMade by viewModel.boxesMade.collectAsState()
    val packingDate by viewModel.packingDate.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val currentStep by viewModel.currentWizardStep.collectAsState()

    val unitsPacked = boxesMade.toIntOrNull()?.let { it * 15 }

    // True when the worker is on PATH B and selected a batch from the left (partial) column.
    // In this case step 3 shows the "is packing now complete?" status question.
    val showStatusQuestion = selectedPackingStatus == PackingStatus.Partial && isFromPartialList

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
                    Text("Packing Wizard", fontWeight = FontWeight.Bold, color = UtpadTextPrimary)
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
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
            ) {
                OperationsModuleTabs(
                    currentRoute = AppRoute.Packing,
                    allowedRoutes = allowedRoutes,
                    onNavigateToRoute = onNavigateToRoute,
                )

                WizardProgressBar(
                    currentStep = currentStep,
                    totalSteps = 3,
                    stepTitle = when (currentStep) {
                        1 -> "Packing Status"
                        2 -> "Select Batch"
                        else -> if (showStatusQuestion) "Outcome & Boxes" else "Boxes & Date"
                    }
                )

                when (currentStep) {
                    // ── Step 1: Packing status selection ──────────────────────
                    1 -> PackingStatusStep(
                        selectedStatus = selectedPackingStatus,
                        onStatusSelected = viewModel::onPackingStatusSelected,
                    )

                    // ── Step 2: Batch selection ───────────────────────────────
                    2 -> when (selectedPackingStatus) {
                        // PATH A: single flat list — all batches without a complete session
                        PackingStatus.Complete -> CompleteBatchListStep(
                            batches = completeBatches,
                            selectedBatch = selectedBatch,
                            loading = batchesLoading,
                            onBatchSelected = { batch -> viewModel.onBatchSelected(batch) },
                        )
                        // PATH B: two-column list (partial/null on left, unpacked on right)
                        PackingStatus.Partial -> PartialBatchListStep(
                            partialBatches = partialBatches,
                            unpackedBatches = unpackedBatches,
                            selectedBatch = selectedBatch,
                            loading = batchesLoading,
                            onBatchSelectedFromPartial = { batch ->
                                viewModel.onBatchSelected(batch, fromPartialList = true)
                            },
                            onBatchSelectedFromUnpacked = { batch ->
                                viewModel.onBatchSelected(batch, fromPartialList = false)
                            },
                        )
                        null -> {}
                    }

                    // ── Step 3: Boxes packed + packing date ───────────────────
                    // When showStatusQuestion == true (PATH B, left column batch),
                    // an inline "is packing now complete?" question is shown first.
                    3 -> PackingOutputStep(
                        selectedBatch = selectedBatch,
                        boxesMade = boxesMade,
                        unitsPacked = unitsPacked,
                        packingDate = packingDate,
                        onBoxesMadeChanged = viewModel::onBoxesMadeChanged,
                        onPackingDateChanged = viewModel::onPackingDateChanged,
                        showStatusQuestion = showStatusQuestion,
                        selectedFinalStatus = selectedFinalStatus,
                        onFinalStatusSelected = viewModel::onFinalStatusSelected,
                    )
                }
            }

            // ── Bottom action bar ─────────────────────────────────────────────
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
                                if (currentStep > 1) viewModel.previousStep() else viewModel.clear()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = UtpadTextPrimary),
                        ) {
                            Text(if (currentStep > 1) "Back" else "Reset", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (currentStep < 3) viewModel.nextStep() else viewModel.submit()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UtpadPrimary,
                                contentColor = Color.White,
                            ),
                            enabled = submitState !is SubmitState.Loading && when (currentStep) {
                                1 -> selectedPackingStatus != null
                                2 -> selectedBatch != null
                                else -> {
                                    // Step 3: also require the status question to be answered
                                    // when the batch came from the partial (left) column.
                                    !showStatusQuestion || selectedFinalStatus != null
                                }
                            },
                        ) {
                            if (submitState is SubmitState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                            } else {
                                Text(
                                    if (currentStep < 3) "Continue" else "Confirm & Submit",
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

// ── Step 1: Packing Status Selection ─────────────────────────────────────────

@Composable
private fun PackingStatusStep(
    selectedStatus: PackingStatus?,
    onStatusSelected: (PackingStatus) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "How was the packing done?",
            style = MaterialTheme.typography.titleMedium,
            color = UtpadTextPrimary,
            fontWeight = FontWeight.SemiBold,
        )

        PackingStatusCard(
            title = "Packing Complete",
            description = "Worker has fully packed this batch",
            selected = selectedStatus == PackingStatus.Complete,
            onClick = { onStatusSelected(PackingStatus.Complete) },
        )

        PackingStatusCard(
            title = "Yet to Finish Packing",
            description = "Worker has partially packed this batch",
            selected = selectedStatus == PackingStatus.Partial,
            onClick = { onStatusSelected(PackingStatus.Partial) },
        )
    }
}

@Composable
private fun PackingStatusCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) UtpadPrimary else UtpadOutline
    val bgColor = if (selected) UtpadPrimary.copy(alpha = 0.07f) else UtpadSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp),
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) UtpadPrimary else UtpadTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = UtpadTextSecondary,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = UtpadPrimary,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(28.dp),
                )
            }
        }
    }
}

// ── Step 2a (PATH A): single flat list ────────────────────────────────────────
// Shows ALL batches that have no complete packing session yet
// (includes both unpacked batches and batches with partial/null sessions).

@Composable
private fun CompleteBatchListStep(
    batches: List<ProductionBatchWithPackingDto>,
    selectedBatch: ProductionBatchWithPackingDto?,
    loading: Boolean,
    onBatchSelected: (ProductionBatchWithPackingDto) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "SELECT BATCH TO COMPLETE PACKING",
            style = MaterialTheme.typography.labelSmall,
            color = UtpadTextSecondary,
            fontWeight = FontWeight.SemiBold,
        )

        if (loading) {
            LoadingRow()
        } else if (batches.isEmpty()) {
            EmptyBatchesNote("All batches have been fully packed.")
        } else {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column {
                    batches.forEachIndexed { index, batch ->
                        BatchListItem(
                            batch = batch,
                            selected = selectedBatch?.id == batch.id,
                            onClick = { onBatchSelected(batch) },
                        )
                        if (index < batches.lastIndex) {
                            Divider(color = UtpadOutline.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

// ── Step 2b (PATH B): two-column list ─────────────────────────────────────────
// LEFT  (onBatchSelectedFromPartial)  → batch has partial/null sessions, no complete session
// RIGHT (onBatchSelectedFromUnpacked) → batch has NO sessions at all

@Composable
private fun PartialBatchListStep(
    partialBatches: List<ProductionBatchWithPackingDto>,
    unpackedBatches: List<ProductionBatchWithPackingDto>,
    selectedBatch: ProductionBatchWithPackingDto?,
    loading: Boolean,
    onBatchSelectedFromPartial: (ProductionBatchWithPackingDto) -> Unit,
    onBatchSelectedFromUnpacked: (ProductionBatchWithPackingDto) -> Unit,
) {
    if (loading) {
        LoadingRow()
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Left: partially packed (has partial/null sessions, no complete session)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "PARTIALLY PACKED",
                style = MaterialTheme.typography.labelSmall,
                color = UtpadPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            if (partialBatches.isEmpty()) {
                EmptyBatchesNote("No partially packed batches.")
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Column {
                        partialBatches.forEachIndexed { index, batch ->
                            BatchListItem(
                                batch = batch,
                                selected = selectedBatch?.id == batch.id,
                                onClick = { onBatchSelectedFromPartial(batch) },
                                compact = true,
                            )
                            if (index < partialBatches.lastIndex) {
                                Divider(color = UtpadOutline.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }

        // Column divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(UtpadOutline.copy(alpha = 0.4f))
        )

        // Right: not yet started (no packing sessions at all)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "NOT YET STARTED",
                style = MaterialTheme.typography.labelSmall,
                color = UtpadTextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
            if (unpackedBatches.isEmpty()) {
                EmptyBatchesNote("No unstarted batches.")
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Column {
                        unpackedBatches.forEachIndexed { index, batch ->
                            BatchListItem(
                                batch = batch,
                                selected = selectedBatch?.id == batch.id,
                                onClick = { onBatchSelectedFromUnpacked(batch) },
                                compact = true,
                            )
                            if (index < unpackedBatches.lastIndex) {
                                Divider(color = UtpadOutline.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Step 3: Boxes packed + packing date ──────────────────────────────────────
// When showStatusQuestion == true, an inline "is packing now complete?" question
// is shown at the top before the boxes/date inputs.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PackingOutputStep(
    selectedBatch: ProductionBatchWithPackingDto?,
    boxesMade: String,
    unitsPacked: Int?,
    packingDate: String,
    onBoxesMadeChanged: (String) -> Unit,
    onPackingDateChanged: (String) -> Unit,
    showStatusQuestion: Boolean,
    selectedFinalStatus: PackingStatus?,
    onFinalStatusSelected: (PackingStatus) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // Selected batch summary
        selectedBatch?.let { batch ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = UtpadPrimary.copy(alpha = 0.07f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "SELECTED BATCH",
                        style = MaterialTheme.typography.labelSmall,
                        color = UtpadPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = batch.displayLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = UtpadTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Status question — PATH B, left column only.
        // Worker explicitly chooses whether this session completes the batch or not.
        if (showStatusQuestion) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "IS PACKING NOW COMPLETE FOR THIS BATCH?",
                    style = MaterialTheme.typography.labelSmall,
                    color = UtpadTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                PackingStatusCard(
                    title = "Packing Complete",
                    description = "Batch is now fully packed",
                    selected = selectedFinalStatus == PackingStatus.Complete,
                    onClick = { onFinalStatusSelected(PackingStatus.Complete) },
                )
                PackingStatusCard(
                    title = "Yet to Finish",
                    description = "Still more packing to do on this batch",
                    selected = selectedFinalStatus == PackingStatus.Partial,
                    onClick = { onFinalStatusSelected(PackingStatus.Partial) },
                )
            }
        }

        // Boxes packed + packing date card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Boxes packed input
                Column {
                    Text(
                        text = "BOXES PACKED",
                        style = MaterialTheme.typography.labelSmall,
                        color = UtpadTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = boxesMade,
                        onValueChange = onBoxesMadeChanged,
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
                }

                if (unitsPacked != null && unitsPacked > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = UtpadPrimary.copy(alpha = 0.1f),
                    ) {
                        Text(
                            text = "= $unitsPacked units ($boxesMade boxes × 15 gums/box)",
                            style = MaterialTheme.typography.bodySmall,
                            color = UtpadPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }

                // Packing date
                Column {
                    Text(
                        text = "PACKING DATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = UtpadTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    var showDatePicker by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = packingDate,
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
                                        onPackingDateChanged(formatter.format(Date(selectedMillis)))
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
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun BatchListItem(
    batch: ProductionBatchWithPackingDto,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val bgColor = if (selected) UtpadPrimary.copy(alpha = 0.09f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${batch.batchCode} — Batch ${batch.batchNumber ?: "?"}",
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = if (selected) UtpadPrimary else UtpadTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${batch.flavor?.name?.uppercase() ?: "—"} · ${batch.expectedBoxes ?: "?"} expected boxes",
                style = MaterialTheme.typography.bodySmall,
                color = UtpadTextSecondary,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = UtpadPrimary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp),
            )
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = UtpadPrimary,
        )
        Text("Loading batches...", color = UtpadTextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyBatchesNote(message: String) {
    Text(
        text = message,
        color = UtpadTextSecondary,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    )
}

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
