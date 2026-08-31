package com.vendo.app.request

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.app.cache.CacheRepository
import com.vendo.app.navigation.VendoDestinations
import com.vendo.core.datastore.SettingsDataStore
import com.vendo.core.network.ApiService
import com.vendo.core.network.dto.AcceptIn
import com.vendo.core.network.dto.CandidateOut
import com.vendo.core.network.dto.CustomerCandidateOut
import com.vendo.core.network.dto.LineEditIn
import com.vendo.core.network.dto.LineOut
import com.vendo.core.network.dto.RejectIn
import com.vendo.core.network.dto.RequestDetail
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

enum class LineMatchStatus { UNRESOLVED, CONFLICT, NEEDS_REVIEW, CONFIRMED }

/** One order line as the reviewer is currently editing it. Mirrors LineOut
 * plus purely-local edit state (isRemoved) - a deleted line stays in this
 * list (rendered struck-through, with Undo) rather than being dropped
 * outright, so a mis-tap doesn't silently lose a line before Accept is even
 * pressed (spec: delete should be safe, undo-able). */
data class EditableLine(
    val lineNb: Int,
    val rawText: String,
    val itemDesc: String,
    val qty: String,
    val uom: String,
    val itemNb: String?,
    val candidates: List<CandidateOut> = emptyList(),
    val matchMethod: String? = null,
    val lineFlags: List<String> = emptyList(),
    val change: String? = null,
    val isRemoved: Boolean = false,
    // QRA preview - what this line's price/item WOULD become at commit
    // time under the customer's active QRA agreement, if any. Purely
    // informational: itemNb/itemDesc/qty above still show what the
    // salesman actually ordered, and this never affects Accept's payload.
    val qraUnitPrice: String? = null,
    val qraIsFree: Boolean = false,
    val qraSubstitutedItemDesc: String? = null,
) {
    /** True if the candidate this line actually resolved to is the one the
     * resolver flagged as conflicting with a spoken size/color/promotion -
     * distinct from just "not yet resolved". */
    val hasConflict: Boolean
        get() = candidates.any { it.item_nb == itemNb && it.attribute_conflict }

    val matchStatus: LineMatchStatus
        get() = when {
            itemNb.isNullOrBlank() -> LineMatchStatus.UNRESOLVED
            hasConflict -> LineMatchStatus.CONFLICT
            lineFlags.isNotEmpty() -> LineMatchStatus.NEEDS_REVIEW
            matchMethod == "fuzzy" || matchMethod == "substring" -> LineMatchStatus.NEEDS_REVIEW
            else -> LineMatchStatus.CONFIRMED
        }

    val qtyValue: BigDecimal? get() = qty.trim().toBigDecimalOrNull()
    val hasValidQty: Boolean get() = (qtyValue?.signum() ?: -1) > 0
    val hasUom: Boolean get() = uom.isNotBlank()
}

fun EditableLine.displayLabel(): String =
    itemDesc.ifBlank { rawText }.ifBlank { "Item $lineNb" }

data class BlockingIssue(val lineNb: Int?, val message: String)

