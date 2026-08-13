package com.seif.stream.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var database: StreamDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun draftIsRecoverableUntilARealSaveCompletes() {
        val store = DraftStore(context)
        store.completeRealSave(1L)
        store.writeRaw("Crash-safe words", 2L)

        val recovered = store.recoverIfNewerThanLastSave(nowMillis = 3L)

        assertEquals("Crash-safe words", recovered?.text)
        assertEquals(2L, recovered?.timestamp)

        store.completeRealSave(System.currentTimeMillis())
        assertFalse(store.draftFileForTest().exists())
        assertNull(store.recoverIfNewerThanLastSave(System.currentTimeMillis()))
    }

    @Test
    fun importSkipsAnExistingTimestampAndMergesTheRest() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StreamDatabase::class.java).build()
        database = db
        val existing = Entry(timestamp = 100L, text = "Original", updatedAt = 100L)
        db.entryDao().upsert(existing)
        val repository = StreamRepository(
            entryDao = db.entryDao(),
            draftStore = DraftStore(context),
            nowMillis = { 999L },
        )
        val payload = EntryJsonCodec.encode(
            entries = listOf(
                Entry(timestamp = 100L, text = "Duplicate", updatedAt = 100L),
                Entry(timestamp = 200L, text = "Imported", updatedAt = 200L),
            ),
            exportedAtMillis = 300L,
        )

        val imported = repository.importJson(payload)
        val all = db.entryDao().getAll()

        assertEquals(1, imported)
        assertEquals(2, all.size)
        assertEquals("Imported", all.first { it.timestamp == 200L }.text)
        assertEquals("Original", all.first { it.timestamp == 100L }.text)
        assertTrue(all.zipWithNext().all { (first, second) -> first.timestamp > second.timestamp })
    }
}
