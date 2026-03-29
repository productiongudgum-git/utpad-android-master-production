package com.example.gudgum_prod_flow.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gudgum_prod_flow.ui.theme.UtpadWarning
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TwoFactorScreen(
    onVerified: () -> Unit,
    onBackPressed: () -> Unit = {}
) {
    var code by remember { mutableStateOf("") }
    var useBackupCode by remember { mutableStateOf(false) }
    var backupCode by remember { mutableStateOf("") }
    var countdownSeconds by remember { mutableIntStateOf(30) }
    var isError by remember { mutableStateOf(false) }

    // Countdown timer
    LaunchedEffect(countdownSeconds) {
        if (countdownSeconds > 0) {
            delay(1000)
            countdownSeconds--
        }
    }

    // Auto-submit on 6 digits
    LaunchedEffect(code) {
        if (code.length == 6) {
            // Mock verify: accept any 6-digit code
            delay(500)
            onVerified()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Two-Factor Authentication") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // Lock icon
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(Modifier.height(24.dp))

            if (!useBackupCode) {
                Text(
                    "Enter the 6-digit code from\nyour authenticator app",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(24.dp))

                // 6 digit boxes
                TotpCodeInput(
                    code = code,
                    onCodeChanged = { newCode ->
                        if (newCode.length <= 6 && newCode.all { it.isDigit() }) {
                            code = newCode
                            isError = false
                        }
                    },
                    isError = isError
                )

                Spacer(Modifier.height(16.dp))

                // Countdown
                val countdownColor = when {
                    countdownSeconds < 5 -> MaterialTheme.colorScheme.error
                    countdownSeconds < 10 -> UtpadWarning
                    else -> MaterialTheme.colorScheme.primary
                }

                LinearProgressIndicator(
                    progress = { countdownSeconds / 30f },
                    modifier = Modifier.fillMaxWidth(0.6f),
                    color = countdownColor,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    if (countdownSeconds > 0) "Expires in ${countdownSeconds}s" else "Code expired",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (countdownSeconds == 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                // Verify button
                Button(
                    onClick = {
                        if (code.length == 6) onVerified()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = code.length == 6
                ) {
                    Text("Verify", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                // Backup code entry
                Text(
                    "Enter one of your backup codes",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = backupCode,
                    onValueChange = { backupCode = it },
                    label = { Text("Backup code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (backupCode.isNotBlank()) onVerified()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = backupCode.isNotBlank()
                ) {
                    Text("Verify", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(onClick = { useBackupCode = !useBackupCode }) {
                Text(
                    if (useBackupCode) "Use authenticator app instead" else "Use backup code instead",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TotpCodeInput(
    code: String,
    onCodeChanged: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = modifier) {
        // Hidden text field for keyboard input
        BasicTextField(
            value = code,
            onValueChange = onCodeChanged,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .focusRequester(focusRequester)
                .size(1.dp) // Invisible but focusable
        )

        // Visual boxes
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(6) { index ->
                val digit = code.getOrNull(index)
                val isFocused = code.length == index
                val borderColor = when {
                    isError -> MaterialTheme.colorScheme.error
                    isFocused -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                }

                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 56.dp)
                        .border(2.dp, borderColor, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (digit != null) {
                        Text(
                            digit.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
