package com.vendo.app.request

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.app.navigation.VendoDestinations
import com.vendo.core.datastore.SettingsDataStore
import com.vendo.core.network.ApiService
import com.vendo.core.network.BuildConfig
import com.vendo.core.network.dto.AcceptIn
import com.vendo.core.network.dto.LineEditIn
import com.vendo.core.network.dto.LineOut
import com.vendo.core.network.dto.RejectIn
import com.vendo.core.network.dto.RequestDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditableLine(
    val lineNb: Int,
    val itemDesc: String,
    val qty: String,
    val uom: String,
    // Preserved from the resolver's automatic match, or set by tapping one
    // of `candidates` in EDIT mode. The backend rejects a line with no
    // item_nb at all (every line needs a resolved item + qty to commit) -
    // free-text item_desc edits alone can't fix an unresolved line, since
    // the reference design has no item search/picker UI. Candidates are
    // the only resolution path available within the spec's minimal card.
    val itemNb: String?,
    val candidates: List<com.vendo.core.network.dto.CandidateOut> = emptyList(),
)

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
)

@HiltViewModel
class RequestViewModel @Inject constructor(
    private val api: ApiService,
    private val settings: SettingsDataStore,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var mediaPlayer: MediaPlayer? = null

    private val _uiState = MutableStateFlow(RequestUiState())
    val uiState: StateFlow<RequestUiState> = _uiState.asStateFlow()

    init {
        val requestId = savedStateHandle.get<Int>(VendoDestinations.REQUEST_ARG) ?: -1
        load(requestId)
    }

    private fun load(requestId: Int) {
        viewModelScope.launch {
            _uiState.value = RequestUiState(isLoading = true)
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
                    _uiState.value = RequestUiState(isLoading = false, error = "No pending request")
                    return@launch
                }
                val detail = api.getRequest(resolvedId)
                _uiState.value = RequestUiState(
                    isLoading = false,
                    request = detail,
                    editableLines = detail.lines.map { it.toEditable() },
                )
            } catch (e: Exception) {
                _uiState.value = RequestUiState(isLoading = false, error = e.message ?: "Failed to load")
            }
        }
    }

    fun toggleEdit() {
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

    fun selectCandidate(lineNb: Int, candidate: com.vendo.core.network.dto.CandidateOut) {
        val lines = _uiState.value.editableLines.map { line ->
            if (line.lineNb == lineNb) {
                line.copy(itemNb = candidate.item_nb, itemDesc = candidate.item_desc)
            } else {
                line
            }
        }
        _uiState.value = _uiState.value.copy(editableLines = lines)
    }

    fun reject(reason: String) {
        val state = _uiState.value
        val request = state.request ?: return
        if (reason.isBlank()) return
        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                api.reject(request.id, RejectIn(reason = reason.trim()))
                _uiState.value = _uiState.value.copy(isSubmitting = false, rejected = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = e.message ?: "Reject failed",
                )
            }
        }
    }

    /** Streams the recording straight off the backend's /audio/{id} - no
     * bearer token needed there (matches console.html's plain <audio> tag,
     * which can't attach one either), but the shared-secret API key is
     * passed the same way AuthInterceptor adds it to Retrofit calls, since
     * MediaPlayer's networking doesn't go through OkHttp. */
    fun togglePlayback() {
        val player = mediaPlayer
        if (player != null) {
            if (player.isPlaying) player.pause() else player.start()
            _uiState.value = _uiState.value.copy(isPlayingAudio = player.isPlaying)
            return
        }
        val request = _uiState.value.request ?: return
        _uiState.value = _uiState.value.copy(isLoadingAudio = true, error = null)
        viewModelScope.launch {
            try {
                val url = resolveAudioUrl(request.audio_url)
                val headers = if (BuildConfig.API_KEY.isNotBlank()) {
                    mapOf("X-Api-Key" to BuildConfig.API_KEY)
                } else {
                    emptyMap()
                }
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(appContext, Uri.parse(url), headers)
                    setOnPreparedListener {
                        it.start()
                        _uiState.value = _uiState.value.copy(isLoadingAudio = false, isPlayingAudio = true)
                    }
                    setOnCompletionListener {
                        _uiState.value = _uiState.value.copy(isPlayingAudio = false)
                    }
                    setOnErrorListener { _, _, _ ->
                        _uiState.value = _uiState.value.copy(
                            isLoadingAudio = false,
                            isPlayingAudio = false,
                            error = "Couldn't play the recording",
                        )
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingAudio = false,
                    error = "Couldn't play the recording",
                )
            }
        }
    }

    private suspend fun resolveAudioUrl(path: String): String {
        val stored = settings.currentServerUrl()?.takeIf { it.isNotBlank() }
        val base = (stored ?: BuildConfig.BASE_URL).trimEnd('/')
        return base + path
    }

    override fun onCleared() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onCleared()
    }

    fun accept() {
        val state = _uiState.value
        val request = state.request ?: return
        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                val orderType = if (request.primary_intent == "return_order") "RETURN" else "SO"
                val lineEdits = state.editableLines.map {
                    LineEditIn(
                        line_nb = it.lineNb,
                        item_nb = it.itemNb,
                        item_desc = it.itemDesc.ifBlank { null },
                        qty = it.qty.ifBlank { null },
                        uom = it.uom.ifBlank { null },
                    )
                }
                api.accept(request.id, AcceptIn(order_type = orderType, lines = lineEdits))
                _uiState.value = _uiState.value.copy(isSubmitting = false, accepted = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = e.message ?: "Accept failed",
                )
            }
        }
    }
}

private fun LineOut.toEditable() = EditableLine(
    lineNb = line_nb,
    itemDesc = item_desc ?: raw_text,
    qty = qty ?: "",
    uom = uom ?: "",
    itemNb = item_nb,
    candidates = candidates,
)
