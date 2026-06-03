package com.example.gudgum_prod_flow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.example.gudgum_prod_flow.data.notification.UtpadFirebaseMessagingService
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import com.example.gudgum_prod_flow.ui.navigation.UtpadNavGraph
import com.example.gudgum_prod_flow.ui.theme.GudGumProdFlowTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Tracks an FCM-launched route so Compose can navigate after the auth flow restores. */
    private val pendingRoute = mutableStateOf<String?>(null)

    private val notificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result intentionally ignored — user can re-enable in system settings later */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationsPermissionIfNeeded()
        readDeepLinkFromIntent(intent)
        setContent {
            GudGumProdFlowTheme {
                val navController = rememberNavController()

                // When MainActivity is launched (or re-launched via singleTop) from
                // an FCM notification tap, push the requested route once auth state
                // has settled. We use launchSingleTop to avoid stacking dispatch screens.
                LaunchedEffect(pendingRoute.value) {
                    val target = pendingRoute.value ?: return@LaunchedEffect
                    pendingRoute.value = null
                    runCatching {
                        navController.navigate(target) { launchSingleTop = true }
                    }
                }

                UtpadNavGraph(navController = navController)
            }
        }
    }

    /**
     * When the activity is already running and a notification tap comes in,
     * Android calls onNewIntent instead of onCreate (because of singleTop in the manifest).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDeepLinkFromIntent(intent)
    }

    private fun readDeepLinkFromIntent(intent: Intent?) {
        if (intent == null) return
        val route = intent.getStringExtra(UtpadFirebaseMessagingService.EXTRA_ROUTE) ?: return
        // Today we only deep-link to Dispatch. New cases can branch off the route string.
        val target = when (route) {
            UtpadFirebaseMessagingService.ROUTE_DISPATCH -> AppRoute.Dispatch
            else -> null
        } ?: return
        pendingRoute.value = target
    }

    private fun requestNotificationsPermissionIfNeeded() {
        // Android 13+ requires runtime permission to post notifications. Older
        // versions auto-grant from the manifest entry. We only ask once per cold
        // launch; users can re-enable later via system settings if they decline.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