data class RequestUiState(
    val isLoading: Boolean = true,
    val request: RequestDetail? = null,
    val editableLines: List<EditableLine> = emptyList(),
    val isEditing: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val accepted: Boolean = false,
    val rejected: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val isLoadingAudio: Boolean = false,
    val playbackPositionMs: Int = 0,
    val playbackDurationMs: Int = 0,
    val currentOperator: String? = null,
    /** Non-null while a product picker/search sheet is open for this line
     * number - null means none is open. */
    val searchingLineNb: Int? = null,
    /** An operator's manual pick for a request the voice pipeline couldn't
     * identify a customer for - staged locally like a line edit, only sent
     * to the server as part of the accept() call (see AcceptIn.cust_nb). */
    val selectedCustomer: CustomerCandidateOut? = null,
    val customerCandidates: List<CustomerCandidateOut> = emptyList(),
    val isPickingCustomer: Boolean = false,
    /** A return's operator-corrected/supplied order reference - staged
     * locally like a line edit, only sent at accept() (AcceptIn.target_order_nb).
     * The server derives cust_nb from this order rather than a separate
     * customer pick (see commit.py) - a return's customer always comes from
     * the order it's returning against. */
    val editedTargetOrderNb: String? = null,
) {
    /** A committed/rejected request is history, not a pending decision -
     * editing/Accept/Reject/Callback only make sense while it's still open. */
    val isDecided: Boolean get() = request?.status in setOf("committed", "rejected")

    /** Someone other than the current user already has this request locked
     * (app/api/queue.py's claim - row-locked so two reviewers can't step on
     * each other). View-only in this state (spec section 39). */
    val isClaimedByAnother: Boolean
        get() {
            val assigned = request?.assigned_to ?: return false
            return !isDecided && assigned != currentOperator
        }

    val isReadOnly: Boolean get() = isDecided || isClaimedByAnother

    val visibleLines: List<EditableLine> get() = editableLines.filterNot { it.isRemoved }

    /** Everything that must be true before Accept is allowed to even try -
     * checked client-side so a rep sees a specific, actionable list instead
     * of a generic failure after a round trip (spec section 38). A non-order
     * recording (primary_intent == "other", e.g. audio_too_long or an
     * unrecognized command) is never "fixable" here - it has no lines to
     * resolve, so it's excluded rather than reported as N broken items. */
    val blockingIssues: List<BlockingIssue>
        get() {
            val r = request ?: return emptyList()
            if (isNonOrder(r.primary_intent)) return emptyList()
            val issues = mutableListOf<BlockingIssue>()
            val isReturn = r.primary_intent == "return_order"
            val isReorder = r.primary_intent == "repeat_order" ||
                r.primary_intent == "repeat_order_adjusted"
            // A return's/reorder's customer is derived from the order it
            // references, never picked independently - so what "unblocks"
            // this differs from every other order type (see AcceptIn's
            // target_order_nb / cust_nb docs and commit.py).
            val customerKnown = if (isReturn || isReorder) {
                !r.target_order_nb.isNullOrBlank() || !editedTargetOrderNb.isNullOrBlank() ||
                    !r.cust_nb.isNullOrBlank()
            } else {
                !r.cust_nb.isNullOrBlank() || selectedCustomer != null
            }
            if (!customerKnown) {
                issues += BlockingIssue(
                    null,
                    if (isReturn) "No order number has been identified to return against."
                    else if (isReorder) "No order number has been identified to repeat."
                    else "No customer has been identified for this order.",
                )
            }
            val active = visibleLines
            if (active.isEmpty()) {
                issues += BlockingIssue(null, "This order has no items.")
            } else {
                active.forEach { line ->
                    when {
                        line.itemNb.isNullOrBlank() -> issues += BlockingIssue(
                            line.lineNb, "\"${line.displayLabel()}\" still needs a matched product.")
                        !line.hasValidQty -> issues += BlockingIssue(
                            line.lineNb, "${line.displayLabel()} needs a valid quantity.")
                        !line.hasUom -> issues += BlockingIssue(
                            line.lineNb, "${line.displayLabel()} needs a unit (Each or Packet).")
                    }
                }
            }
            return issues
        }
}

/** Loads one PendingRequest, claims it (row-locked server-side against
 * concurrent reviewers), and drives every reviewer action on it: editing
 * lines, searching/replacing a matched product, accept, reject, and audio
 * playback. */
