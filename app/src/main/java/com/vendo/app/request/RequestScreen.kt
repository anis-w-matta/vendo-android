@file:OptIn(ExperimentalFoundationApi::class)

package com.vendo.app.request

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vendo.app.common.ErrorSnackbarEffect
import com.vendo.core.designsystem.components.PillButton
import com.vendo.core.designsystem.components.RequestCard
import com.vendo.core.designsystem.components.StatusBadge
import com.vendo.core.designsystem.components.VendoTone
import com.vendo.core.designsystem.vendoContentMaxWidth
import com.vendo.core.designsystem.vendoScreenPadding
import kotlinx.coroutines.launch

/** Top-level orchestration for the pending-request review screen: which
 * section to show for the current load/edit state, and wiring the shared
 * dialogs (reject/product-picker/customer-picker) that float above it.
 * The sections themselves live in RequestHeaderSection.kt,
 * RequestEvidenceSection.kt, RequestItemsSection.kt, and
 * RequestActionsAndDialogs.kt - split out of this one file purely for
 * navigability, no behavior change. */
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
