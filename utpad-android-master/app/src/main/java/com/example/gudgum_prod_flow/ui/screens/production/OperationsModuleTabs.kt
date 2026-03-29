package com.example.gudgum_prod_flow.ui.screens.production

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.PrecisionManufacturing
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.gudgum_prod_flow.ui.navigation.AppRoute

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.gudgum_prod_flow.ui.theme.UtpadPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadSurface
import com.example.gudgum_prod_flow.ui.theme.UtpadTextPrimary

private data class ModuleTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val moduleTabs = listOf(
    ModuleTab(AppRoute.Inwarding, "Inwarding", Icons.Outlined.Inventory2),
    ModuleTab(AppRoute.Production, "Production", Icons.Outlined.PrecisionManufacturing),
    ModuleTab(AppRoute.Packing, "Packing", Icons.Outlined.Widgets),
    ModuleTab(AppRoute.Dispatch, "Dispatch", Icons.Outlined.LocalShipping),
)

@Composable
fun OperationsModuleTabs(
    currentRoute: String,
    allowedRoutes: Set<String>,
    onNavigateToRoute: (String) -> Unit,
) {
    val visibleTabs = moduleTabs.filter { it.route in allowedRoutes }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(visibleTabs) { tab ->
            val isSelected = currentRoute == tab.route
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) UtpadPrimary else UtpadSurface)
                    .clickable {
                        if (!isSelected) {
                            onNavigateToRoute(tab.route)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (isSelected) Color.White else UtpadTextPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) Color.White else UtpadTextPrimary
                    )
                }
            }
        }
    }
}
