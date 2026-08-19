package com.vendo.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class IngestVoiceOut(val id: Int, val status: String)

@Serializable
data class VoiceStatusOut(
    val id: Int,
    val status: String,
    val error: String? = null,
    val transcript: String? = null,
    val language: String? = null,
    val request_id: Int? = null,
)

@Serializable
data class TranscribePreviewOut(val transcript: String, val language: String? = null)
