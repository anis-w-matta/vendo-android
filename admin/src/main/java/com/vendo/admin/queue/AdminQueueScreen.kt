package com.vendo.admin.queue

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vendo.admin.common.ErrorSnackbarEffect
import com.vendo.core.designsystem.components.PillButton
import com.vendo.core.designsystem.components.StatusBadge
import com.vendo.core.designsystem.components.VendoTone
import com.vendo.core.designsystem.vendoContentMaxWidth
import com.vendo.core.designsystem.vendoScreenPadding
import com.vendo.core.network.dto.QueueRow

/** Read-only: every salesman's pending requests. Tap one to see its full
 * detail (AdminRequestDetailScreen) - no claim/accept/reject here, that
 * stays exclusive to :app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminQueueScreen(onOpenRequest: (Int) -> Unit) {
    val viewModel: AdminQueueViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    ErrorSnackbarEffect(state.error?.takeIf { state.rows.isNotEmpty() || !state.isLoading }, snackbarHostState)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .vendoContentMaxWidth()
                .padding(vendoScreenPadding()),
        ) {
            Text(
                text = "QUEUE",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize())
                    state.error != null && state.rows.isEmpty() -> Column {
                        Text(
                            text = state.error ?: "We couldn't load the queue.",
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        PillButton(text = "Try again", onClick = viewModel::refresh)
                    }
                    state.rows.isEmpty() -> Text(
                        text = "Nothing pending right now.",
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.rows, key = { it.id }) { row ->
                            QueueRowItem(row, onClick = { onOpenRequest(row.id) })
                        }
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun QueueRowItem(row: QueueRow, onClick: () -> Unit) {
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
                text = row.customer_name ?: row.cust_nb ?: "Unidentified customer",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusBadge(text = row.status, tone = VendoTone.Neutral)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${row.primary_intent} · ${row.line_count} line${if (row.line_count == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
