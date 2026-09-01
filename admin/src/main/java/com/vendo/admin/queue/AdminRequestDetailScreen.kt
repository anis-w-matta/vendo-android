package com.vendo.admin.queue

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vendo.core.designsystem.components.PillButton
import com.vendo.core.designsystem.components.RequestCard
import com.vendo.core.designsystem.components.StatusBadge
import com.vendo.core.designsystem.components.VendoTone
import com.vendo.core.designsystem.vendoContentMaxWidth
import com.vendo.core.designsystem.vendoScreenPadding
import com.vendo.core.network.dto.LineOut

@Composable
fun AdminRequestDetailScreen(viewModel: AdminRequestDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.request == null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .vendoContentMaxWidth()
                    .padding(vendoScreenPadding()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.error ?: "We couldn't load this request.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(16.dp))
                PillButton(text = "Try again", onClick = viewModel::retry)
            }
            else -> {
                val request = state.request!!
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .vendoContentMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(vendoScreenPadding()),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = request.primary_intent,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        StatusBadge(text = request.status, tone = VendoTone.Neutral)
                    }
                    Spacer(Modifier.height(16.dp))

                    RequestCard {
                        Text(
                            text = request.customer_name ?: request.cust_nb ?: "Unidentified customer",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        request.assigned_to?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Assigned to $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (request.flags.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            request.flags.forEach { flag ->
                                StatusBadge(text = flag, tone = VendoTone.Warning)
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    RequestCard {
                        Text(
                            text = "TRANSCRIPT",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = request.transcript?.takeIf { it.isNotBlank() }
                                ?: "No transcript is available for this recording.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(14.dp))

                    if (request.lines.isNotEmpty()) {
                        RequestCard {
                            Text(
                                text = "LINES",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            request.lines.forEachIndexed { i, line ->
                                LineRow(line)
                                if (i != request.lines.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LineRow(line: LineOut) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = line.item_desc ?: line.raw_text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${line.qty ?: "?"} ${line.uom.orEmpty()}".trim(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
