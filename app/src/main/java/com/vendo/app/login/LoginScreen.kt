package com.vendo.app.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vendo.app.common.ErrorSnackbarEffect
import com.vendo.core.designsystem.VendoPrimaryBlue
import com.vendo.core.designsystem.VendoWhite
import com.vendo.core.designsystem.components.PillButton
import com.vendo.core.designsystem.components.PillVariant

/** Vertical white/blue split login screen - spec section 10. Deliberately
 * has no VendoTopBar (no hamburger/theme toggle here, per section 38). */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbarEffect(state.error, snackbarHostState)

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(VendoWhite))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(VendoPrimaryBlue))
        }

        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "VeNdO",
                    style = MaterialTheme.typography.displayLarge,
                    color = androidx.compose.ui.graphics.Color.Black,
                )
                Spacer(modifier = Modifier.height(32.dp))

                LoginField(
                    label = "SERVER:",
                    placeholder = "http://192.168.1.20:8000/",
                    value = state.serverUrl,
                    onValueChange = viewModel::onServerUrlChange,
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(modifier = Modifier.height(16.dp))

                LoginField(
                    label = "ID:",
                    placeholder = "Enter ID",
                    value = state.id,
                    onValueChange = viewModel::onIdChange,
                )
                Spacer(modifier = Modifier.height(16.dp))

                LoginField(
                    label = "PASSWORD:",
                    placeholder = "Enter password",
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    isPassword = true,
                )

                Spacer(modifier = Modifier.height(28.dp))

                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    PillButton(
                        text = "LOGIN",
                        variant = PillVariant.DarkGray,
                        onClick = { viewModel.login(onLoginSuccess) },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun LoginField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = androidx.compose.ui.graphics.Color.Black,
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else
                androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = if (isPassword) {
                KeyboardOptions(keyboardType = KeyboardType.Password)
            } else {
                KeyboardOptions(keyboardType = keyboardType)
            },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = VendoWhite,
                focusedContainerColor = VendoWhite,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
