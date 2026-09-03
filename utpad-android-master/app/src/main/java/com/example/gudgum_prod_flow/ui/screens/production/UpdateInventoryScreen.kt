package com.example.gudgum_prod_flow.ui.screens.production

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gudgum_prod_flow.ui.theme.*
import com.example.gudgum_prod_flow.ui.viewmodels.UpdateInventoryStep
import com.example.gudgum_prod_flow.ui.viewmodels.UpdateInventoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateInventoryScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: UpdateInventoryViewModel = hiltViewModel(),
) {
    val step          by viewModel.step.collectAsState()
    val batchCode     by viewModel.batchCode.collectAsState()
    val batchNumber   by viewModel.batchNumber.collectAsState()
    val matches       by viewModel.matches.collectAsState()
    val resolved      by viewModel.resolved.collectAsState()
    val currentBoxes  by viewModel.currentBoxes.collectAsState()
    val addBoxes      by viewModel.addBoxes.collectAsState()
    val packFormats       by viewModel.packFormats.collectAsState()
    val selectedPackFormat by viewModel.selectedPackFormat.collectAsState()
    val busy          by viewModel.busy.collectAsState()
    val error         by viewModel.error.collectAsState()
    val success       by viewModel.success.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Update Inventory", fontWeight = FontWeight.Bold, color = UtpadTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = UtpadTextPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) { Text("Logout", color = UtpadPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = UtpadBackground),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                UpdateInventoryStep.EnterBatch -> {
                    Text(
                        "Find the batch you want to update.",
                        style = MaterialTheme.typography.titleMedium,
                        color = UtpadTextPrimary, fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = batchCode,
                        onValueChange = viewModel::onBatchCodeChange,
                        label = { Text("Batch code (e.g. AB0626)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = batchNumber,
                        onValueChange = viewModel::onBatchNumberChange,
                        label = { Text("Batch number (e.g. 2)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = viewModel::findBatch,
                        enabled = !busy && batchCode.isNotBlank() && batchNumber.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = UtpadPrimary),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                        else Text("Find batch", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }

                UpdateInventoryStep.PickFlavor -> {
                    Text(
                        "${matches.size} flavours produced under this batch — pick one.",
                        style = MaterialTheme.typography.titleMedium,
                        color = UtpadTextPrimary, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${matches.firstOrNull()?.batchCode ?: ""} · #${matches.firstOrNull()?.batchNumber ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UtpadTextSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    matches.forEach { m ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            onClick = { viewModel.selectFlavor(m) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        m.flavor?.name ?: "(unknown flavour)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = UtpadTextPrimary,
                                    )
                                    Text(
                                        "Tap to top up boxes for this flavour",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UtpadTextSecondary,
                                    )
                                }
                                if (busy) {
                                    CircularProgressIndicator(Modifier.size(20.dp), color = UtpadPrimary, strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::reset,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancel & start over", color = UtpadPrimary) }
                }

                UpdateInventoryStep.AddBoxes -> {
                    val match = resolved
                    Card(
                        colors = CardDefaults.cardColors(containerColor = UtpadPrimary.copy(alpha = 0.07f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${match?.batchCode} · #${match?.batchNumber}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = UtpadTextPrimary,
                            )
                            Text(
                                match?.flavor?.name ?: "—",
                                style = MaterialTheme.typography.bodyMedium,
                                color = UtpadTextSecondary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Currently packed",
                                style = MaterialTheme.typography.labelSmall,
                                color = UtpadTextSecondary,
                            )
                            Text(
                                "$currentBoxes boxes",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = UtpadPrimary,
                            )
                        }
                    }
                    // Box format — shown only when this flavour has packing
                    // variants, so a single-format flavour looks unchanged.
                    if (packFormats.size > 1) {
                        Text(
                            "HOW ARE THESE PACKED?",
                            style = MaterialTheme.typography.labelSmall,
                            color = UtpadTextSecondary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        packFormats.forEach { format ->
                            val isSelected = selectedPackFormat?.id == format.id
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) UtpadPrimary.copy(alpha = 0.12f) else UtpadSurface,
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { viewModel.onPackFormatSelected(format) },
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        if (format.isPackingVariant) format.name else "Standard — ${format.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) UtpadPrimary else UtpadTextPrimary,
                                    )
                                    Text(
                                        "${format.unitsPerBox} gums per box",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = UtpadTextSecondary,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    OutlinedTextField(
                        value = addBoxes,
                        onValueChange = viewModel::onAddBoxesChange,
                        label = { Text("Add boxes") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = viewModel::confirmTopUp,
                        enabled = !busy && (addBoxes.toIntOrNull() ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = UtpadPrimary),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
                        else Text("Confirm", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = viewModel::reset,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancel & start over", color = UtpadPrimary) }
                }

                UpdateInventoryStep.Done -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UtpadPrimary.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null, tint = UtpadPrimary, modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                success ?: "Inventory updated.",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UtpadTextPrimary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::reset,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = UtpadPrimary),
                    ) { Text("Update another batch", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Back to Packing", color = UtpadPrimary) }
                }
            }
        }
    }
}
