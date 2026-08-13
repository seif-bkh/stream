package com.seif.stream.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object EntryJsonCodec {
    private const val FORMAT = "stream"
    private const val VERSION = 1

    fun encode(entries: List<Entry>, exportedAtMillis: Long): String {
        val payload = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", Instant.ofEpochMilli(exportedAtMillis).toString())

        val encodedEntries = JSONArray()
        entries.forEach { entry ->
            encodedEntries.put(
                JSONObject()
                    .put("timestamp", entry.timestamp)
                    .put("text", entry.text),
            )
        }
        payload.put("entries", encodedEntries)
        return payload.toString(2)
    }

    fun decode(raw: String, importedAtMillis: Long): List<Entry> {
        val payload = JSONObject(raw)
        require(payload.optString("format") == FORMAT) { "Not a Stream export" }
        require(payload.optInt("version", -1) == VERSION) { "Unsupported export version" }

        val encodedEntries = payload.getJSONArray("entries")
        return buildList(encodedEntries.length()) {
            for (index in 0 until encodedEntries.length()) {
                val encodedEntry = encodedEntries.getJSONObject(index)
                val timestamp = encodedEntry.getLong("timestamp")
                require(timestamp > 0L) { "Invalid entry timestamp" }
                add(
                    Entry(
                        timestamp = timestamp,
                        text = encodedEntry.getString("text"),
                        updatedAt = importedAtMillis,
                    ),
                )
            }
        }
    }
}
