package com.revibe.update

import kotlinx.serialization.json.Json

object UpdateManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun parse(raw: String): UpdateManifest = try {
        json.decodeFromString(UpdateManifest.serializer(), raw)
    } catch (error: Exception) {
        throw UpdateException("The update manifest is not valid JSON.", error)
    }
}
