package com.vendo.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class ActivityLogOut(
    val id: Int,
    val ts: String,
    val event_type: String,
    val level: String,
    val voice_message_id: Int? = null,
    val request_id: Int? = null,
    val cust_nb: String? = null,
    val order_nb: String? = null,
    val message: String,
    val details: Map<String, JsonElement> = emptyMap(),
)

/** The three event types LOG QUERY surfaces - everything else logged by the
 * backend (item_classified, reorder_resolved, unrecognized_command,
 * audio_too_long, error, update_rejected, ...) is internal/diagnostic, not
 * a salesman-facing order decision, and stays out of this screen. */
val LOG_QUERY_EVENT_TYPES = listOf("order_committed", "request_rejected", "voice_received")

/** Maps PendingRequest.primary_intent (carried in details.primary_intent
 * since the commit.py fix) to the LOG QUERY screen's display verb. See the
 * backend plan's "Log Query data source" note - only order_committed rows
 * fetched with primary_intent are guaranteed to have this key; historical
 * rows committed before that fix fall back to "ORDER". */
fun ActivityLogOut.logQueryVerb(): String {
    val intent = details["primary_intent"]?.jsonPrimitive?.contentOrNull
    return when (intent) {
        "add_order" -> "ADD ORDER"
        "repeat_order", "repeat_order_adjusted" -> "REORDER"
        "return_order" -> "RETURN ORDER"
        else -> "ORDER"
    }
}

/** voice_received rows cover both "Accept" and "Draft" submissions
 * (RecordViewModel.submit) - only the draft ones are worth a LOG QUERY
 * line of their own, since an accepted one gets its own order_committed
 * (or request_rejected) row once decided. */
fun ActivityLogOut.isDraftSubmission(): Boolean =
    event_type == "voice_received" &&
        details["submit_mode"]?.jsonPrimitive?.contentOrNull == "draft"

fun ActivityLogOut.logQueryLine(): String = when (event_type) {
    "order_committed" -> "${logQueryVerb()}, ${cust_nb ?: "?"}, ${order_nb ?: "?"}"
    "request_rejected" -> {
        val reason = details["reason"]?.jsonPrimitive?.contentOrNull ?: "-"
        "REJECTED (${logQueryVerb()}), ${cust_nb ?: "?"}, $reason"
    }
    "voice_received" -> "DRAFT, voice #${voice_message_id ?: "?"}, saved for later"
    else -> "${event_type.uppercase()}, ${cust_nb ?: "?"}, ${order_nb ?: "?"}"
}

/** One LOG QUERY row: its display text, and the request it opens when
 * tapped - null for rows with no request yet (a draft voice submission
 * hasn't been turned into a PendingRequest at the point it's logged). */
data class LogQueryLine(val text: String, val requestId: Int?)

fun ActivityLogOut.toLogQueryLine(): LogQueryLine = LogQueryLine(logQueryLine(), request_id)
