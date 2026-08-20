package com.vendo.app.request

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vendo.app.common.ErrorSnackbarEffect
import com.vendo.core.designsystem.components.PillButton
import com.vendo.core.designsystem.components.PillVariant
import com.vendo.core.designsystem.components.RequestCard
import com.vendo.core.designsystem.vendoContentMaxWidth
import com.vendo.core.designsystem.vendoScreenPadding
import com.vendo.core.network.dto.CandidateOut

@Composable
fun RequestScreen(
    onAccepted: () -> Unit,
    onRejected: () -> Unit,
    viewModel: RequestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRejectDialog by remember { mutableStateOf(false) }
    var rejectReason by remember { mutableStateOf("") }

    LaunchedEffect(state.accepted) {
        if (state.accepted) onAccepted()
    }
    LaunchedEffect(state.rejected) {
        if (state.rejected) onRejected()
    }

    // state.error is shared between "couldn't load the request at all" and
    // "the Accept submission failed" - only the former should replace the
    // whole card below; the latter surfaces as a snackbar so a failed
    // Accept doesn't wipe out the user's in-progress edits.
    ErrorSnackbarEffect(state.error?.takeIf { state.request != null }, snackbarHostState)

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
                text = "Request",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(20.dp))

            val contentKey = when {
                state.isLoading -> "loading"
                state.request == null -> "error"
                else -> "content"
            }
            Crossfade(targetState = contentKey, label = "request-content") { key ->
                when (key) {
                    "loading" -> CircularProgressIndicator()
                    "error" -> Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> {
                        val request = state.request!!
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RequestCard {
                                VoicePlaybackRow(
                                    isPlaying = state.isPlayingAudio,
                                    isLoading = state.isLoadingAudio,
                                    onToggle = viewModel::togglePlayback,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "TRANSCRIPT:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = request.transcript?.takeIf { it.isNotBlank() } ?: "(no transcript)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                LabeledRow("REQUEST TYPE:", request.primary_intent.uppercase())
                                LabeledRow("CUST.NAME:", request.customer_name ?: "-")
                                LabeledRow("ORDER-NUM:", request.target_order_nb ?: "-")
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "ITEMS:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                state.editableLines.forEach { line ->
                                    ItemLine(
                                        line = line,
                                        isEditing = state.isEditing,
                                        onDescChange = { viewModel.updateLine(line.lineNb, itemDesc = it) },
                                        onQtyChange = { viewModel.updateLine(line.lineNb, qty = it) },
                                        onUomChange = { viewModel.updateLine(line.lineNb, uom = it) },
                                        onCandidateSelected = { viewModel.selectCandidate(line.lineNb, it) },
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    PillButton(
                                        text = "EDIT",
                                        variant = PillVariant.DarkGray,
                                        onClick = viewModel::toggleEdit,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            if (state.isSubmitting) {
                                CircularProgressIndicator()
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PillButton(
                                        text = "Reject",
                                        variant = PillVariant.DarkGray,
                                        onClick = {
                                            rejectReason = ""
                                            showRejectDialog = true
                                        },
                                    )
                                    PillButton(
                                        text = "Accept",
                                        variant = PillVariant.PrimaryBlue,
                                        onClick = viewModel::accept,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showRejectDialog) {
            AlertDialog(
                onDismissRequest = { showRejectDialog = false },
                title = { Text("Reject request") },
                text = {
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        placeholder = { Text("Reason") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = rejectReason.isNotBlank(),
                        onClick = {
                            showRejectDialog = false
                            viewModel.reject(rejectReason)
                        },
                    ) { Text("Reject") }
                },
                dismissButton = {
                    TextButton(onClick = { showRejectDialog = false }) { Text("Cancel") }
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun VoicePlaybackRow(isPlaying: Boolean, isLoading: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            PillButton(
                text = if (isPlaying) "⏸ PAUSE" else "▶ PLAY",
                variant = PillVariant.DarkBlue,
                onClick = onToggle,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "VOICE RECORDING",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Every candidate the backend resolved for this line (match_item.py's
 * top-scoring matches, already sorted most-to-least probable) as a real
 * dropdown - available whether or not the line auto-resolved, so a
 * confident-but-wrong auto-pick can still be overridden. Selecting one
 * writes its item_nb (the catalogue id), never just its description -
 * see RequestViewModel.selectCandidate. */
@Composable
private fun CandidateDropdown(
    candidates: List<CandidateOut>,
    selectedItemNb: String?,
    onSelect: (CandidateOut) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PillButton(
            text = "MATCHES (${candidates.size}) ▾",
            variant = PillVariant.DarkGray,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            candidates.forEach { candidate ->
                val selected = candidate.item_nb == selectedItemNb
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "${if (selected) "✓ " else ""}" +
                                "${candidate.item_desc} (${candidate.item_nb}) " +
                                "- ${candidate.score.toInt()}%",
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(candidate)
                    },
                )
            }
        }
    }
}

@Composable
private fun ItemLine(
    line: EditableLine,
    isEditing: Boolean,
    onDescChange: (String) -> Unit,
    onQtyChange: (String) -> Unit,
    onUomChange: (String) -> Unit,
    onCandidateSelected: (CandidateOut) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (isEditing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = line.itemDesc,
                    onValueChange = onDescChange,
                    modifier = Modifier.weight(2f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = line.qty,
                    onValueChange = onQtyChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = line.uom,
                    onValueChange = onUomChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            if (line.candidates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                if (line.itemNb == null) {
                    Text(
                        text = "Unresolved - pick a match:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                CandidateDropdown(
                    candidates = line.candidates,
                    selectedItemNb = line.itemNb,
                    onSelect = onCandidateSelected,
                )
            } else if (line.itemNb == null) {
                Text(
                    text = "Unresolved - no match found, type manually",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    // Show the catalogue item number, not the description -
                    // falls back to the description/raw text when the line
                    // is unresolved and has no item_nb yet.
                    text = line.itemNb ?: line.itemDesc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${line.qty} ${line.uom}".trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
