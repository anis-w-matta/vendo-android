package com.vendo.admin.customers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.vendo.admin.common.ErrorSnackbarEffect
import com.vendo.core.designsystem.components.RequestCard
import com.vendo.core.designsystem.components.StatusBadge
import com.vendo.core.designsystem.components.VendoTone
import com.vendo.core.designsystem.vendoContentMaxWidth
import com.vendo.core.designsystem.vendoScreenPadding
import com.vendo.core.network.dto.CustomerCandidateOut
import com.vendo.core.network.dto.SalesmanOut

/** Search the full customer book (every customer, not just one salesman's -
 * see CustomersViewModel) and reassign one to a different salesman. */
@Composable
fun CustomersScreen(viewModel: CustomersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val detailState by viewModel.detailState.collectAsState()
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
            Text(
                text = "CUSTOMERS",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    placeholder = { Text("Search by name or number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::search) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
            Spacer(Modifier.height(12.dp))

            if (state.isSearching) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.results.isEmpty()) {
                Text(
                    text = "Search for a customer by name or number above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.results, key = { it.cust_nb }) { candidate ->
                        CustomerRow(candidate, onClick = { viewModel.openDetail(candidate.cust_nb) })
                    }
                }
            }
        }

        detailState?.let { d ->
            CustomerDetailDialog(
                state = d,
                onReassign = viewModel::reassign,
                onDismiss = viewModel::closeDetail,
            )
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun CustomerRow(candidate: CustomerCandidateOut, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = candidate.customer_name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = candidate.cust_nb,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CustomerDetailDialog(
    state: CustomerDetailUiState,
    onReassign: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        RequestCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.detail?.customer_name ?: state.custNb,
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(8.dp))

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@RequestCard
            }
            val detail = state.detail
            if (detail == null) {
                Text(
                    text = state.error ?: "We couldn't load this customer.",
                    color = MaterialTheme.colorScheme.error,
                )
                return@RequestCard
            }

            Text(detail.cust_nb, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            detail.telephone?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            detail.city?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))

            val owner = state.salesmen.find { it.login_id == detail.salesman_id }
            StatusBadge(
                text = "Owner: ${owner?.name ?: detail.salesman_id ?: "Unassigned"}",
                tone = if (detail.salesman_id != null) VendoTone.Info else VendoTone.Warning,
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = "REASSIGN TO",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            if (state.isSaving) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    item {
                        SalesmanRow(
                            label = "Unassigned", selected = detail.salesman_id == null,
                            onClick = { onReassign(null) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                    items(state.salesmen, key = { it.login_id }) { salesman ->
                        SalesmanRow(
                            label = "${salesman.name} (${salesman.login_id})",
                            selected = salesman.login_id == detail.salesman_id,
                            onClick = { onReassign(salesman.login_id) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.error, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun SalesmanRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) StatusBadge(text = "Current", tone = VendoTone.Positive)
    }
}
