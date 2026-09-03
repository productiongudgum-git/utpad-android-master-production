package com.example.gudgum_prod_flow.ui.screens.production

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gudgum_prod_flow.ui.theme.*

/**
 * Step 0 wrapper for AppRoute.Dispatch. Shows a Retail | D2C choice on entry;
 * Retail falls through to the existing DispatchScreen (invoice-based flow),
 * D2C goes to the new D2CDispatchScreen.
 *
 * Back button on either sub-screen returns here for re-picking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchEntryScreen(
    allowedRoutes: Set<String>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
) {
    var subFlow by rememberSaveable { mutableStateOf<SubFlow>(SubFlow.None) }

    when (subFlow) {
        SubFlow.None -> {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Dispatch", fontWeight = FontWeight.Bold, color = UtpadTextPrimary) },
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
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Dispatch type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = UtpadTextPrimary,
                    )
                    DispatchPickCard(
                        title = "Retail",
                        description = "Invoice-based dispatch to a customer",
                        onClick = { subFlow = SubFlow.Retail },
                    )
                    DispatchPickCard(
                        title = "D2C",
                        description = "Shopify, Amazon, Swiggy and similar — submit a request for admin approval",
                        onClick = { subFlow = SubFlow.D2C },
                    )
                }
            }
        }

        SubFlow.Retail -> {
            // Hand off to the existing dispatch flow. Back returns to the picker.
            DispatchScreen(
                allowedRoutes = allowedRoutes,
                onBack = { subFlow = SubFlow.None },
                onLogout = onLogout,
                onNavigateToRoute = onNavigateToRoute,
            )
        }

        SubFlow.D2C -> {
            D2CDispatchScreen(
                onBack = { subFlow = SubFlow.None },
                onLogout = onLogout,
            )
        }
    }
}

private sealed interface SubFlow : java.io.Serializable {
    object None : SubFlow
    object Retail : SubFlow
    object D2C : SubFlow
}

@Composable
private fun DispatchPickCard(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = UtpadSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = UtpadTextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(description, color = UtpadTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}
