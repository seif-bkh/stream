package com.seif.stream.ui.log

import com.seif.stream.data.Entry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogProjectionTest {
    private val today = LocalDate.of(2026, 8, 13)

    @Test
    fun entriesAreReverseChronologicalAndGroupedByDay() {
        val newer = entry("2026-08-13T15:00:00", "Newer")
        val olderToday = entry("2026-08-13T09:00:00", "Morning")
        val yesterday = entry("2026-08-12T20:00:00", "Yesterday")

        val items = buildLogItems(
            entries = listOf(olderToday, yesterday, newer),
            contentQuery = "",
            today = today,
            zoneId = ZoneOffset.UTC,
        )

        assertEquals("Today", (items[0] as LogListItem.DayDivider).label)
        assertEquals(newer, (items[1] as LogListItem.EntryRow).entry)
        assertEquals(olderToday, (items[2] as LogListItem.EntryRow).entry)
        assertEquals("Yesterday", (items[3] as LogListItem.DayDivider).label)
        assertEquals(yesterday, (items[4] as LogListItem.EntryRow).entry)
    }

    @Test
    fun contentSearchIsCaseInsensitiveSubstringMatch() {
        val matching = entry("2026-08-13T15:00:00", "Buy Oat Milk")
        val other = entry("2026-08-13T14:00:00", "Call Sam")

        val items = buildLogItems(
            entries = listOf(matching, other),
            contentQuery = "oat",
            today = today,
            zoneId = ZoneOffset.UTC,
        )

        val rows = items.filterIsInstance<LogListItem.EntryRow>()
        assertEquals(listOf(matching), rows.map { it.entry })
    }

    @Test
    fun dateJumpTargetsTheDividerNotAnEntry() {
        val targetDate = today.minusDays(2)
        val items = buildLogItems(
            entries = listOf(
                entry("2026-08-13T15:00:00", "Today"),
                entry("2026-08-11T10:00:00", "Target"),
            ),
            contentQuery = "",
            today = today,
            zoneId = ZoneOffset.UTC,
        )

        val index = dividerIndexForDate(items, targetDate)
        assertTrue(index >= 0)
        assertEquals(targetDate, (items[index] as LogListItem.DayDivider).date)
    }

    private fun entry(localDateTime: String, text: String): Entry {
        val timestamp = LocalDateTime.parse(localDateTime).toInstant(ZoneOffset.UTC).toEpochMilli()
        return Entry(timestamp = timestamp, text = text, updatedAt = timestamp)
    }
}
