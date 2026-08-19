package com.vendo.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class TranscriptSegment(val start: Double, val end: Double, val text: String, val avg_logprob: Double)

@Serializable
data class CandidateOut(
    val item_nb: String,
    val item_desc: String,
    val category: String,
    val score: Double,
    val method: String? = null,
    val attribute_conflict: Boolean = false,
)

@Serializable
data class LineOut(
    val line_nb: Int,
    val raw_text: String,
    val raw_lang: String? = null,
    val item_nb: String? = null,
    val item_desc: String? = null,
    // Decimal on the backend - always a JSON string (e.g. "2.000").
    val qty: String? = null,
    val uom: String? = null,
    val match_confidence: Double? = null,
    val match_method: String? = null,
    val change: String? = null,
    val category: String? = null,
    val candidates: List<CandidateOut> = emptyList(),
    val line_flags: List<String> = emptyList(),
    val resolution_meta: Map<String, JsonElement> = emptyMap(),
    val attributes: Map<String, JsonElement> = emptyMap(),
    val qualifiers: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class RequestDetail(
    val id: Int,
    val status: String,
    val intents: List<String> = emptyList(),
    val primary_intent: String,
    val flags: List<String> = emptyList(),
    val cust_nb: String? = null,
    val customer_name: String? = null,
    val phone_e164: String? = null,
    val transcript: String? = null,
    val transcript_conf: Double? = null,
    val language: String? = null,
    val languages: List<String> = emptyList(),
    val duration_sec: String? = null,
    val audio_url: String,
    val segments: List<TranscriptSegment> = emptyList(),
    val target_order_nb: String? = null,
    val assigned_to: String? = null,
    val lines: List<LineOut> = emptyList(),
)

@Serializable
data class LineEditIn(
    val line_nb: Int,
    val item_nb: String? = null,
    val item_desc: String? = null,
    val qty: String? = null,
    val uom: String? = null,
    val remember_alias: Boolean = false,
)

@Serializable
data class AcceptIn(
    val order_type: String,
    val lines: List<LineEditIn>,
    val removed_line_nbs: List<Int> = emptyList(),
    val note: String? = null,
)

@Serializable
data class AcceptOut(val order_nb: String, val order_type: String)

@Serializable
data class RejectIn(val reason: String, val note: String? = null)

@Serializable
data class CallbackIn(val note: String? = null)

@Serializable
data class OkOut(val ok: Boolean)
