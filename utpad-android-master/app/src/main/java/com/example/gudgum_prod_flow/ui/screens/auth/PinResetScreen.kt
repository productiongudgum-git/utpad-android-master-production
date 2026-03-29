package com.example.gudgum_prod_flow.ui.screens.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class PinResetMode { WorkerRequest, SupervisorApproval }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinResetScreen(
    mode: PinResetMode = PinResetMode.WorkerRequest,
    prefilledPhone: String = "",
    onBackPressed: () -> Unit = {},
    onSubmit: () -> Unit = {}
) {
    var phone by remember { mutableStateOf(prefilledPhone) }
    var isSubmitted by remember { mutableStateOf(false) }

    // Supervisor mode state
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reset PIN") },
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            when (mode) {
                PinResetMode.WorkerRequest -> {
                    if (!isSubmitted) {
                        // Request form
                        Text(
                            "Can't remember your PIN?",
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Your supervisor will receive a request and set a new PIN for you.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(24.dp))

                        OutlinedTextField(
                            value = phone,
                            onValueChange = {
                                if (it.length <= 10 && it.all { c -> c.isDigit() }) phone = it
                            },
                            label = { Text("Your phone number") },
                            prefix = { Text("+91 ") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = { isSubmitted = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = phone.length == 10
                        ) {
                            Text("Request PIN Reset", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        // Success confirmation
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Filled.Email,
                                contentDescription = null,
                                modifier = Modifier.padding(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            "Request sent!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "Your supervisor has been notified. They will set a new PIN for you.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(24.dp))

                        OutlinedButton(
                            onClick = onBackPressed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Back to Login")
                        }
                    }
                }

                PinResetMode.SupervisorApproval -> {
                    Text(
                        "Set new PIN for worker",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = newPin,
                        onValueChange = {
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) newPin = it
                        },
                        label = { Text("Enter 6-digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (newPin.length == 6) onSubmit()
                        },
                        enabled = newPin.length == 6,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Submit New PIN", style = MaterialTheme.typography.labelLarge)
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Use a PIN that the worker can remember.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
