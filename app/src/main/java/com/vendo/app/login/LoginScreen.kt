package com.vendo.app.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vendo.app.common.ErrorSnackbarEffect
import com.vendo.core.designsystem.VendoGray
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

    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    // The server address is admin/dev configuration, not something an
    // ordinary rep should have to look at every time they sign in - tucked
    // behind this toggle instead of sitting on the form by default (spec:
    // runtime server overrides should stay "appropriately tucked away").
    // It still auto-expands on a fresh, never-configured install, and again
    // if a login attempt fails to even reach the server - both cases where
    // hiding it would leave the rep unable to tell why sign-in isn't working.
    var showServerField by rememberSaveable { mutableStateOf(false) }
    var serverFieldInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.serverUrlKnown) {
        if (state.serverUrlKnown && !serverFieldInitialized) {
            showServerField = !state.serverUrlConfigured
            serverFieldInitialized = true
        }
    }
    LaunchedEffect(state.error) {
        if (state.error?.contains("reach that server") == true) showServerField = true
    }
    fun doLogin() = viewModel.login(onLoginSuccess)

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(VendoWhite))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(VendoPrimaryBlue))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .widthIn(max = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "VeNdO",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.Black,
                )
                Spacer(modifier = Modifier.height(32.dp))

                LoginField(
                    label = "ID:",
                    placeholder = "Enter ID",
                    value = state.id,
                    onValueChange = viewModel::onIdChange,
                    imeAction = ImeAction.Next,
                )
                Spacer(modifier = Modifier.height(16.dp))

                LoginField(
                    label = "PASSWORD:",
                    placeholder = "Enter password",
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onImeDone = ::doLogin,
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = VendoGray,
                            )
                        }
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { showServerField = !showServerField }) {
                    Text(
                        text = if (showServerField) "Hide server settings" else "Server settings",
                        color = Color.Black,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (showServerField) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LoginField(
                        label = "SERVER:",
                        placeholder = "http://192.168.1.20:8000/",
                        value = state.serverUrl,
                        onValueChange = viewModel::onServerUrlChange,
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    PillButton(
                        text = "LOGIN",
                        variant = PillVariant.DarkGray,
                        onClick = ::doLogin,
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
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeDone: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = Color.Black,
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onImeDone?.invoke() }),
            colors = OutlinedTextFieldDefaults.colors(
                // Login's white/blue split background is fixed regardless
                // of the app's dark-mode toggle (spec section 10/38) - the
                // field's text/placeholder must be pinned to match, or
                // dark mode's white MaterialTheme.colorScheme.onSurface
                // text becomes invisible against this always-white field.
                unfocusedContainerColor = VendoWhite,
                focusedContainerColor = VendoWhite,
                unfocusedTextColor = Color.Black,
                focusedTextColor = Color.Black,
                unfocusedPlaceholderColor = VendoGray,
                focusedPlaceholderColor = VendoGray,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
