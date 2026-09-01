package com.vendo.admin.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vendo.admin.common.ErrorSnackbarEffect
import com.vendo.core.designsystem.components.PillButton
import com.vendo.core.designsystem.components.PillVariant
import com.vendo.core.designsystem.vendoContentMaxWidth
import com.vendo.core.designsystem.vendoScreenPadding

@Composable
fun AccountScreen(viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbarEffect(state.error?.takeIf { !state.loadFailed }, snackbarHostState)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .vendoContentMaxWidth()
                .padding(vendoScreenPadding()),
        ) {
            Text(
                text = "ACCOUNT INFO",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (state.isLoading) {
                CircularProgressIndicator()
            } else if (state.loadFailed) {
                Text(
                    text = state.error ?: "We couldn't load your account.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(16.dp))
                PillButton(text = "Try again", onClick = viewModel::retry)
            } else {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.saved) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Saved", color = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.height(20.dp))
                if (state.isSaving) {
                    CircularProgressIndicator()
                } else {
                    PillButton(text = "Save", variant = PillVariant.PrimaryBlue, onClick = viewModel::save)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
