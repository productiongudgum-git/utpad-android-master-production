package com.example.gudgum_prod_flow.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.gudgum_prod_flow.ui.screens.auth.PinResetScreen
import com.example.gudgum_prod_flow.ui.screens.auth.WorkerLoginScreen
import com.example.gudgum_prod_flow.ui.screens.production.DispatchScreen
import com.example.gudgum_prod_flow.ui.screens.production.InwardingScreen
import com.example.gudgum_prod_flow.ui.screens.production.ModuleSelectorScreen
import com.example.gudgum_prod_flow.ui.screens.production.PackingScreen
import com.example.gudgum_prod_flow.ui.screens.production.ProductionScreen
import com.example.gudgum_prod_flow.ui.viewmodels.AuthViewModel

@Composable
fun UtpadNavGraph(navController: NavHostController) {
    val authViewModel: AuthViewModel = viewModel()
    val workerSession by authViewModel.workerSession.collectAsState()
    val allowedRoutes = workerSession?.authorizedRoutes ?: emptySet()

    fun navigateToAuthorizedRoute(route: String) {
        if (route in allowedRoutes) {
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    fun logoutAndNavigateToLogin() {
        authViewModel.logout()
        navController.navigate(AppRoute.WorkerLogin) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.WorkerLogin,
    ) {
        composable(AppRoute.WorkerLogin) {
            WorkerLoginScreen(
                onLoginSuccess = { authorizedRoute ->
                    // If worker has multiple modules, go to module selector
                    val destination = if (allowedRoutes.size > 1) AppRoute.ModuleSelector else authorizedRoute
                    navController.navigate(destination) {
                        popUpTo(AppRoute.WorkerLogin) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                authViewModel = authViewModel,
            )
        }

        composable(AppRoute.PinReset) {
            PinResetScreen(onBackPressed = { navController.popBackStack() })
        }

        composable(AppRoute.ModuleSelector) {
            // Module selector: shown when worker has 2+ modules
            LaunchedEffect(workerSession) {
                if (workerSession == null) {
                    navController.navigate(AppRoute.WorkerLogin) { launchSingleTop = true }
                }
            }
            // Inline composable — simple screen showing allowed module buttons
            ModuleSelectorScreen(
                allowedRoutes = allowedRoutes,
                onModuleSelected = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                },
                onLogout = ::logoutAndNavigateToLogin,
            )
        }

        // Module screens — all guard against missing session and unauthorized access
        listOf<Pair<String, @Composable () -> Unit>>(
            AppRoute.Inwarding to { InwardingScreen(allowedRoutes = allowedRoutes, onBack = { navController.navigate(AppRoute.ModuleSelector) { launchSingleTop = true } }, onLogout = ::logoutAndNavigateToLogin, onNavigateToRoute = ::navigateToAuthorizedRoute) },
            AppRoute.Production to { ProductionScreen(allowedRoutes = allowedRoutes, onBack = { navController.navigate(AppRoute.ModuleSelector) { launchSingleTop = true } }, onLogout = ::logoutAndNavigateToLogin, onNavigateToRoute = ::navigateToAuthorizedRoute) },
            AppRoute.Packing to { PackingScreen(allowedRoutes = allowedRoutes, onBack = { navController.navigate(AppRoute.ModuleSelector) { launchSingleTop = true } }, onLogout = ::logoutAndNavigateToLogin, onNavigateToRoute = ::navigateToAuthorizedRoute) },
            AppRoute.Dispatch to { DispatchScreen(allowedRoutes = allowedRoutes, onBack = { navController.navigate(AppRoute.ModuleSelector) { launchSingleTop = true } }, onLogout = ::logoutAndNavigateToLogin, onNavigateToRoute = ::navigateToAuthorizedRoute) },
        ).forEach { (route, screen) ->
            composable(route) {
                val canAccess = route in allowedRoutes
                LaunchedEffect(workerSession, canAccess) {
                    if (workerSession == null) {
                        navController.navigate(AppRoute.WorkerLogin) { launchSingleTop = true }
                    } else if (!canAccess) {
                        navController.navigate(AppRoute.ModuleSelector) { launchSingleTop = true }
                    }
                }
                if (canAccess) screen()
            }
        }
    }
}
