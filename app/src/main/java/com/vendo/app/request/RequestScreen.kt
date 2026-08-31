@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.vendo.app.request

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.vendo.app.common.ErrorSnackbarEffect
import com.vendo.core.designsystem.components.PillButton
import com.vendo.core.designsystem.components.PillVariant
import com.vendo.core.designsystem.components.QuantityStepper
import com.vendo.core.designsystem.components.RequestCard
import com.vendo.core.designsystem.components.StatusBadge
import com.vendo.core.designsystem.components.VendoTone
import com.vendo.core.designsystem.vendoContentMaxWidth
import com.vendo.core.designsystem.vendoScreenPadding
import com.vendo.core.network.dto.CandidateOut
import com.vendo.core.network.dto.CustomerCandidateOut
import kotlinx.coroutines.launch

@Composable
fun RequestScreen(
    onAccepted: () -> Unit,
    onRejected: () -> Unit,
    viewModel: RequestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRejectDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val lineRequesters = remember { mutableMapOf<Int, BringIntoViewRequester>() }

    LaunchedEffect(state.accepted) { if (state.accepted) onAccepted() }
    LaunchedEffect(state.rejected) { if (state.rejected) onRejected() }

    // state.error is shared between "couldn't load the request at all" and
    // "an action (accept/reject/search) failed" - only the former replaces
    // the whole screen; the latter surfaces as a snackbar so a failed
    // action doesn't wipe out the user's in-progress edits.
    ErrorSnackbarEffect(state.error?.takeIf { state.request != null }, snackbarHostState)

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val contentKey = when {
            state.isLoading -> "loading"
            state.request == null -> "error"
            else -> "content"
        }
        Crossfade(targetState = contentKey, label = "request-content") { key ->
            when (key) {
                "loading" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                "error" -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .vendoContentMaxWidth()
                        .padding(vendoScreenPadding()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.error ?: "We couldn't load this order.",
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
                        Text(
                            text = if (isNonOrder(request.primary_intent)) "Recording" else "Draft Order",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(16.dp))

                        if (state.isClaimedByAnother) {
                            InfoBanner(
                                text = "This order is being reviewed by ${request.assigned_to}.",
                                tone = VendoTone.Warning,
                                action = "Refresh" to viewModel::retry,
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        RequestCard {
                            HeaderSection(
                                request = request,
                                selectedCustomer = state.selectedCustomer,
                                canSelectCustomer = !state.isReadOnly,
                                onSelectCustomer = viewModel::openCustomerPicker,
                                editedTargetOrderNb = state.editedTargetOrderNb,
                                onTargetOrderNbEdited = viewModel::onTargetOrderNbEdited,
                            )
                        }
                        Spacer(Modifier.height(14.dp))

                        val flagPresentations = requestFlagPresentations(request.flags)
                        if (flagPresentations.isNotEmpty()) {
                            RequestCard {
                                Text(
                                    text = "ISSUES",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    flagPresentations.forEach { fp ->
                                        StatusBadge(text = fp.message, tone = fp.tone)
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        RequestCard {
                            EvidenceSection(state = state, request = request, onTogglePlayback = viewModel::togglePlayback, onSeek = viewModel::seekTo)
                        }
                        Spacer(Modifier.height(14.dp))

                        if (!isNonOrder(request.primary_intent)) {
                            RequestCard {
                                ItemsSection(
                                    state = state,
                                    viewModel = viewModel,
                                    lineRequesters = lineRequesters,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        if (state.isDecided) {
                            val presentation = requestStatusPresentation(request.status)
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                StatusBadge(text = presentation.label, tone = presentation.tone)
                            }
                        } else if (!state.isClaimedByAnother) {
                            val blockers = state.blockingIssues
                            if (blockers.isNotEmpty() && !isNonOrder(request.primary_intent)) {
                                BlockersCard(blockers) { lineNb ->
                                    lineNb?.let {
                                        scope.launch { lineRequesters[it]?.bringIntoView() }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            if (state.isSubmitting) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                ActionsRow(
                                    canAccept = !isNonOrder(request.primary_intent) && blockers.isEmpty(),
                                    isEditing = state.isEditing,
                                    onToggleEdit = viewModel::toggleEdit,
                                    onAccept = viewModel::accept,
                                    onReject = { showRejectDialog = true },
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }

        if (showRejectDialog) {
            RejectDialog(
                onDismiss = { showRejectDialog = false },
                onConfirm = { reason, note ->
                    showRejectDialog = false
                    viewModel.reject(reason, note)
                },
            )
        }
        state.searchingLineNb?.let { lineNb ->
            val line = state.editableLines.find { it.lineNb == lineNb }
            if (line != null) {
                ProductPickerDialog(
                    line = line,
                    onSearch = { q -> viewModel.searchItem(lineNb, q) },
                    onSelect = { viewModel.selectCandidate(lineNb, it) },
                    onDismiss = viewModel::closeProductPicker,
                )
            }
        }
        if (state.isPickingCustomer) {
            CustomerPickerDialog(
                candidates = state.customerCandidates,
                onSearch = viewModel::searchCustomer,
                onSelect = viewModel::selectCustomer,
                onDismiss = viewModel::closeCustomerPicker,
            )
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun InfoBanner(text: String, tone: VendoTone, action: Pair<String, () -> Unit>? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatusBadge(text = text, tone = tone, modifier = Modifier.weight(1f, fill = false))
        action?.let { (label, onClick) ->
            TextButton(onClick = onClick) { Text(label) }
        }
    }
}

@Composable
private fun HeaderSection(
    request: com.vendo.core.network.dto.RequestDetail,
    selectedCustomer: CustomerCandidateOut?,
    canSelectCustomer: Boolean,
    onSelectCustomer: () -> Unit,
    editedTargetOrderNb: String?,
    onTargetOrderNbEdited: (String) -> Unit,
) {
    val isReturn = request.primary_intent == "return_order"
    val isReorder = request.primary_intent == "repeat_order" ||
        request.primary_intent == "repeat_order_adjusted"
    if (isReturn) {
        StatusBadge(text = "RETURN", tone = VendoTone.Danger)
        Spacer(Modifier.height(10.dp))
    }
    val statusPresentation = requestStatusPresentation(request.status)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = orderTypeLabel(request.primary_intent),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        StatusBadge(text = statusPresentation.label, tone = statusPresentation.tone)
    }
    Spacer(Modifier.height(10.dp))
    val customerLabel = selectedCustomer?.customer_name ?: request.customer_name
    if (customerLabel != null) {
        LabeledRow("Customer", customerLabel)
    } else if (isReturn || isReorder) {
        // A return's/reorder's customer comes from the reference order
        // below, not a separate pick - see the target-order-nb field and
        // commit.py. Picking a customer independently here wouldn't pull
        // in the order's own lines the way correcting the order number
        // does, so the generic picker is skipped for both.
    } else if (canSelectCustomer) {
        SelectCustomerRow(onClick = onSelectCustomer)
    } else {
        LabeledRow("Customer", "Not identified")
    }
    val targetOrderNb = request.target_order_nb
    if (targetOrderNb != null) {
        LabeledRow("Reference order", targetOrderNb)
    } else if ((isReturn || isReorder) && canSelectCustomer) {
        TargetOrderNbField(
            value = editedTargetOrderNb.orEmpty(),
            onValueChange = onTargetOrderNbEdited,
            forReorder = isReorder,
        )
    }
    if (request.languages.isNotEmpty()) {
        LabeledRow("Language", request.languages.joinToString(" + ") { it.uppercase() })
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Tappable stand-in for LabeledRow when the voice pipeline couldn't
 * identify a customer - opens CustomerPickerDialog so a reviewer can pick
 * one manually instead of being stuck behind an unresolvable blocker. */
@Composable
private fun SelectCustomerRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Customer: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Select customer",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Shown instead of a read-only "Reference order" row when a return
 * couldn't be matched to a sales order at intake (spec: never guess a
 * customer for a return/reorder - it's pulled from this order number
 * instead, see AcceptIn.target_order_nb / commit.py). */
@Composable
private fun TargetOrderNbField(
    value: String,
    onValueChange: (String) -> Unit,
    forReorder: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = if (forReorder) {
                "We couldn't match a sales order to repeat - enter the order number:"
            } else {
                "We couldn't match a sales order to return against - enter the order number:"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = { Text("Order number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CustomerPickerDialog(
    candidates: List<CustomerCandidateOut>,
    onSearch: (String) -> Unit,
    onSelect: (CustomerCandidateOut) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        RequestCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Select customer", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search by name or number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onSearch(query) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (candidates.isEmpty()) {
                Text(
                    text = "Search for the customer by name or number above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(candidates.size) { i ->
                        val candidate = candidates[i]
                        CustomerCandidateRow(candidate = candidate, onClick = { onSelect(candidate) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomerCandidateRow(candidate: CustomerCandidateOut, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = candidate.customer_name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = candidate.cust_nb,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EvidenceSection(
    state: RequestUiState,
    request: com.vendo.core.network.dto.RequestDetail,
    onTogglePlayback: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    Text(text = "EVIDENCE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
    AudioPlayerRow(state = state, onToggle = onTogglePlayback, onSeek = onSeek)
    Spacer(Modifier.height(12.dp))
    Text(
        text = "What VeNdO heard",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = request.transcript?.takeIf { it.isNotBlank() } ?: "No transcript is available for this recording.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    // config.py's transcript_conf_min (0.5) is the backend's own cutoff for
    // "large parts were a guess" - mirrored here rather than picking an
    // arbitrary UI threshold.
    val conf = request.transcript_conf
    if (conf != null && conf < 0.5) {
        Spacer(Modifier.height(6.dp))
        StatusBadge(text = "Some parts of this recording may be unclear.", tone = VendoTone.Warning)
    }
}

@Composable
private fun AudioPlayerRow(state: RequestUiState, onToggle: () -> Unit, onSeek: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (state.isLoadingAudio) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (state.isPlayingAudio) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlayingAudio) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Slider(
            value = state.playbackPositionMs.toFloat().coerceAtMost(state.playbackDurationMs.toFloat().coerceAtLeast(1f)),
            onValueChange = { onSeek(it.toInt()) },
            valueRange = 0f..state.playbackDurationMs.coerceAtLeast(1).toFloat(),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${formatMs(state.playbackPositionMs)} / ${formatMs(state.playbackDurationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatMs(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}

@Composable
private fun ItemsSection(
    state: RequestUiState,
    viewModel: RequestViewModel,
    lineRequesters: MutableMap<Int, BringIntoViewRequester>,
) {
    Text(text = "ITEMS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
    if (state.editableLines.isEmpty()) {
        Text(
            text = "No items were understood from this recording.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    state.editableLines.forEach { line ->
        val requester = lineRequesters.getOrPut(line.lineNb) { BringIntoViewRequester() }
        Box(modifier = Modifier.bringIntoViewRequester(requester)) {
            ItemRow(
                line = line,
                isEditing = state.isEditing && !state.isReadOnly,
                onQtyChange = { viewModel.updateLine(line.lineNb, qty = it) },
                onUomChange = { viewModel.updateLine(line.lineNb, uom = it) },
                onChangeProduct = { viewModel.openProductPicker(line.lineNb) },
                onDelete = { viewModel.deleteLine(line.lineNb) },
                onUndoDelete = { viewModel.undoDelete(line.lineNb) },
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
    // QRA bonus lines have no PendingLine of their own yet (see backend's
    // preview_qra) - shown read-only, never editable/deletable like a
    // real line, since Accept doesn't send them at all: the server adds
    // the real one itself inside commit().
    state.request?.qra_bonus_lines?.forEach { bonus ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = bonus.item_desc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                StatusBadge(text = "FREE (QRA bonus)", tone = VendoTone.Positive)
            }
            Text(
                text = "${bonus.qty} ${bonus.uom.orEmpty()}".trim(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
    if (state.isEditing && !state.isReadOnly) {
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PillButton(text = "+ ADD ITEM", variant = PillVariant.DarkGray, onClick = viewModel::addLine)
        }
    }
}

@Composable
private fun ItemRow(
    line: EditableLine,
    isEditing: Boolean,
    onQtyChange: (String) -> Unit,
    onUomChange: (String) -> Unit,
    onChangeProduct: () -> Unit,
    onDelete: () -> Unit,
    onUndoDelete: () -> Unit,
) {
    if (line.isRemoved) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\"${line.displayLabel()}\" removed",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onUndoDelete) { Text("UNDO") }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = line.displayLabel(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isEditing) {
                QuantityStepper(value = line.qty, onValueChange = onQtyChange)
            } else {
                Text(
                    text = "${line.qty.ifBlank { "?" }} ${line.uom}".trim(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (line.rawText.isNotBlank() && line.rawText != line.itemDesc) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Spoken: “${line.rawText}”",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val (label, tone) = when (line.matchStatus) {
                LineMatchStatus.CONFIRMED -> "Confirmed" to VendoTone.Positive
                LineMatchStatus.NEEDS_REVIEW -> "Needs review" to VendoTone.Warning
                LineMatchStatus.CONFLICT -> "Doesn't match" to VendoTone.Danger
                LineMatchStatus.UNRESOLVED -> "Unresolved" to VendoTone.Danger
            }
            StatusBadge(text = label, tone = tone)
            changeLabel(line.change)?.let { StatusBadge(text = it, tone = VendoTone.Info) }
            line.qraSubstitutedItemDesc?.let {
                StatusBadge(text = "QRA: becomes \"$it\"", tone = VendoTone.Info)
            }
            line.qraUnitPrice?.let {
                StatusBadge(text = "QRA price: $it", tone = VendoTone.Info)
            }
            if (line.matchStatus == LineMatchStatus.CONFIRMED) {
                matchMethodLabel(line.matchMethod)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (line.matchStatus == LineMatchStatus.CONFLICT) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "The spoken size, color, or promotion doesn't match this product.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        line.lineFlags.forEach { flag ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = lineFlagMessage(flag),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (isEditing) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(text = "CHANGE PRODUCT", variant = PillVariant.DarkGray, onClick = onChangeProduct)
                PillButton(text = "DELETE", variant = PillVariant.DarkGray, onClick = onDelete)
            }
            Spacer(Modifier.height(8.dp))
            UomSelector(value = line.uom, onValueChange = onUomChange)
        } else if (line.matchStatus != LineMatchStatus.CONFIRMED) {
            Spacer(Modifier.height(4.dp))
            PillButton(text = "REVIEW", variant = PillVariant.DarkGray, onClick = onChangeProduct)
        }
    }
}

/** The business only orders in two units (see backend's UOM_SYNONYMS
 * docstring) - a fixed Each/Packet choice instead of free text keeps a
 * reviewer's edit from drifting into a value nothing downstream recognizes,
 * and pairs with RequestUiState.blockingIssues refusing to accept a line
 * with no unit chosen at all. */
@Composable
private fun UomSelector(value: String, onValueChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("EACH" to "Each", "PKT" to "Packet").forEach { (code, label) ->
            FilterChip(
                selected = value.equals(code, ignoreCase = true),
                onClick = { onValueChange(code) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun BlockersCard(blockers: List<BlockingIssue>, onJump: (Int?) -> Unit) {
    RequestCard {
        Text(
            text = "${blockers.size} thing${if (blockers.size == 1) "" else "s"} need${if (blockers.size == 1) "s" else ""} attention before this order can be accepted",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            blockers.forEach { issue ->
                Text(
                    text = "• ${issue.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (issue.lineNb != null) {
                                Modifier.clickable { onJump(issue.lineNb) }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun ActionsRow(
    canAccept: Boolean,
    isEditing: Boolean,
    onToggleEdit: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PillButton(text = "Reject", variant = PillVariant.DarkGray, onClick = onReject)
            PillButton(
                text = "Accept",
                variant = PillVariant.PrimaryBlue,
                enabled = canAccept,
                onClick = onAccept,
            )
        }
        PillButton(
            text = if (isEditing) "Done editing" else "Edit",
            variant = PillVariant.DarkGray,
            onClick = onToggleEdit,
        )
    }
}

@Composable
private fun RejectDialog(onDismiss: () -> Unit, onConfirm: (String, String?) -> Unit) {
    val reasons = listOf(
        "Customer unreachable", "Wrong customer", "Duplicate order", "Pricing issue", "Other",
    )
    var selected by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reject this order") },
        text = {
            Column {
                Text(
                    "Why is this order being rejected? The reason is kept with the request.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(reasons.size) { i ->
                        val reason = reasons[i]
                        FilterChip(
                            selected = selected == reason,
                            onClick = { selected = reason },
                            label = { Text(reason) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Additional detail (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = { selected?.let { onConfirm(it, note.ifBlank { null }) } },
            ) { Text("Reject") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProductPickerDialog(
    line: EditableLine,
    onSearch: (String) -> Unit,
    onSelect: (CandidateOut) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf(line.itemDesc.ifBlank { line.rawText }) }
    Dialog(onDismissRequest = onDismiss) {
        RequestCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Choose a product", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search catalogue") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onSearch(query) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (line.candidates.isEmpty()) {
                Text(
                    text = "Search the catalogue above, or leave this item unresolved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(line.candidates.size) { i ->
                        val candidate = line.candidates[i]
                        CandidateRow(candidate = candidate, selected = candidate.item_nb == line.itemNb, onClick = { onSelect(candidate) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onDismiss) { Text("Leave unresolved") }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: CandidateOut, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.item_desc,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${candidate.item_nb} · ${candidate.category}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (candidate.attribute_conflict) {
                Text(
                    text = "Size, color, or promotion may not match what was said.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        StatusBadge(
            text = candidateQualityLabel(candidate.score),
            tone = if (candidate.attribute_conflict) VendoTone.Danger else VendoTone.Neutral,
        )
    }
}
