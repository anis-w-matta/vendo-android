package com.vendo.app.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.datastore.SettingsDataStore
import com.vendo.core.network.ApiService
import com.vendo.whisper.AudioRecorder
import com.vendo.whisper.WavWriter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

data class RecordUiState(
    val isRecording: Boolean = false,
    val hasAudio: Boolean = false,
    val isTranscribing: Boolean = false,
    val transcript: String = "",
    val hint: String? = null,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

sealed interface RecordEvent {
    data class OpenRequest(val requestId: Int) : RecordEvent
    data object Submitted : RecordEvent
}

/** Recording is transcribed via a synchronous backend call to Gemini
 * (POST /ingest/transcribe-preview) right after stopping, so the salesman
 * can review/correct the text before submitting - the on-device whisper.cpp
 * path was swapped out after proving too slow on real hardware (minutes
 * for a few seconds of speech on a mid-range phone). The (possibly edited)
 * transcript is then sent as POST /ingest/voice's `transcript` field, which
 * the pipeline treats as already-transcribed and skips a second Gemini
 * call for. */
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val api: ApiService,
    private val settings: SettingsDataStore,
    private val audioRecorder: AudioRecorder,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecordEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<RecordEvent> = _events

    private var lastPcm: ShortArray? = null
    private var detectedLanguage: String? = null

    fun startRecording() {
        if (_uiState.value.isRecording) return
        _uiState.value = _uiState.value.copy(
            isRecording = true, error = null, hint = null, transcript = "",
        )
        detectedLanguage = null
        viewModelScope.launch {
            val pcm = audioRecorder.record()
            lastPcm = pcm
            _uiState.value = _uiState.value.copy(isRecording = false, hasAudio = pcm.isNotEmpty())
            if (pcm.isNotEmpty()) fetchPreview(pcm)
        }
    }

    fun stopRecording() {
        audioRecorder.stop()
    }

    fun onTranscriptEdited(value: String) {
        _uiState.value = _uiState.value.copy(transcript = value)
    }

    private suspend fun fetchPreview(pcm: ShortArray) {
        _uiState.value = _uiState.value.copy(isTranscribing = true)
        try {
            val wavBytes = WavWriter.write(pcm)
            val audioPart = MultipartBody.Part.createFormData(
                "audio", "order.wav",
                wavBytes.toRequestBody("audio/wav".toMediaType()),
            )
            val result = api.transcribePreview(audioPart)
            detectedLanguage = result.language
            _uiState.value = _uiState.value.copy(isTranscribing = false, transcript = result.transcript)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isTranscribing = false,
                hint = "Preview transcription failed - type the order manually below.",
            )
        }
    }

    /** accept=true waits for the backend to draft the request, then opens
     * the Request screen; accept=false ("Draft") submits and leaves the
     * salesman on Record - the request is picked up later from Log
     * Query/queue. This is an interpretation of Accept vs Draft, not
     * specified by the reference design - see the plan's open items. */
    fun submit(accept: Boolean) {
        val state = _uiState.value
        if (!state.hasAudio || state.transcript.isBlank()) {
            _uiState.value = state.copy(error = "Nothing to submit yet")
            return
        }
        _uiState.value = state.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                val loginId = settings.loginId.first() ?: "unknown"
                val wavBytes = WavWriter.write(lastPcm ?: ShortArray(0))
                val audioPart = MultipartBody.Part.createFormData(
                    "audio", "order.wav",
                    wavBytes.toRequestBody("audio/wav".toMediaType()),
                )
                // No caller-phone field exists on the RECORD screen per the
                // reference design; the scripted grammar resolves the
                // customer from the spoken name in the transcript, not from
                // this value; it's carried mainly for the backend's audit
                // trail. Revisit if a real caller phone number needs to be
                // captured for this app's actual usage pattern.
                val phonePart = "salesman:$loginId".toRequestBody("text/plain".toMediaType())
                val transcriptPart = state.transcript.toRequestBody("text/plain".toMediaType())
                val languagePart = (detectedLanguage ?: "ar").toRequestBody("text/plain".toMediaType())

                val ingestResult = api.ingestVoice(phonePart, audioPart, transcriptPart, languagePart)

                if (!accept) {
                    _uiState.value = RecordUiState()
                    lastPcm = null
                    detectedLanguage = null
                    _events.tryEmit(RecordEvent.Submitted)
                    return@launch
                }

                var requestId: Int? = null
                for (attempt in 1..15) {
                    delay(1000)
                    val status = api.voiceStatus(ingestResult.id)
                    if (status.request_id != null) {
                        requestId = status.request_id
                        break
                    }
                    if (status.status == "failed" || status.status == "too_long") {
                        break
                    }
                }

                _uiState.value = RecordUiState()
                lastPcm = null
                detectedLanguage = null
                val id = requestId
                if (id != null) {
                    _events.tryEmit(RecordEvent.OpenRequest(id))
                } else {
                    _events.tryEmit(RecordEvent.Submitted)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    error = e.message ?: "Submit failed",
                )
            }
        }
    }
}
