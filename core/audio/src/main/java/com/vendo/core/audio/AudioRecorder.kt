package com.vendo.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

const val AUDIO_SAMPLE_RATE = 16000

/** Thrown by [AudioRecorder.record] when the microphone couldn't be opened
 * at all - permission race, already claimed by another app, or no input
 * device present. Distinct from a mid-recording interruption (handled by
 * just stopping cleanly and returning whatever was captured, below) so the
 * caller can tell "never started" from "started, then cut short". */
class AudioUnavailableException(message: String) : Exception(message)

/** Consecutive failed AudioRecord.read() calls (e.g. the mic gets seized by
 * a phone call mid-recording) tolerated before giving up on this recording
 * rather than spinning the IO thread forever. */
private const val MAX_CONSECUTIVE_READ_ERRORS = 20

/** Captures 16kHz mono 16-bit PCM via AudioRecord - archived and uploaded
 * as the `audio` multipart field on POST /ingest/voice (transcription
 * itself happens server-side via Gemini, see RecordViewModel). */
class AudioRecorder @Inject constructor() {
    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false

    private val minBufferSize = AudioRecord.getMinBufferSize(
        AUDIO_SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(4096)

    /** Suspends until stop() is called (or the mic is lost mid-recording);
     * returns whatever PCM was captured before that point. Caller must hold
     * RECORD_AUDIO permission before calling this.
     *
     * @throws AudioUnavailableException if the microphone couldn't be
     * opened at all - e.g. another app already holds it. A previous version
     * of this code called startRecording() unconditionally, which can throw
     * IllegalStateException (crashing the app) on a device that failed to
     * initialize instead of surfacing a recoverable "mic unavailable" state.
     */
    @Suppress("MissingPermission")
    suspend fun record(): ShortArray = withContext(Dispatchers.IO) {
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw AudioUnavailableException(
                "The microphone couldn't be started - it may be in use by another app.")
        }
        audioRecord = record
        val output = ArrayList<Short>()
        val buffer = ShortArray(minBufferSize / 2)

        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw AudioUnavailableException(
                    "The microphone couldn't be started - it may be in use by another app.")
            }
            isRecording = true
            var consecutiveErrors = 0
            while (isRecording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    consecutiveErrors = 0
                    for (i in 0 until read) output.add(buffer[i])
                } else {
                    // A transient read failure (buffer underrun) is normal;
                    // a long run of them means the input device is gone
                    // (e.g. a phone call just took the mic) - stop instead
                    // of looping forever, keeping whatever was captured so
                    // far rather than losing the recording outright.
                    consecutiveErrors++
                    if (consecutiveErrors > MAX_CONSECUTIVE_READ_ERRORS) break
                }
            }
        } finally {
            try {
                record.stop()
            } catch (_: IllegalStateException) {
                // stop() throws if startRecording() never actually reached
                // RECORDSTATE_RECORDING - already surfaced above.
            }
            record.release()
            audioRecord = null
        }
        output.toShortArray()
    }

    fun stop() {
        isRecording = false
    }
}

object WavWriter {
    /** Wraps raw 16kHz mono 16-bit PCM in a minimal WAV header - a format
     * already in the backend's AUDIO_MIME_BY_EXT/ALLOWED_EXT list. */
    fun write(pcm: ShortArray, sampleRate: Int = AUDIO_SAMPLE_RATE): ByteArray {
        val dataSize = pcm.size * 2
        val byteRate = sampleRate * 2
        val out = ByteArrayOutputStream(44 + dataSize)

        fun writeString(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun writeIntLE(v: Int) {
            out.write(v and 0xff)
            out.write((v shr 8) and 0xff)
            out.write((v shr 16) and 0xff)
            out.write((v shr 24) and 0xff)
        }
        fun writeShortLE(v: Int) {
            out.write(v and 0xff)
            out.write((v shr 8) and 0xff)
        }

        writeString("RIFF")
        writeIntLE(36 + dataSize)
        writeString("WAVE")
        writeString("fmt ")
        writeIntLE(16)
        writeShortLE(1) // PCM
        writeShortLE(1) // mono
        writeIntLE(sampleRate)
        writeIntLE(byteRate)
        writeShortLE(2) // block align
        writeShortLE(16) // bits per sample
        writeString("data")
        writeIntLE(dataSize)
        for (sample in pcm) {
            writeShortLE(sample.toInt())
        }
        return out.toByteArray()
    }
}
