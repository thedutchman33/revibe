package com.revibe.platform

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

private val addedDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

actual fun formatAddedDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(addedDateFormatter)

actual fun isoToMillis(value: String): Long? =
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(value).toEpochMilli() }
        .getOrNull()

actual fun millisToIso(value: Long): String = Instant.ofEpochMilli(value).toString()
