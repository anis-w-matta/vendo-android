package com.vendo.app.record

import android.content.Context
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vendo.core.audio.AUDIO_SAMPLE_RATE
import com.vendo.core.audio.AudioRecorder
import com.vendo.core.audio.AudioUnavailableException
import com.vendo.core.audio.WavWriter
import com.vendo.core.datastore.SettingsDataStore
import com.vendo.core.network.ApiService
import com.vendo.core.network.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

/** A recording shorter than this can't meaningfully be an order - the
 * backend would draft an empty/near-empty request from it either way, so
 * it's caught here before spending a transcription call on it (spec:
 * "too-short recording"). */
private const val MIN_RECORDING_SECONDS = 1.0

/** Soft, client-side warning threshold - the backend's actual hard limit
 * (app/config.py's max_audio_seconds, 120s by default) is enforced
 * server-side regardless; this just gives the rep a chance to wrap up
 * before hitting it, since discovering the limit only after the fact
 * means re-recording (spec: "extremely long recording"). */
private const val LONG_RECORDING_WARNING_MS = 100_000L

/** How many times to poll GET /ingest/voice/{id} before telling the rep
 * this is taking unusually long. The worker retries a failed attempt up to
 * worker_max_attempts times (app/config.py, default 3) with its own poll
 * interval (worker_poll_seconds, default 2s) - a `status: "failed"` seen
 * mid-poll is very often *not* final, so this window is sized to comfortably
 * outlast a couple of those retry cycles rather than reporting failure on
 * the first transient sighting. */
private const val POLL_INTERVAL_MS = 1500L
private const val POLL_ATTEMPTS_BEFORE_TAKING_LONG = 40

enum class ProcessingStage { SAVING, UNDERSTANDING, MATCHING, PREPARING }

enum class SubmitMode { REVIEW_NOW, SAVE_FOR_LATER }

data class RecordUiState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val longRecordingWarning: Boolean = false,
    val hasAudio: Boolean = false,
    val isTooShort: Boolean = false,
    val isTranscribing: Boolean = false,
    val transcript: String = "",
    val hint: String? = null,
    val isPlayingPreview: Boolean = false,
    val isSubmitting: Boolean = false,
    /** 0 = not started, 1 = audio saved, 2 = understanding speech,
     * 3 = matching products. Once the backend reports a drafted request,
     * the screen navigates away immediately rather than showing a fourth
     * "done" stage. */
    val processingStageIndex: Int = 0,
    val isTakingLong: Boolean = false,
    val error: String? = null,
) {
    val elapsedLabel: String
        get() {
            val totalSec = (elapsedMs / 1000).toInt()
            return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
        }
}

sealed interface RecordEvent {
    data class OpenRequest(val requestId: Int) : RecordEvent
    /** The rep chose "Save for later" - submitted, deliberately not waiting. */
    data object SavedForLater : RecordEvent
    /** We gave up polling after [POLL_ATTEMPTS_BEFORE_TAKING_LONG] more
     * attempts past the first "taking long" prompt - the recording is still
     * safely uploaded and will keep processing on the server; the rep just
     * isn't watching it finish. */
    data object StillProcessing : RecordEvent
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
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecordEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<RecordEvent> = _events

    private var lastPcm: ShortArray? = null
    private var detectedLanguage: String? = null
    private var elapsedJob: Job? = null
    private var discardPending = false
    private var previewPlayer: MediaPlayer? = null
    private var pendingVoiceId: Int? = null

    fun startRecording() {
        if (_uiState.value.isRecording || _uiState.value.isSubmitting) return
        releasePreviewPlayer()
        _uiState.value = RecordUiState(isRecording = true)
        detectedLanguage = null
        discardPending = false

        elapsedJob?.cancel()
        elapsedJob = viewModelScope.launch {
            val start = System.currentTimeMillis()
            while (_uiState.value.isRecording) {
                val elapsed = System.currentTimeMillis() - start
                _uiState.value = _uiState.value.copy(
                    elapsedMs = elapsed,
                    longRecordingWarning = elapsed >= LONG_RECORDING_WARNING_MS,
                )
                delay(200)
            }
        }

        viewModelScope.launch {
            try {
                val pcm = audioRecorder.record()
                elapsedJob?.cancel()
                if (discardPending) {
                    discardPending = false
                    lastPcm = null
                    _uiState.value = RecordUiState()
                    return@launch
                }
                lastPcm = pcm
                val seconds = pcm.size.toDouble() / AUDIO_SAMPLE_RATE
                if (seconds < MIN_RECORDING_SECONDS) {
                    _uiState.value = _uiState.value.copy(
                        isRecording = false, hasAudio = false, isTooShort = true,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isRecording = false, hasAudio = true)
                    fetchPreview(pcm)
                }
            } catch (e: AudioUnavailableException) {
                elapsedJob?.cancel()
                _uiState.value = RecordUiState(error = e.message)
            } catch (e: Exception) {
                elapsedJob?.cancel()
                _uiState.value = RecordUiState(error = "Recording failed. Please try again.")
            }
        }
    }

    fun stopRecording() {
        audioRecorder.stop()
    }

    /** Stops and throws away the in-progress recording entirely - no
     * preview, no upload. Distinct from stopRecording(), which proceeds to
     * transcript preview. */
    fun cancelRecording() {
        discardPending = true
        audioRecorder.stop()
    }

