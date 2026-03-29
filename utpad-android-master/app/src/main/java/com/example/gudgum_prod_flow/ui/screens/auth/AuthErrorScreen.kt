package com.example.gudgum_prod_flow.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class ErrorAction(
    val label: String,
    val icon: ImageVector? = null,
    val onClick: () -> Unit
)

@Composable
fun ErrorScreen(
    icon: ImageVector,
    iconContainerColor: Color,
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    message: String,
    primaryAction: ErrorAction,
    secondaryAction: ErrorAction? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = iconContainerColor,
            modifier = Modifier.size(80.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = titleColor,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = primaryAction.onClick,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (primaryAction.icon != null) {
                Icon(primaryAction.icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(primaryAction.label, style = MaterialTheme.typography.labelLarge)
        }

        if (secondaryAction != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = secondaryAction.onClick) {
                Text(
                    secondaryAction.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun NetworkErrorScreen(onRetry: () -> Unit, onContinueOffline: (() -> Unit)? = null) {
    ErrorScreen(
        icon = Icons.Rounded.WifiOff,
        iconContainerColor = MaterialTheme.colorScheme.errorContainer,
        title = "No internet connection",
        message = "Check your Wi-Fi or mobile data and try again",
        primaryAction = ErrorAction("Try Again", onClick = onRetry),
        secondaryAction = onContinueOffline?.let { ErrorAction("Continue Offline", onClick = it) }
    )
}

@Composable
fun AccountLockedScreen(onCallSupervisor: () -> Unit, onRequestReset: () -> Unit) {
    ErrorScreen(
        icon = Icons.Rounded.Lock,
        iconContainerColor = MaterialTheme.colorScheme.errorContainer,
        title = "Account locked",
        titleColor = MaterialTheme.colorScheme.error,
        message = "Too many failed attempts. Contact your supervisor to unlock your account.",
        primaryAction = ErrorAction("Call Supervisor", icon = Icons.Rounded.Phone, onClick = onCallSupervisor),
        secondaryAction = ErrorAction("Request PIN Reset", onClick = onRequestReset)
    )
}

@Composable
fun GeneralErrorScreen(message: String = "Something went wrong", onRetry: () -> Unit) {
    ErrorScreen(
        icon = Icons.Rounded.ErrorOutline,
        iconContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = "Something went wrong",
        message = "We're having trouble right now. Please try again in a moment.",
        primaryAction = ErrorAction("Try Again", onClick = onRetry)
    )
}
