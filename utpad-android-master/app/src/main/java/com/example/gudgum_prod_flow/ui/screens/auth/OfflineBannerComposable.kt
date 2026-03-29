package com.example.gudgum_prod_flow.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.gudgum_prod_flow.ui.theme.UtpadSuccess
import com.example.gudgum_prod_flow.ui.theme.UtpadWarning
import kotlinx.coroutines.delay

sealed class ConnectivityState {
    data object Online : ConnectivityState()
    data class Offline(val lastSyncText: String?) : ConnectivityState()
    data class Syncing(val current: Int, val total: Int) : ConnectivityState()
    data object SyncComplete : ConnectivityState()
    data class SyncError(val message: String) : ConnectivityState()
}

@Composable
fun OfflineBanner(
    state: ConnectivityState,
    onRetrySync: () -> Unit = {},
    onSyncCompleteHidden: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state != ConnectivityState.Online,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val backgroundColor = when (state) {
            is ConnectivityState.Offline -> UtpadWarning.copy(alpha = 0.2f)
            is ConnectivityState.Syncing -> MaterialTheme.colorScheme.primaryContainer
            is ConnectivityState.SyncComplete -> UtpadSuccess.copy(alpha = 0.2f)
            is ConnectivityState.SyncError -> MaterialTheme.colorScheme.errorContainer
            else -> Color.Transparent
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = backgroundColor
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .then(
                            if (state is ConnectivityState.SyncError)
                                Modifier.clickable { onRetrySync() }
                            else Modifier
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (state) {
                        is ConnectivityState.Offline -> {
                            Icon(
                                Icons.Rounded.CloudOff,
                                contentDescription = "Offline",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildString {
                                    append("Offline")
                                    state.lastSyncText?.let { append(" — Last synced $it") }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        is ConnectivityState.Syncing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Syncing... (${state.current} of ${state.total})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        is ConnectivityState.SyncComplete -> {
                            Icon(
                                Icons.Rounded.CloudDone,
                                contentDescription = "Synced",
                                modifier = Modifier.size(20.dp),
                                tint = UtpadSuccess
                            )
                            Text(
                                text = "All synced",
                                style = MaterialTheme.typography.bodyMedium,
                                color = UtpadSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            LaunchedEffect(Unit) {
                                delay(3000)
                                onSyncCompleteHidden()
                            }
                        }
                        is ConnectivityState.SyncError -> {
                            Icon(
                                Icons.Rounded.CloudOff,
                                contentDescription = "Sync failed",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Sync failed — Tap to retry",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        else -> {}
                    }
                }
                if (state is ConnectivityState.Syncing && state.total > 0) {
                    LinearProgressIndicator(
                        progress = { state.current.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
