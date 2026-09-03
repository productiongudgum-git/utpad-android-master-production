package com.example.gudgum_prod_flow.ui.screens.production

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gudgum_prod_flow.data.repository.FinishedGoodsRow
import com.example.gudgum_prod_flow.ui.theme.*
import com.example.gudgum_prod_flow.ui.viewmodels.FinishedGoodsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishedGoodsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: FinishedGoodsViewModel = hiltViewModel(),
) {
    val rows    by viewModel.rows.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error   by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Finished Goods", fontWeight = FontWeight.Bold, color = UtpadTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = UtpadTextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = UtpadPrimary)
                    }
                    TextButton(onClick = onLogout) { Text("Logout", color = UtpadPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = UtpadBackground),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = UtpadPrimary)
                }
                rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No flavours to show.", color = UtpadTextSecondary)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "${rows.size} flavours · ${rows.sumOf { it.boxesAvailable }} total boxes available",
                            style = MaterialTheme.typography.bodySmall,
                            color = UtpadTextSecondary,
                        )
                    }
                    items(rows, key = { it.flavorId }) { row -> FinishedGoodsCard(row) }
                }
            }
        }
    }
}

@Composable
private fun FinishedGoodsCard(row: FinishedGoodsRow) {
    val tint = if (row.boxesAvailable <= 0) UtpadError else UtpadPrimary
    Card(
        colors = CardDefaults.cardColors(containerColor = UtpadSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                row.flavorName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = UtpadTextPrimary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Available", style = MaterialTheme.typography.labelSmall, color = UtpadTextSecondary)
                    Text(
                        "${row.boxesAvailable} boxes",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = tint,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "↑ ${row.boxesPacked} packed",
                        style = MaterialTheme.typography.labelSmall,
                        color = UtpadTextSecondary,
                    )
                    Text(
                        "↓ ${row.boxesDispatched} dispatched",
                        style = MaterialTheme.typography.labelSmall,
                        color = UtpadTextSecondary,
                    )
                }
            }
        }
    }
}
