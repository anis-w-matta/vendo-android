package com.vendo.admin.orderhistory

import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.vendo.admin.common.ErrorSnackbarEffect
import com.vendo.core.designsystem.components.PillButton
import com.vendo.core.designsystem.components.RequestCard
import com.vendo.core.designsystem.components.StatusBadge
import com.vendo.core.designsystem.components.VendoTone
import com.vendo.core.designsystem.vendoContentMaxWidth
import com.vendo.core.designsystem.vendoScreenPadding
import com.vendo.core.network.dto.RecentOrderOut

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminOrderHistoryScreen(viewModel: AdminOrderHistoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var openedOrder by remember { mutableStateOf<RecentOrderOut?>(null) }
    ErrorSnackbarEffect(state.error, snackbarHostState)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .vendoContentMaxWidth()
                .padding(vendoScreenPadding()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "ORDER HISTORY",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(16.dp))

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val contentKey = when {
                    state.isLoading -> "loading"
                    state.orders.isEmpty() -> "empty"
                    else -> "content"
                }
                Crossfade(targetState = contentKey, label = "admin-order-history-content") { key ->
                    when (key) {
                        "loading" -> Box(Modifier.fillMaxSize())
                        "empty" -> Text(
                            text = "No committed orders yet.",
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        else -> LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(state.orders, key = { it.order_nb + it.order_type }) { order ->
                                OrderRow(order = order, onClick = { openedOrder = order })
                            }
                        }
                    }
                }
            }
        }

        openedOrder?.let { order ->
            OrderDetailDialog(order = order, onDismiss = { openedOrder = null })
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun OrderRow(order: RecentOrderOut, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Order ${order.order_nb}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (order.order_type == "RETURN") {
                StatusBadge(text = "RETURN", tone = VendoTone.Danger)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = order.customer_name ?: order.cust_nb,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${order.lines.size} item${if (order.lines.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OrderDetailDialog(order: RecentOrderOut, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        RequestCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Order ${order.order_nb}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = order.customer_name ?: order.cust_nb,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            if (order.order_type == "RETURN") {
                Spacer(Modifier.height(6.dp))
                StatusBadge(text = "RETURN", tone = VendoTone.Danger)
            }
            Spacer(Modifier.height(10.dp))
            order.lines.forEachIndexed { i, line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = line.item_desc,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (line.is_free) {
                            Spacer(Modifier.height(4.dp))
                            StatusBadge(text = "FREE (QRA bonus)", tone = VendoTone.Positive)
                        }
                    }
                    Text(
                        text = "${line.qty} ${line.uom.orEmpty()}".trim(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (i != order.lines.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
