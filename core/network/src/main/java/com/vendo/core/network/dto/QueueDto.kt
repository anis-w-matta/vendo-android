package com.vendo.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class QueueRow(
    val id: Int,
    val created_at: String,
    val customer_name: String? = null,
    val cust_nb: String? = null,
    val primary_intent: String,
    val line_count: Int,
    val flags: List<String> = emptyList(),
    val status: String,
    // Decimal on the backend - always serialized as a JSON string (e.g.
    // "4.20"), never a bare number. Parse with toBigDecimalOrNull() if
    // arithmetic is needed; for display it can be shown as-is.
    val duration_sec: String? = null,
    val languages: List<String> = emptyList(),
)
