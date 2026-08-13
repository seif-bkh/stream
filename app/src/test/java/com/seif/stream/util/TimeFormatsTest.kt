package com.seif.stream.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeFormatsTest {
    @Test
    fun captureTimestampMatchesRequiredShape() {
        val timestamp = Instant.parse("2026-08-13T14:32:07Z").toEpochMilli()

        assertEquals(
            "THU AUG 13 · 14:32:07",
            formatCaptureTimestamp(timestamp, ZoneOffset.UTC),
        )
    }

    @Test
    fun dayLabelsUseTodayYesterdayThenDate() {
        val today = LocalDate.of(2026, 8, 13)

        assertEquals("Today", formatDayDivider(today, today))
        assertEquals("Yesterday", formatDayDivider(today.minusDays(1), today))
        assertEquals("Tue Aug 11", formatDayDivider(today.minusDays(2), today))
    }

    @Test
    fun dateParserOnlyAcceptsIsoDates() {
        assertEquals(LocalDate.of(2026, 8, 13), parseIsoDate("2026-08-13"))
        assertNull(parseIsoDate("Aug 13"))
        assertNull(parseIsoDate("2026-99-13"))
    }
}
