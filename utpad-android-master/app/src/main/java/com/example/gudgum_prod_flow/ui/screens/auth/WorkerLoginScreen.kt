package com.example.gudgum_prod_flow.ui.screens.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gudgum_prod_flow.ui.theme.UtpadError
import com.example.gudgum_prod_flow.ui.theme.UtpadPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadTextPrimary
import com.example.gudgum_prod_flow.ui.viewmodels.AuthViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.LoginState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerLoginScreen(
    onLoginSuccess: (String) -> Unit,
    authViewModel: AuthViewModel = viewModel(),
) {
    val phone by authViewModel.phone.collectAsState()
    val loginState by authViewModel.loginState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is LoginState.Success -> {
                onLoginSuccess(state.authorizedRoute)
                authViewModel.consumeLoginSuccess()
            }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.gudgum_prod_flow.R.drawable.gudgum_logo),
                        contentDescription = "GudGum Logo",
                        modifier = Modifier.height(48.dp)
                    )
                },
                navigationIcon = { Box(Modifier.size(48.dp)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Enter your phone\nnumber",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = UtpadTextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Phone Number",
                style = MaterialTheme.typography.labelMedium,
                color = Color.DarkGray,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = authViewModel::onPhoneChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = loginState !is LoginState.Loading,
                isError = loginState is LoginState.Error,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        if (phone.isNotBlank() && loginState !is LoginState.Loading) {
                            authViewModel.submitLogin()
                        }
                    }
                ),
                singleLine = true,
                leadingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                    ) {
                        Text("🇮🇳", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("+91", fontWeight = FontWeight.SemiBold, color = UtpadTextPrimary, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFCBD5E1)))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC),
                    focusedBorderColor = UtpadPrimary,
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                ),
                shape = RoundedCornerShape(16.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp,
                    letterSpacing = 1.sp,
                    color = UtpadTextPrimary,
                    fontWeight = FontWeight.Medium
                )
            )

            if (loginState is LoginState.Error) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = (loginState as LoginState.Error).message,
                    style = MaterialTheme.typography.bodySmall,
                    color = UtpadError,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = { authViewModel.submitLogin() },
                enabled = phone.isNotBlank() && loginState !is LoginState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        if (phone.isNotBlank() && loginState !is LoginState.Loading) 8.dp else 0.dp,
                        RoundedCornerShape(16.dp),
                        spotColor = UtpadPrimary.copy(alpha = 0.5f)
                    ),
                colors = ButtonDefaults.buttonColors(containerColor = UtpadPrimary),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (loginState is LoginState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 3.dp)
                } else {
                    Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "By continuing, you agree to our Terms of Service and Privacy Policy.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
