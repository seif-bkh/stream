package com.seif.stream.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class StreamRepository(
    private val entryDao: EntryDao,
    private val draftStore: DraftStore,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : CapturePersistence {
    private val persistenceMutex = Mutex()

    val entries: Flow<List<Entry>> = entryDao.observeActive()
    val trashedEntries: Flow<List<Entry>> = entryDao.observeTrashed()

    override fun recoverDraft(): RecoveredDraft? =
        draftStore.recoverIfNewerThanLastSave(nowMillis())

    override suspend fun writeDraft(text: String, timestamp: Long) = withContext(Dispatchers.IO) {
        persistenceMutex.withLock {
            draftStore.writeRaw(text, timestamp)
        }
    }

    override suspend fun discardBlankDraft(text: String, timestamp: Long) =
        withContext(Dispatchers.IO) {
            require(text.isBlank()) { "Only blank scratch state may be discarded" }
            persistenceMutex.withLock {
                // Flush the exact current snapshot first so a previous non-blank debounce cannot
                // survive a lifecycle stop after the user has cleared the editor.
                draftStore.writeRaw(text, timestamp)
                draftStore.discardBlank()
            }
        }

    /**
     * Flushes the exact editor snapshot to the draft tier, then upserts it into Room. The draft
     * is removed only after Room confirms the write.
     */
    override suspend fun commitCapture(text: String, timestamp: Long) = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "Blank captures cannot be committed" }
        persistenceMutex.withLock {
            draftStore.writeRaw(text, timestamp)
            entryDao.upsert(
                Entry(
                    timestamp = timestamp,
                    text = text,
                    updatedAt = nowMillis(),
                    trashedAt = null,
                ),
            )
            draftStore.completeRealSave(nowMillis())
        }
    }

    suspend fun moveToTrash(timestamp: Long): Boolean = withContext(Dispatchers.IO) {
        persistenceMutex.withLock {
            entryDao.moveToTrash(timestamp, nowMillis()) > 0
        }
    }

    suspend fun restoreFromTrash(timestamp: Long): Boolean = withContext(Dispatchers.IO) {
        persistenceMutex.withLock {
            entryDao.restoreFromTrash(timestamp) > 0
        }
    }

    suspend fun deletePermanently(timestamp: Long): Boolean = withContext(Dispatchers.IO) {
        persistenceMutex.withLock {
            entryDao.deletePermanently(timestamp) > 0
        }
    }

    suspend fun emptyTrash(): Int = withContext(Dispatchers.IO) {
        persistenceMutex.withLock {
            entryDao.emptyTrash()
        }
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        EntryJsonCodec.encode(entryDao.getAll(), nowMillis())
    }

    suspend fun importJson(raw: String): Int = withContext(Dispatchers.IO) {
        val importedAt = nowMillis()
        val decoded = EntryJsonCodec.decode(raw, importedAt)
            .filterNot { entry -> entry.text.isBlank() }
        if (decoded.isEmpty()) return@withContext 0
        entryDao.insertIgnoringTimestamps(decoded).count { rowId -> rowId != -1L }
    }
}
