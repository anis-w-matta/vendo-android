// Adapted from whisper.cpp's examples/whisper.android LibWhisper.kt. The
// WhisperLib object's package/class name is load-bearing: jni.c's exported
// symbols are named Java_com_vendo_whisper_WhisperLib_00024Companion_* and
// JNI resolves native methods by that exact mangled name.
package com.vendo.whisper

import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executors

private const val LOG_TAG = "LibWhisper"

class WhisperContext private constructor(private var ptr: Long) {
    // whisper.cpp's C++ context must only be touched from one thread at a
    // time - this dedicated single-thread dispatcher enforces that.
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
    )

    /** language: an ISO 639-1 code ("ar", "en", ...) or "auto" to let
     * whisper.cpp detect it per utterance - this app's speech is
     * Arabizi/Arabic-English mixed, so "auto" is the default. */
    suspend fun transcribeData(data: FloatArray, language: String = "auto"): TranscribeResult =
        withContext(scope.coroutineContext) {
            require(ptr != 0L)
            val numThreads = WhisperCpuConfig.preferredThreadCount
            Log.d(LOG_TAG, "Selecting $numThreads threads")
            WhisperLib.fullTranscribe(ptr, numThreads, data, language)
            val textCount = WhisperLib.getTextSegmentCount(ptr)
            val text = buildString {
                for (i in 0 until textCount) {
                    append(WhisperLib.getTextSegment(ptr, i))
                }
            }.trim()
            val detectedLanguage = WhisperLib.getDetectedLanguage(ptr).ifBlank { null }
            TranscribeResult(text = text, detectedLanguage = detectedLanguage)
        }

    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0
        }
    }

    protected fun finalize() {
        runBlocking { release() }
    }

    companion object {
        fun createContextFromFile(filePath: String): WhisperContext {
            val ptr = WhisperLib.initContext(filePath)
            if (ptr == 0L) {
                throw RuntimeException("Couldn't create whisper context from $filePath")
            }
            return WhisperContext(ptr)
        }

        fun createContextFromInputStream(stream: InputStream): WhisperContext {
            val ptr = WhisperLib.initContextFromInputStream(stream)
            if (ptr == 0L) {
                throw RuntimeException("Couldn't create whisper context from input stream")
            }
            return WhisperContext(ptr)
        }

        fun createContextFromAsset(assetManager: AssetManager, assetPath: String): WhisperContext {
            val ptr = WhisperLib.initContextFromAsset(assetManager, assetPath)
            if (ptr == 0L) {
                throw RuntimeException("Couldn't create whisper context from asset $assetPath")
            }
            return WhisperContext(ptr)
        }

        fun getSystemInfo(): String = WhisperLib.getSystemInfo()
    }
}

data class TranscribeResult(val text: String, val detectedLanguage: String?)

private class WhisperLib {
    companion object {
        init {
            Log.d(LOG_TAG, "Primary ABI: ${Build.SUPPORTED_ABIS[0]}")
            var loadVfpv4 = false
            var loadV8fp16 = false
            if (isArmEabiV7a()) {
                cpuInfo()?.let {
                    Log.d(LOG_TAG, "CPU info: $it")
                    if (it.contains("vfpv4")) loadVfpv4 = true
                }
            } else if (isArmEabiV8a()) {
                cpuInfo()?.let {
                    Log.d(LOG_TAG, "CPU info: $it")
                    if (it.contains("fphp")) loadV8fp16 = true
                }
            }

            when {
                loadVfpv4 -> {
                    Log.d(LOG_TAG, "Loading libwhisper_vfpv4.so")
                    System.loadLibrary("whisper_vfpv4")
                }
                loadV8fp16 -> {
                    Log.d(LOG_TAG, "Loading libwhisper_v8fp16_va.so")
                    System.loadLibrary("whisper_v8fp16_va")
                }
                else -> {
                    Log.d(LOG_TAG, "Loading libwhisper.so")
                    System.loadLibrary("whisper")
                }
            }
        }

        // JNI methods - implemented in whisper/src/main/cpp/jni.c
        external fun initContextFromInputStream(inputStream: InputStream): Long
        external fun initContextFromAsset(assetManager: AssetManager, assetPath: String): Long
        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray, language: String)
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getDetectedLanguage(contextPtr: Long): String
        external fun getSystemInfo(): String
    }
}

private fun isArmEabiV7a(): Boolean = Build.SUPPORTED_ABIS[0] == "armeabi-v7a"
private fun isArmEabiV8a(): Boolean = Build.SUPPORTED_ABIS[0] == "arm64-v8a"

private fun cpuInfo(): String? = try {
    File("/proc/cpuinfo").inputStream().bufferedReader().use { it.readText() }
} catch (e: Exception) {
    Log.w(LOG_TAG, "Couldn't read /proc/cpuinfo", e)
    null
}
