package com.vendo.app.request

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.vendo.core.datastore.SettingsDataStore
import com.vendo.core.network.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the MediaPlayer instance and playback-progress ticker backing the
 * request review screen's audio evidence player - split out of
 * RequestViewModel so its line-editing/accept/reject logic isn't tangled
 * with MediaPlayer lifecycle plumbing. Reads/writes the ViewModel's shared
 * RequestUiState through `updateState` (a transform callback) rather than
 * owning a separate state flow, so RequestUiState's shape - and every
 * screen that reads it - is unchanged by this split.
 */
class RequestAudioPlayer(
    private val appContext: Context,
    private val settings: SettingsDataStore,
    private val scope: CoroutineScope,
    private val updateState: ((RequestUiState) -> RequestUiState) -> Unit,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    /** Streams the recording straight off the backend's /audio/{id} - no
     * bearer token needed there, but the shared-secret API key is passed the
     * same way AuthInterceptor adds it to Retrofit calls, since MediaPlayer's
     * networking doesn't go through OkHttp. `isLoadingAudio` is passed in
     * (rather than read from state here) since this class doesn't hold the
     * ViewModel's state itself - see updateState. */
    fun toggle(audioPath: String, isLoadingAudio: Boolean) {
        val player = mediaPlayer
        if (player != null && !isLoadingAudio) {
            if (player.isPlaying) {
                player.pause()
                stopProgressTicker()
                updateState { s -> s.copy(isPlayingAudio = false) }
            } else {
                player.start()
                startProgressTicker()
                updateState { s -> s.copy(isPlayingAudio = true) }
            }
            return
        }
        if (player != null) return
        updateState { s -> s.copy(isLoadingAudio = true, error = null) }
        scope.launch {
            try {
                val url = resolveAudioUrl(audioPath)
                val headers = if (BuildConfig.API_KEY.isNotBlank()) {
                    mapOf("X-Api-Key" to BuildConfig.API_KEY)
                } else {
                    emptyMap()
                }
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(appContext, Uri.parse(url), headers)
                    setOnPreparedListener { mp ->
                        mp.start()
                        updateState { s ->
                            s.copy(
                                isLoadingAudio = false, isPlayingAudio = true,
                                playbackDurationMs = mp.duration.coerceAtLeast(0),
                            )
                        }
                        startProgressTicker()
                    }
                    setOnCompletionListener {
                        stopProgressTicker()
                        updateState { s -> s.copy(isPlayingAudio = false, playbackPositionMs = 0) }
                    }
                    setOnErrorListener { _, _, _ ->
                        stopProgressTicker()
                        updateState { s ->
                            s.copy(
                                isLoadingAudio = false,
                                isPlayingAudio = false,
                                error = "Couldn't play the recording.",
                            )
                        }
                        true
                    }
                    prepareAsync()
                }
            } catch (e: Exception) {
                updateState { s ->
                    s.copy(isLoadingAudio = false, error = "Couldn't play the recording.")
                }
            }
        }
    }

    fun seekTo(ms: Int) {
        mediaPlayer?.seekTo(ms)
        updateState { s -> s.copy(playbackPositionMs = ms) }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val player = mediaPlayer ?: break
                updateState { s -> s.copy(playbackPositionMs = player.currentPosition) }
                delay(200)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private suspend fun resolveAudioUrl(path: String): String {
        val stored = settings.currentServerUrl()?.takeIf { it.isNotBlank() }
        val base = (stored ?: BuildConfig.BASE_URL).trimEnd('/')
        return base + path
    }

    /** Releases the MediaPlayer and stops the progress ticker - call from
     * the owning ViewModel's onCleared(). */
    fun release() {
        stopProgressTicker()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
