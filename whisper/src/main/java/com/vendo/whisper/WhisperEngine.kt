package com.vendo.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class WhisperResult(val text: String, val language: String?)

/**
 * On-device speech-to-text via whisper.cpp (small multilingual GGML model).
 * See the RECORD screen: capture audio with AudioRecord at 16kHz mono PCM
 * (whisper.cpp's required input format), transcribe locally, submit the
 * resulting text to POST /ingest/voice's `transcript` field instead of
 * relying on the backend's Gemini call.
 */
interface WhisperEngine {
    val isModelLoaded: Boolean

    suspend fun loadModel(modelFile: File)

    suspend fun transcribe(pcm16Mono16k: ShortArray): WhisperResult
}

/** JNI-backed implementation - see LibWhisper.kt (Kotlin bridge) and
 * whisper/src/main/cpp/jni.c (native side, vendoring whisper.cpp under
 * whisper/src/main/cpp/whisper.cpp/). */
@Singleton
class WhisperCppEngine @Inject constructor() : WhisperEngine {
    @Volatile
    private var context: WhisperContext? = null

    override val isModelLoaded: Boolean
        get() = context != null

    override suspend fun loadModel(modelFile: File) = withContext(Dispatchers.IO) {
        context?.release()
        context = WhisperContext.createContextFromFile(modelFile.absolutePath)
    }

    override suspend fun transcribe(pcm16Mono16k: ShortArray): WhisperResult {
        val ctx = context
            ?: throw IllegalStateException("WhisperEngine.loadModel() must be called before transcribe()")
        val floatSamples = FloatArray(pcm16Mono16k.size) { i ->
            // whisper.cpp expects 32-bit float PCM in [-1, 1], not 16-bit
            // shorts - see whisper_full's audio_data contract.
            pcm16Mono16k[i] / 32768.0f
        }
        val result = ctx.transcribeData(floatSamples, language = "auto")
        return WhisperResult(text = result.text, language = result.detectedLanguage)
    }
}
