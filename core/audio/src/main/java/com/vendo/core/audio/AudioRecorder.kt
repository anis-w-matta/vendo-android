package com.vendo.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

const val AUDIO_SAMPLE_RATE = 16000

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

    /** Suspends until stop() is called; returns the full captured PCM.
     * Caller must hold RECORD_AUDIO permission before calling this. */
    @Suppress("MissingPermission")
    suspend fun record(): ShortArray = withContext(Dispatchers.IO) {
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AUDIO_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize,
        )
        audioRecord = record
        val output = ArrayList<Short>()
        val buffer = ShortArray(minBufferSize / 2)

        try {
            record.startRecording()
            isRecording = true
            while (isRecording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    for (i in 0 until read) output.add(buffer[i])
                }
            }
        } finally {
            record.stop()
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
