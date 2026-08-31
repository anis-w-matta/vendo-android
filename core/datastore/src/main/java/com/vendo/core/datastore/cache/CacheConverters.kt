package com.vendo.core.datastore.cache

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

class CacheConverters {
    @TypeConverter
    fun linesToJson(lines: List<CachedOrderLine>): String = json.encodeToString(lines)

    @TypeConverter
    fun linesFromJson(raw: String): List<CachedOrderLine> =
        json.decodeFromString(raw)

    @TypeConverter
    fun qraDetailsToJson(details: List<CachedQraDetail>): String = json.encodeToString(details)

    @TypeConverter
    fun qraDetailsFromJson(raw: String): List<CachedQraDetail> =
        json.decodeFromString(raw)
}
