package com.example.gudgum_prod_flow.ui.screens.production

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gudgum_prod_flow.ui.theme.*
import com.example.gudgum_prod_flow.ui.viewmodels.D2CDispatchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun D2CDispatchScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: D2CDispatchViewModel = hiltViewModel(),
) {
    val step             by viewModel.step.collectAsState()
    val channels         by viewModel.channels.collectAsState()
    val selectedChannel  by viewModel.selectedChannel.collectAsState()
    val flavors          by viewModel.flavors.collectAsState()
    val qtyByFlavor      by viewModel.qtyByFlavor.collectAsState()
    val myPending        by viewModel.myPending.collectAsState()
    val preview          by viewModel.preview.collectAsState()
    val busy             by viewModel.busy.collectAsState()
    val error            by viewModel.error.collectAsState()
    val success          by viewModel.success.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error)   { error?.let   { snackbarHostState.showSnackbar(it); viewModel.clearError() } }
    LaunchedEffect(success) { success?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() } }

    var addNewChannel by remember { mutableStateOf(false) }
    var newChannelText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("D2C Dispatch", fontWeight = FontWeight.Bold, color = UtpadTextPrimary) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                // ───────── Step 1: Channel picker ─────────
                D2CDispatchViewModel.Step.Channel -> {
                    if (myPending.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = UtpadPrimary.copy(alpha = 0.07f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "Your pending requests: ${myPending.size}",
                                    fontWeight = FontWeight.SemiBold,
                                    color = UtpadTextPrimary,
                                )
                                myPending.take(3).forEach { r ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            "${r.channel} — ${r.items.size} line${if (r.items.size == 1) "" else "s"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = UtpadTextSecondary,
                                        )
                                        TextButton(onClick = { viewModel.cancelExistingRequest(r.id) }) {
                                            Text("Cancel", color = UtpadError, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "Choose channel for this dispatch:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = UtpadTextPrimary,
                    )
                    channels.forEach { ch ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.selectChannel(ch) },
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(ch, fontWeight = FontWeight.SemiBold, color = UtpadTextPrimary, modifier = Modifier.weight(1f))
                                if (busy && selectedChannel == ch) {
                                    CircularProgressIndicator(Modifier.size(20.dp), color = UtpadPrimary, strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                    if (!addNewChannel) {
                        OutlinedButton(
                            onClick = { addNewChannel = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("+ Add new channel", color = UtpadPrimary) }
                    } else {
                        OutlinedTextField(
                            value = newChannelText,
                            onValueChange = { newChannelText = it },
                            label = { Text("New channel name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { addNewChannel = false; newChannelText = "" },
                                modifier = Modifier.weight(1f),
                            ) { Text("Cancel") }
                            Button(
                                onClick = {
                                    if (newChannelText.isNotBlank()) viewModel.selectChannel(newChannelText.trim())
                                },
                                modifier = Modifier.weight(1f),
                                enabled = newChannelText.isNotBlank() && !busy,
                                colors = ButtonDefaults.buttonColors(containerColor = UtpadPrimary),
                            ) { Text("Use this channel", color = Color.White) }
                        }
                    }
                }

                // ───────── Step 2: Build request ─────────
                D2CDispatchViewModel.Step.BuildRequest -> {
                    Text(
                        "Channel: $selectedChannel",
                        style = MaterialTheme.typography.labelMedium,
                        color = UtpadTextSecondary,
                    )
                    Text(
                        "Enter boxes per flavour:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = UtpadTextPrimary,
                    )
                    flavors.forEach { f ->
                        val qty = qtyByFlavor[f.flavorId] ?: 0
                        val exceeded = qty > f.boxesAvailable
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (exceeded) UtpadError.copy(alpha = 0.07f) else UtpadSurface,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.flavorName, fontWeight = FontWeight.SemiBold, color = UtpadTextPrimary)
                                    Text(
                                        "Available: ${f.boxesAvailable} boxes",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (f.boxesAvailable > 0) UtpadTextSecondary else UtpadError,
                                    )
                                }
                                OutlinedTextField(
                                    value = if (qty == 0) "" else qty.toString(),
                                    onValueChange = { v ->
                                        val n = v.filter(Char::isDigit).toIntOrNull() ?: 0
                                        viewModel.setQty(f.flavorId, n)
                                    },
                                    label = { Text("Boxes") },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    isError = exceeded,
                                    modifier = Modifier.width(96.dp),
                                )
                            }
                            if (exceeded) {
                                Text(
                                    "Exceeds available stock",
                                    color = UtpadError,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = viewModel::submit,
                        enabled = !busy && viewModel.reviewable(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = UtpadPrimary),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Review batches", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = viewModel::reset,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cancel & start over", color = UtpadPrimary) }
                }

                // ───────── Step 3: Confirm ─────────
                D2CDispatchViewModel.Step.Confirm -> {
                    val p = preview
                    Text(
                        "FIFO preview · Channel: $selectedChannel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = UtpadTextPrimary,
                    )
                    Text(
                        "Actual deduction is recomputed at admin approval — these may differ slightly.",
                        style = MaterialTheme.typography.labelSmall,
                        color = UtpadTextSecondary,
                    )
                    p?.items?.forEach { it ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = UtpadSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                val flavorName = flavors.find { f -> f.flavorId == it.flavorId }?.flavorName ?: "(flavour)"
                                Text(
                                    "$flavorName — ${it.boxes} boxes",
                                    fontWeight = FontWeight.SemiBold,
                                    color = UtpadTextPrimary,
                                )
                                it.splits.forEach { s ->
                                    Text(
                                        "  ${s.boxes} from ${s.batchCode}${s.batchNumber?.let { n -> " #$n" } ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = UtpadTextSecondary,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = viewModel::finish,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = UtpadPrimary),
                    ) { Text("Confirm submission", color = Color.White, fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Start over", color = UtpadPrimary) }
                }

                // ───────── Done ─────────
                D2CDispatchViewModel.Step.Done -> {
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
                                "Request submitted — waiting for admin approval.",
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
                    ) { Text("New D2C dispatch", color = Color.White, fontWeight = FontWeight.SemiBold) }
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Back to Dispatch", color = UtpadPrimary) }
                }
            }
        }
    }
}
