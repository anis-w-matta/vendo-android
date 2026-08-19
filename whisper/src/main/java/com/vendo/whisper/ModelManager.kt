package com.vendo.whisper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed interface ModelDownloadState {
    data class Progress(val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState
    data object VerifyingChecksum : ModelDownloadState
    data object Ready : ModelDownloadState
    data class Failed(val message: String) : ModelDownloadState
}

/**
 * Download-on-first-launch for the small multilingual GGML model (~460MB).
 * Not bundled in the APK - see the whisper.cpp integration plan (Milestone
 * 3): bundling would push the app well past Play's practical size
 * thresholds and force a 460MB re-download on every update.
 *
 * MODEL_URL points at ggerganov/whisper.cpp's official Hugging Face model
 * mirror (the same models/download-ggml-model.sh script whisper.cpp itself
 * ships uses this repo). MODEL_SHA256 is the LFS object's sha256 as
 * reported by the Hugging Face API (huggingface.co/api/models/ggerganov/
 * whisper.cpp/tree/main) at the time this was written - double-check it
 * against the file you actually receive before relying on it, since a
 * model update on the HF side would change both the file and this hash
 * together (this constant would then need updating to match).
 */
class ModelManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build(),
) {
    companion object {
        const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
        const val MODEL_SHA256 = "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b"
        const val MODEL_FILE_NAME = "ggml-small.bin"
    }

    fun modelFile(): File = File(File(context.filesDir, "models"), MODEL_FILE_NAME)

    fun isModelReady(): Boolean = modelFile().exists()

    fun download(): Flow<ModelDownloadState> = callbackFlow {
        val target = modelFile()
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.part")

        try {
            val request = Request.Builder().url(MODEL_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    trySend(ModelDownloadState.Failed("HTTP ${response.code}"))
                    close()
                    return@use
                }
                val body = response.body ?: run {
                    trySend(ModelDownloadState.Failed("empty response body"))
                    close()
                    return@use
                }
                val total = body.contentLength()
                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L

                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            trySend(ModelDownloadState.Progress(downloaded, total))
                        }
                    }
                }

                trySend(ModelDownloadState.VerifyingChecksum)
                val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                if (MODEL_SHA256.isNotBlank() && !actualHash.equals(MODEL_SHA256, ignoreCase = true)) {
                    tmp.delete()
                    trySend(ModelDownloadState.Failed("checksum mismatch"))
                    close()
                    return@use
                }

                tmp.renameTo(target)
                trySend(ModelDownloadState.Ready)
                close()
            }
        } catch (e: Exception) {
            tmp.delete()
            trySend(ModelDownloadState.Failed(e.message ?: "download failed"))
            close()
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)
}
