package com.vendo.admin.salesmen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.vendo.admin.common.ErrorSnackbarEffect
import com.vendo.core.designsystem.components.PillButton
import com.vendo.core.designsystem.components.PillVariant
import com.vendo.core.designsystem.components.RequestCard
import com.vendo.core.designsystem.components.StatusBadge
import com.vendo.core.designsystem.components.VendoTone
import com.vendo.core.designsystem.vendoContentMaxWidth
import com.vendo.core.designsystem.vendoScreenPadding
import com.vendo.core.network.dto.SalesmanOut

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesmenScreen(viewModel: SalesmenViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbarEffect(state.error, snackbarHostState)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .vendoContentMaxWidth()
                .padding(vendoScreenPadding()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SALESMEN",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                PillButton(text = "+ New account", variant = PillVariant.PrimaryBlue,
                          onClick = viewModel::openCreateDialog)
            }
            Spacer(Modifier.height(16.dp))

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (!state.isLoading) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.salesmen, key = { it.login_id }) { salesman ->
                            SalesmanRow(salesman, onToggleActive = { viewModel.toggleActive(salesman) })
                        }
                    }
                }
            }
        }

        if (state.showCreateDialog) {
            CreateAccountDialog(
                isCreating = state.isCreating,
                error = state.createError,
                onDismiss = viewModel::closeCreateDialog,
                onCreate = viewModel::createAccount,
            )
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SalesmanRow(salesman: SalesmanOut, onToggleActive: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = salesman.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (salesman.role == "admin") {
                    Spacer(Modifier.width(6.dp))
                    StatusBadge(text = "ADMIN", tone = VendoTone.Info)
                }
            }
            Text(
                text = "${salesman.login_id} · ${salesman.email ?: "no email"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusBadge(
            text = if (salesman.is_active) "Active" else "Inactive",
            tone = if (salesman.is_active) VendoTone.Positive else VendoTone.Danger,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onToggleActive) {
            Text(if (salesman.is_active) "Deactivate" else "Reactivate")
        }
    }
}

@Composable
private fun CreateAccountDialog(
    isCreating: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (loginId: String, password: String, name: String, email: String?, role: String) -> Unit,
) {
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("salesman") }

    Dialog(onDismissRequest = onDismiss) {
        RequestCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("New account", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = loginId, onValueChange = { loginId = it }, singleLine = true,
                label = { Text("Login ID") }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it }, singleLine = true,
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it }, singleLine = true,
                label = { Text("Email (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("salesman" to "Salesman", "admin" to "Admin").forEach { (code, label) ->
                    FilterChip(
                        selected = role == code,
                        onClick = { role = code },
                        label = { Text(label) },
                    )
                }
            }
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PillButton(
                    text = if (isCreating) "Creating…" else "Create",
                    variant = PillVariant.PrimaryBlue,
                    enabled = !isCreating,
                    onClick = { onCreate(loginId, password, name, email, role) },
                )
            }
        }
    }
}
