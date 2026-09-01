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
data class CustomerCandidateOut(
    val cust_nb: String,
    val customer_name: String,
    val score: Double,
)

@Serializable
data class CustomerCacheOut(
    val cust_nb: String,
    val customer_name: String,
)

/** Full single-customer detail, including the owning salesman - backs the
 * admin app's Customers screen (backend: GET /customers/{cust_nb}). */
@Serializable
data class CustomerDetailOut(
    val cust_nb: String,
    val customer_name: String,
    val email: String? = null,
    val telephone: String? = null,
    val city: String? = null,
    val address1: String? = null,
    val salesman_id: String? = null,
)

/** Admin-only customer reassignment (backend: PATCH /customers/{cust_nb}/salesman).
 * salesman_id = null clears the assignment. */
@Serializable
data class AssignSalesmanIn(val salesman_id: String? = null)

@Serializable
data class ItemCacheOut(
    val item_nb: String,
    val item_desc: String,
    val category: String,
)

@Serializable
data class RecentOrderLineOut(
    val item_nb: String? = null,
    val item_desc: String,
    val qty: String,
    val uom: String? = null,
    val is_free: Boolean = false,
)

@Serializable
data class RecentOrderOut(
    val order_nb: String,
    val order_type: String,
    val cust_nb: String,
    val customer_name: String? = null,
    val lines: List<RecentOrderLineOut> = emptyList(),
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
    // QRA preview (backend's preview_qra) - what this line's price/item
    // WOULD become at commit time under the customer's active QRA
    // agreement, if any. item_nb/item_desc above still show what the
    // salesman actually said/ordered - never mutated by this preview.
    val qra_unit_price: String? = null,
    val qra_is_free: Boolean = false,
    val qra_substituted_item_nb: String? = null,
    val qra_substituted_item_desc: String? = null,
)

/** A free bonus line QRA would add at commit time - has no corresponding
 * LineOut yet, so it can't live on one. */
@Serializable
data class QraBonusLineOut(
    val item_nb: String,
    val item_desc: String,
    val qty: String,
    val uom: String? = null,
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
    val qra_bonus_lines: List<QraBonusLineOut> = emptyList(),
)

@Serializable
data class LineEditIn(
    val line_nb: Int,
    val item_nb: String? = null,
    val item_desc: String? = null,
    val qty: String? = null,
    val uom: String? = null,
)

@Serializable
data class AcceptIn(
    val order_type: String,
    val lines: List<LineEditIn>,
    val removed_line_nbs: List<Int> = emptyList(),
    val note: String? = null,
    val cust_nb: String? = null,
    val target_order_nb: String? = null,
)

@Serializable
data class AcceptOut(val order_nb: String, val order_type: String)

@Serializable
data class RejectIn(val reason: String, val note: String? = null)

@Serializable
data class OkOut(val ok: Boolean)