@HiltViewModel
class RequestViewModel @Inject constructor(
    private val api: ApiService,
    private val settings: SettingsDataStore,
    private val cache: CacheRepository,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestUiState())
    val uiState: StateFlow<RequestUiState> = _uiState.asStateFlow()

    private val audioPlayer = RequestAudioPlayer(appContext, settings, viewModelScope) { transform ->
        _uiState.value = transform(_uiState.value)
    }

    private val requestedId: Int = savedStateHandle.get<Int>(VendoDestinations.REQUEST_ARG) ?: -1

    init {
        load(requestedId)
    }

    private fun load(requestId: Int) {
        viewModelScope.launch {
            val operator = settings.loginId.first()
            _uiState.value = RequestUiState(isLoading = true, currentOperator = operator)
            try {
                val resolvedId = if (requestId >= 0) {
                    requestId
                } else {
                    // No id passed (opened from the drawer) - show the most
                    // recent pending request, since there's no separate
                    // request-list screen in the 5-screen spec.
                    api.listQueue(limit = 1).firstOrNull()?.id
                }
                if (resolvedId == null) {
                    _uiState.value = RequestUiState(
                        isLoading = false, currentOperator = operator,
                        error = "There's no pending request to review right now.",
                    )
                    return@launch
                }
                var detail = api.getRequest(resolvedId)
                val decided = detail.status == "committed" || detail.status == "rejected"
                // Claim it for myself unless it's already someone else's or
                // already decided - makes the row-level lock the backend
                // already enforces visible, instead of silently allowing an
                // edit that a concurrent accept() would just reject later.
                if (!decided && (detail.assigned_to == null || detail.assigned_to == operator)) {
                    try {
                        api.claim(resolvedId)
                        detail = api.getRequest(resolvedId)
                    } catch (_: Exception) {
                        // Someone else claimed it in the gap between our GET
                        // and our claim - re-fetch so assigned_to reflects
                        // reality and the screen falls back to read-only
                        // instead of trusting stale state.
                        detail = try { api.getRequest(resolvedId) } catch (_: Exception) { detail }
                    }
                }
                _uiState.value = RequestUiState(
                    isLoading = false,
                    request = detail,
                    editableLines = detail.lines.map { it.toEditable() },
                    currentOperator = operator,
                )
            } catch (e: Exception) {
                _uiState.value = RequestUiState(
                    isLoading = false, currentOperator = operator, error = e.toUserMessage(),
                )
            }
        }
    }

    fun retry() = load(_uiState.value.request?.id ?: requestedId)

    fun toggleEdit() {
        if (_uiState.value.isReadOnly) return
        _uiState.value = _uiState.value.copy(isEditing = !_uiState.value.isEditing)
    }

    fun updateLine(lineNb: Int, itemDesc: String? = null, qty: String? = null, uom: String? = null) {
        val lines = _uiState.value.editableLines.map { line ->
            if (line.lineNb == lineNb) {
                line.copy(
                    itemDesc = itemDesc ?: line.itemDesc,
                    qty = qty ?: line.qty,
                    uom = uom ?: line.uom,
                )
            } else {
                line
            }
        }
        _uiState.value = _uiState.value.copy(editableLines = lines)
    }

    /** Writes the candidate's item_nb (the catalogue id), never just its
     * description - the backend only ever resolves an order line by
     * item_nb. Clears stale flags/confidence from the old auto-match since
     * an operator's explicit pick is definitionally resolved.
     *
     * If another line already has this same item_nb (e.g. the reviewer
     * picked the same product twice via "+ ADD ITEM", or repointed a
     * line's product at one already on the order), that's a duplicate
     * order line, not two separate ones - the picked quantity is folded
     * into the existing line instead, and this line is marked removed (not
     * dropped outright, so accept() still tells the server to delete it -
     * see AcceptIn.removed_line_nbs). A uom mismatch only blocks the merge
     * when BOTH sides actually specify one and they disagree (e.g. "EACH"
     * vs "BOX") - a freshly added line has no uom yet, so a blank on
     * either side doesn't stop the merge. Mirrors the same merge the
     * backend applies when a spoken order names one item twice
     * (resolve_order.py's _merge_duplicate_lines).
     */
    fun selectCandidate(lineNb: Int, candidate: CandidateOut) {
        val current = _uiState.value.editableLines
        val target = current.find { it.lineNb == lineNb } ?: return
        val duplicate = current.find {
            it.lineNb != lineNb && !it.isRemoved && it.itemNb == candidate.item_nb &&
                (it.uom.isBlank() || target.uom.isBlank() ||
                    it.uom.trim().equals(target.uom.trim(), ignoreCase = true))
        }
        val lines = if (duplicate != null) {
            val mergedQty = (duplicate.qtyValue ?: BigDecimal.ZERO) + (target.qtyValue ?: BigDecimal.ONE)
            current.map { line ->
                when (line.lineNb) {
                    duplicate.lineNb -> line.copy(qty = mergedQty.toPlainString())
                    lineNb -> line.copy(isRemoved = true)
                    else -> line
                }
            }
        } else {
            current.map { line ->
                if (line.lineNb == lineNb) {
                    line.copy(
                        itemNb = candidate.item_nb,
                        itemDesc = candidate.item_desc,
                        matchMethod = "manual",
                        lineFlags = emptyList(),
                    )
                } else {
                    line
                }
            }
        }
        _uiState.value = _uiState.value.copy(editableLines = lines, searchingLineNb = null)
    }

    fun onTargetOrderNbEdited(value: String) {
        _uiState.value = _uiState.value.copy(editedTargetOrderNb = value)
    }

    fun deleteLine(lineNb: Int) = setRemoved(lineNb, true)
    fun undoDelete(lineNb: Int) = setRemoved(lineNb, false)

    private fun setRemoved(lineNb: Int, removed: Boolean) {
        val lines = _uiState.value.editableLines.map { line ->
            if (line.lineNb == lineNb) line.copy(isRemoved = removed) else line
        }
        _uiState.value = _uiState.value.copy(editableLines = lines)
    }

    /** A new blank line for the operator to resolve via the product picker -
     * the backend creates a real PendingLine for any line_nb it doesn't
     * already know about (commit.py._apply_edits). */
    fun addLine() {
        val lines = _uiState.value.editableLines
        val nextNb = (lines.maxOfOrNull { it.lineNb } ?: 0) + 1
        _uiState.value = _uiState.value.copy(
            editableLines = lines + EditableLine(
                lineNb = nextNb, rawText = "", itemDesc = "", qty = "1", uom = "", itemNb = null,
            ),
            searchingLineNb = nextNb,
        )
    }

    fun openCustomerPicker() {
        if (_uiState.value.isReadOnly) return
        _uiState.value = _uiState.value.copy(isPickingCustomer = true)
    }

    fun closeCustomerPicker() {
        _uiState.value = _uiState.value.copy(isPickingCustomer = false, customerCandidates = emptyList())
    }

    /** Same fuzzy resolver the pipeline uses to auto-match a customer during
     * voice intake (GET /customers/search), just surfaced for an explicit
     * human pick instead of an automatic one. */
    fun searchCustomer(query: String) {
        if (query.isBlank()) return
        _uiState.value = _uiState.value.copy(error = null)
        viewModelScope.launch {
            try {
                val results = api.searchCustomers(query.trim())
                _uiState.value = _uiState.value.copy(customerCandidates = results)
            } catch (e: Exception) {
                // No network (or the server's unreachable) - fall back to
                // the offline cache (see CacheRepository, populated only
                // when the operator taps Refresh) instead of leaving the
                // picker stuck on an error with nothing to select.
                val offline = runCatching {
                    cache.searchCustomersOffline(query.trim())
                }.getOrDefault(emptyList())
                if (offline.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(customerCandidates = offline)
                } else {
                    _uiState.value = _uiState.value.copy(error = e.toUserMessage())
                }
            }
        }
    }

    fun selectCustomer(candidate: CustomerCandidateOut) {
        _uiState.value = _uiState.value.copy(
            selectedCustomer = candidate,
            isPickingCustomer = false,
            customerCandidates = emptyList(),
        )
    }

    fun openProductPicker(lineNb: Int) {
        _uiState.value = _uiState.value.copy(searchingLineNb = lineNb)
    }

    fun closeProductPicker() {
        _uiState.value = _uiState.value.copy(searchingLineNb = null)
    }

    /** Looks up candidates via the same fuzzy resolver every auto-extracted
     * line already uses (GET /items/search). */
    fun searchItem(lineNb: Int, query: String) {
        if (query.isBlank()) return
        _uiState.value = _uiState.value.copy(error = null)
        viewModelScope.launch {
            try {
                val results = api.searchItems(query.trim())
                val lines = _uiState.value.editableLines.map { line ->
                    if (line.lineNb == lineNb) line.copy(candidates = results) else line
                }
                _uiState.value = _uiState.value.copy(editableLines = lines)
            } catch (e: Exception) {
                // Same offline fallback as searchCustomer - see its comment.
                val offline = runCatching { cache.searchItemsOffline(query.trim()) }
                    .getOrDefault(emptyList())
                if (offline.isNotEmpty()) {
                    val lines = _uiState.value.editableLines.map { line ->
                        if (line.lineNb == lineNb) line.copy(candidates = offline) else line
                    }
                    _uiState.value = _uiState.value.copy(editableLines = lines)
                } else {
                    _uiState.value = _uiState.value.copy(error = e.toUserMessage())
                }
            }
        }
    }

    fun reject(reason: String, note: String?) {
        val state = _uiState.value
        val request = state.request ?: return
        if (reason.isBlank() || state.isReadOnly) return
        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                api.reject(request.id, RejectIn(reason = reason.trim(), note = note?.trim()?.ifBlank { null }))
                _uiState.value = _uiState.value.copy(isSubmitting = false, rejected = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSubmitting = false, error = e.toUserMessage())
            }
        }
    }

    fun accept() {
        val state = _uiState.value
        val request = state.request ?: return
        if (state.isReadOnly || state.blockingIssues.isNotEmpty()) return
        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                val orderType = if (request.primary_intent == "return_order") "RETURN" else "SO"
                val active = state.editableLines.filterNot { it.isRemoved }
                val lineEdits = active.map {
                    LineEditIn(
                        line_nb = it.lineNb,
                        item_nb = it.itemNb,
                        item_desc = it.itemDesc.ifBlank { null },
                        qty = it.qty.ifBlank { null },
                        uom = it.uom.ifBlank { null },
                    )
                }
                val removed = state.editableLines.filter { it.isRemoved }.map { it.lineNb }
                api.accept(
                    request.id,
                    AcceptIn(
                        order_type = orderType,
                        lines = lineEdits,
                        removed_line_nbs = removed,
                        cust_nb = state.selectedCustomer?.cust_nb,
                        target_order_nb = state.editedTargetOrderNb?.trim()?.ifBlank { null },
                    ),
                )
                _uiState.value = _uiState.value.copy(isSubmitting = false, accepted = true)
            } catch (e: Exception) {
                // If the edits themselves are safe (already validated
                // client-side), only the submission failed - the user's
                // edits stay right here in state for another attempt rather
                // than being wiped by a failed request.
                _uiState.value = _uiState.value.copy(isSubmitting = false, error = e.toUserMessage())
            }
        }
    }

    /** Delegates to RequestAudioPlayer, which owns the MediaPlayer instance
     * and progress ticker - see its class doc. */
    fun togglePlayback() {
        val request = _uiState.value.request ?: return
        audioPlayer.toggle(request.audio_url, _uiState.value.isLoadingAudio)
    }

    fun seekTo(ms: Int) {
        audioPlayer.seekTo(ms)
    }

    override fun onCleared() {
        audioPlayer.release()
        super.onCleared()
    }
}

private fun LineOut.toEditable() = EditableLine(
    lineNb = line_nb,
    rawText = raw_text,
    itemDesc = item_desc ?: raw_text,
    qty = qty ?: "",
    uom = uom ?: "",
    itemNb = item_nb,
    candidates = candidates,
    matchMethod = match_method,
    lineFlags = line_flags,
    change = change,
    qraUnitPrice = qra_unit_price,
    qraIsFree = qra_is_free,
    qraSubstitutedItemDesc = qra_substituted_item_desc,
)
