package com.example.gudgum_prod_flow.ui.screens.production

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gudgum_prod_flow.ui.navigation.AppRoute

@Composable
fun ModuleSelectorScreen(
    allowedRoutes: Set<String>,
    onModuleSelected: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val moduleLabels = mapOf(
        AppRoute.Inwarding  to "Inwarding",
        AppRoute.Production to "Production",
        AppRoute.Packing    to "Packing",
        AppRoute.Dispatch   to "Dispatch",
    )

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Select Module",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 32.dp),
            )

            moduleLabels.forEach { (route, label) ->
                if (route in allowedRoutes) {
                    Button(
                        onClick = { onModuleSelected(route) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(56.dp),
                    ) {
                        Text(text = label, fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = onLogout) {
                Text("Logout")
            }
        }
    }
}
