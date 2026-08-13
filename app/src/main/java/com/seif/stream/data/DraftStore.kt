package com.seif.stream.data

import android.content.Context
import java.io.File

private const val DRAFT_FILE_NAME = "draft_buffer.txt"
private const val PREFS_NAME = "stream_persistence"
private const val KEY_FIRST_KEYSTROKE_MILLIS = "first_keystroke_millis"
private const val KEY_LAST_REAL_SAVE_TIMESTAMP = "last_real_save_timestamp"

data class RecoveredDraft(
    val text: String,
    val timestamp: Long,
)

/**
 * The deliberately small first tier of persistence. The draft file contains only the raw text.
 * Its first-keystroke timestamp lives separately in preferences so the file never needs parsing.
 */
class DraftStore(context: Context) {
    private val appContext = context.applicationContext
    private val draftFile = File(appContext.filesDir, DRAFT_FILE_NAME)
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun writeRaw(text: String, firstKeystrokeMillis: Long) {
        draftFile.writeText(text, Charsets.UTF_8)
        preferences.edit()
            .putLong(KEY_FIRST_KEYSTROKE_MILLIS, firstKeystrokeMillis)
            .commit()
    }

    fun recoverIfNewerThanLastSave(nowMillis: Long): RecoveredDraft? {
        if (!draftFile.exists()) return null

        val text = runCatching { draftFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        if (text.isEmpty()) {
            draftFile.delete()
            preferences.edit().remove(KEY_FIRST_KEYSTROKE_MILLIS).commit()
            return null
        }

        val lastRealSave = preferences.getLong(KEY_LAST_REAL_SAVE_TIMESTAMP, 0L)
        val fileModifiedAt = draftFile.lastModified()
        val firstKeystroke = preferences.getLong(KEY_FIRST_KEYSTROKE_MILLIS, 0L)

        // Equality is treated as recoverable: preserving a possible keystroke is safer than
        // dropping it on file systems whose timestamp resolution is coarser than a millisecond.
        val couldPostdateSave = lastRealSave == 0L ||
            fileModifiedAt >= lastRealSave ||
            firstKeystroke >= lastRealSave

        if (!couldPostdateSave) {
            // A completed real save is the only reason a non-empty draft is discarded.
            draftFile.delete()
            preferences.edit().remove(KEY_FIRST_KEYSTROKE_MILLIS).commit()
            return null
        }

        val recoveredTimestamp = firstKeystroke
            .takeIf { it > 0L }
            ?: fileModifiedAt.takeIf { it > 0L }
            ?: nowMillis

        return RecoveredDraft(text = text, timestamp = recoveredTimestamp)
    }

    /** Called only after the database upsert has completed successfully. */
    fun completeRealSave(savedAtMillis: Long) {
        // Persist the marker first. If the process dies before deletion, the old file is
        // recognized as already committed on the next launch.
        preferences.edit()
            .putLong(KEY_LAST_REAL_SAVE_TIMESTAMP, savedAtMillis)
            .remove(KEY_FIRST_KEYSTROKE_MILLIS)
            .commit()
        draftFile.delete()
    }

    internal fun draftFileForTest(): File = draftFile
}