    /** From the "too short" state: start over. */
    fun discardAndReset() {
        lastPcm = null
        detectedLanguage = null
        releasePreviewPlayer()
        _uiState.value = RecordUiState()
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
                hint = "We're checking the recording, but the preview text failed to load - " +
                    "you can still type the order manually below, or submit the audio as-is.",
            )
        }
    }

    /** Plays back the just-recorded audio locally (before it's ever
     * uploaded) so the rep can confirm what was actually captured - distinct
     * from the transcript text below it, never to be confused with it (spec:
     * "the user should never confuse original audio with edited transcript"). */
    fun togglePreviewPlayback() {
        val player = previewPlayer
        if (player != null) {
            if (player.isPlaying) {
                player.pause()
                _uiState.value = _uiState.value.copy(isPlayingPreview = false)
            } else {
                player.seekTo(0)
                player.start()
                _uiState.value = _uiState.value.copy(isPlayingPreview = true)
            }
            return
        }
        val pcm = lastPcm ?: return
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    File(appContext.cacheDir, "record_preview.wav").apply {
                        writeBytes(WavWriter.write(pcm))
                    }
                }
                val newPlayer = MediaPlayer()
                withContext(Dispatchers.IO) {
                    newPlayer.setDataSource(file.absolutePath)
                    newPlayer.prepare()
                }
                newPlayer.setOnCompletionListener {
                    _uiState.value = _uiState.value.copy(isPlayingPreview = false)
                }
                previewPlayer = newPlayer
                newPlayer.start()
                _uiState.value = _uiState.value.copy(isPlayingPreview = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(hint = "Couldn't play back the recording.")
            }
        }
    }

    private fun releasePreviewPlayer() {
        previewPlayer?.release()
        previewPlayer = null
    }

    /** REVIEW_NOW waits for the backend to draft the request, showing real
     * pipeline progress, then opens the Request screen. SAVE_FOR_LATER
     * submits and leaves the salesman on Record - the request is picked up
     * later from Log Query/queue. */
    fun submit(mode: SubmitMode) {
        val state = _uiState.value
        if (!state.hasAudio || state.transcript.isBlank()) {
            // Two-step clear-then-set: a StateFlow with an unchanged value
            // doesn't emit, so setting the identical error string twice in a
            // row (e.g. tapping twice with nothing recorded) would
            // otherwise silently fail to re-show the snackbar the second time.
            _uiState.value = state.copy(error = null)
            _uiState.value = _uiState.value.copy(error = "Nothing to submit yet")
            return
        }
        releasePreviewPlayer()
        _uiState.value = state.copy(
            isSubmitting = true, error = null, processingStageIndex = 1,
        )
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
                // trail.
                val phonePart = "salesman:$loginId".toRequestBody("text/plain".toMediaType())
                val transcriptPart = state.transcript.toRequestBody("text/plain".toMediaType())
                val languagePart = (detectedLanguage ?: "ar").toRequestBody("text/plain".toMediaType())
                val submitModePart = (if (mode == SubmitMode.REVIEW_NOW) "accept" else "draft")
                    .toRequestBody("text/plain".toMediaType())

                val ingestResult = api.ingestVoice(
                    phonePart, audioPart, transcriptPart, languagePart, submitModePart,
                )

                if (mode == SubmitMode.SAVE_FOR_LATER) {
                    resetAfterSubmit()
                    _events.tryEmit(RecordEvent.SavedForLater)
                    return@launch
                }

                pendingVoiceId = ingestResult.id
                pollUntilDrafted(ingestResult.id, POLL_ATTEMPTS_BEFORE_TAKING_LONG)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmitting = false,
                    processingStageIndex = 0,
                    error = e.toUserMessage("We couldn't submit this recording."),
                )
            }
        }
    }

    /** Called from the "this is taking longer than expected" state to keep
     * watching the same voice message rather than giving up on it. */
    fun keepWaiting() {
        val voiceId = pendingVoiceId ?: return
        _uiState.value = _uiState.value.copy(isTakingLong = false, isSubmitting = true)
        viewModelScope.launch { pollUntilDrafted(voiceId, POLL_ATTEMPTS_BEFORE_TAKING_LONG) }
    }

    /** From "taking longer than expected": stop watching. The recording is
     * already safely uploaded and keeps processing server-side regardless -
     * it'll show up in Log Query/the review queue once it's done. */
    fun stopWaiting() {
        pendingVoiceId = null
        resetAfterSubmit()
        _events.tryEmit(RecordEvent.StillProcessing)
    }

    private suspend fun pollUntilDrafted(voiceId: Int, attempts: Int) {
        var requestId: Int? = null
        for (attempt in 1..attempts) {
            delay(POLL_INTERVAL_MS)
            val status = try {
                api.voiceStatus(voiceId)
            } catch (_: Exception) {
                null // transient network hiccup while polling - just retry
            }
            if (status?.request_id != null) {
                requestId = status.request_id
                break
            }
            when (status?.status) {
                "transcribed", "classified" ->
                    _uiState.value = _uiState.value.copy(processingStageIndex = 3)
                "transcribing", "received" ->
                    _uiState.value = _uiState.value.copy(processingStageIndex = 2)
                // "failed" is very often mid-retry (the worker requeues it
                // up to worker_max_attempts times) rather than final - keep
                // polling instead of reporting failure on the first sighting.
                else -> Unit
            }
        }
        if (requestId != null) {
            pendingVoiceId = null
            resetAfterSubmit()
            _events.tryEmit(RecordEvent.OpenRequest(requestId))
        } else {
            _uiState.value = _uiState.value.copy(isSubmitting = false, isTakingLong = true)
        }
    }

    private fun resetAfterSubmit() {
        lastPcm = null
        detectedLanguage = null
        _uiState.value = RecordUiState()
    }

    override fun onCleared() {
        releasePreviewPlayer()
        super.onCleared()
    }
}
