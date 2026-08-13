package com.seif.stream.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val captureFormatter = DateTimeFormatter.ofPattern("EEE MMM dd · HH:mm:ss", Locale.ENGLISH)
private val rowTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val dividerFormatter = DateTimeFormatter.ofPattern("EEE MMM dd", Locale.ENGLISH)

fun formatCaptureTimestamp(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(zoneId)
        .format(captureFormatter)
        .uppercase(Locale.ENGLISH)

fun formatEntryTime(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(timestamp).atZone(zoneId).format(rowTimeFormatter)

fun entryLocalDate(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()

fun formatDayDivider(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(dividerFormatter)
}

fun parseIsoDate(value: String): LocalDate? = try {
    LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
} catch (_: DateTimeParseException) {
    null
}
