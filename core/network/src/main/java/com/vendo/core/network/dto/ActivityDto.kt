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

fun ActivityLogOut.logQueryLine(): String =
    "${logQueryVerb()}, ${cust_nb ?: "?"}, ${order_nb ?: "?"}"
