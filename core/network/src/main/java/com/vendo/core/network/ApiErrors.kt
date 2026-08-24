package com.vendo.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Turns any exception thrown by an ApiService call into copy that's safe to
 * show a user directly - never a raw HTTP code, a bare Retrofit message
 * ("HTTP 422 "), or a stack trace (spec: never expose raw HTTP
 * 500/Retrofit errors/JSON payloads). FastAPI's HTTPException(status,
 * detail) bodies are `{"detail": "..."}`; every `detail` string in the
 * backend's FastAPI route handlers is already written for a human reviewer,
 * so it's surfaced as-is rather than re-worded, with a per-status fallback
 * only for bodies that don't parse (network-level failures, unexpected
 * shapes).
 */
fun Throwable.toUserMessage(
    fallback: String = "Something went wrong. Please try again.",
): String = when (this) {
    is HttpException -> {
        val detail = errorDetail()
        when (code()) {
            // A 401 means different things on different endpoints - bad
            // credentials on /auth/login, a wrong current password on
            // /auth/change-password, or a genuinely expired session
            // everywhere else. The backend's own `detail` text already says
            // which one it is (see app/api/auth.py, app/api/deps.py) - only
            // fall back to a generic "session expired" when a body didn't
            // come through at all.
            401 -> detail ?: "Your session has expired. Please sign in again."
            403 -> detail ?: "You don't have permission to do that."
            404 -> detail ?: "That request no longer exists."
            409 -> detail ?: "This order was already handled."
            413 -> "This recording is too large to upload."
            422 -> detail ?: "Some information is missing before this can go through."
            in 500..599 -> "VeNdO's server ran into a problem. Please try again."
            else -> detail ?: fallback
        }
    }
    is SocketTimeoutException -> "VeNdO is taking too long to respond. Please try again."
    is IOException -> "We couldn't connect to VeNdO. Check your connection and try again."
    else -> message?.takeIf { it.isNotBlank() && !it.startsWith("HTTP ") } ?: fallback
}

/** True for a connectivity-shaped failure (offline, DNS, timeout) as opposed
 * to a request the server actively rejected - lets a caller distinguish
 * "your recording/edits are still safe, we'll retry" from "this specific
 * request was refused". */
fun Throwable.isConnectivityFailure(): Boolean = this is IOException

private fun HttpException.errorDetail(): String? = try {
    val body = response()?.errorBody()?.string()
    if (body.isNullOrBlank()) {
        null
    } else {
        (Json.parseToJsonElement(body) as? JsonObject)
            ?.get("detail")?.jsonPrimitive?.contentOrNull
    }
} catch (_: Exception) {
    null
}
