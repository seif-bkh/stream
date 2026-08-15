package com.seif.stream.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object EntryJsonCodec {
    private const val FORMAT = "stream"
    private const val CURRENT_VERSION = 2
    private const val FIRST_SUPPORTED_VERSION = 1

    fun encode(entries: List<Entry>, exportedAtMillis: Long): String {
        val payload = JSONObject()
            .put("format", FORMAT)
            .put("version", CURRENT_VERSION)
            .put("exportedAt", Instant.ofEpochMilli(exportedAtMillis).toString())

        val encodedEntries = JSONArray()
        entries.forEach { entry ->
            val encodedEntry = JSONObject()
                .put("timestamp", entry.timestamp)
                .put("text", entry.text)
            entry.trashedAt?.let { encodedEntry.put("trashedAt", it) }
            encodedEntries.put(encodedEntry)
        }
        payload.put("entries", encodedEntries)
        return payload.toString(2)
    }

    fun decode(raw: String, importedAtMillis: Long): List<Entry> {
        val payload = JSONObject(raw)
        require(payload.optString("format") == FORMAT) { "Not a Stream export" }
        val version = payload.optInt("version", -1)
        require(version in FIRST_SUPPORTED_VERSION..CURRENT_VERSION) {
            "Unsupported export version"
        }

        val encodedEntries = payload.getJSONArray("entries")
        return buildList(encodedEntries.length()) {
            for (index in 0 until encodedEntries.length()) {
                val encodedEntry = encodedEntries.getJSONObject(index)
                val timestamp = encodedEntry.getLong("timestamp")
                require(timestamp > 0L) { "Invalid entry timestamp" }
                val trashedAt = if (
                    version >= 2 &&
                    encodedEntry.has("trashedAt") &&
                    !encodedEntry.isNull("trashedAt")
                ) {
                    encodedEntry.getLong("trashedAt").also { value ->
                        require(value > 0L) { "Invalid trash timestamp" }
                    }
                } else {
                    null
                }
                add(
                    Entry(
                        timestamp = timestamp,
                        text = encodedEntry.getString("text"),
                        updatedAt = importedAtMillis,
                        trashedAt = trashedAt,
                    ),
                )
            }
        }
    }
}
