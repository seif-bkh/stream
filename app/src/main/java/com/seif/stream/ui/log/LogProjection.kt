package com.seif.stream.ui.log

import com.seif.stream.data.Entry
import com.seif.stream.util.entryLocalDate
import com.seif.stream.util.formatDayDivider
import java.time.LocalDate
import java.time.ZoneId

sealed interface LogListItem {
    data class DayDivider(
        val date: LocalDate,
        val label: String,
    ) : LogListItem

    data class EntryRow(val entry: Entry) : LogListItem
}

fun buildLogItems(
    entries: List<Entry>,
    contentQuery: String,
    today: LocalDate = LocalDate.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<LogListItem> {
    val visibleEntries = entries
        .asSequence()
        .filter { entry -> contentQuery.isBlank() || entry.text.contains(contentQuery, ignoreCase = true) }
        .sortedByDescending(Entry::timestamp)
        .toList()

    return buildList {
        var previousDate: LocalDate? = null
        visibleEntries.forEach { entry ->
            val date = entryLocalDate(entry.timestamp, zoneId)
            if (date != previousDate) {
                add(LogListItem.DayDivider(date, formatDayDivider(date, today)))
                previousDate = date
            }
            add(LogListItem.EntryRow(entry))
        }
    }
}

fun dividerIndexForDate(items: List<LogListItem>, date: LocalDate): Int =
    items.indexOfFirst { item -> item is LogListItem.DayDivider && item.date == date }
