package com.seif.stream.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
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
    private var databaseNameToDelete: String? = null

    @After
    fun tearDown() {
        database?.close()
        databaseNameToDelete?.let(context::deleteDatabase)
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
    fun whitespaceOnlyDraftIsNeverRecovered() {
        val store = DraftStore(context)
        store.completeRealSave(System.currentTimeMillis())
        store.writeRaw(" \n\t", System.currentTimeMillis())

        assertNull(store.recoverIfNewerThanLastSave(System.currentTimeMillis()))
        assertFalse(store.draftFileForTest().exists())
    }

    @Test
    fun lifecycleBlankDiscardReplacesAnOlderNonBlankScratchRevision() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StreamDatabase::class.java).build()
        database = db
        val store = DraftStore(context)
        store.completeRealSave(System.currentTimeMillis())
        store.writeRaw("Earlier draft revision", 10L)
        val repository = StreamRepository(
            entryDao = db.entryDao(),
            draftStore = store,
            nowMillis = { 20L },
        )

        repository.discardBlankDraft(" \n", timestamp = 10L)

        assertFalse(store.draftFileForTest().exists())
        assertTrue(db.entryDao().getAll().isEmpty())
    }

    @Test
    fun importSkipsAnExistingTimestampAndMergesTheRest() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StreamDatabase::class.java).build()
        database = db
        val existing = Entry(
            timestamp = 100L,
            text = "Original",
            updatedAt = 100L,
            trashedAt = 150L,
        )
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
                Entry(timestamp = 300L, text = " \n\t", updatedAt = 300L),
            ),
            exportedAtMillis = 300L,
        )

        val imported = repository.importJson(payload)
        val all = db.entryDao().getAll()

        assertEquals(1, imported)
        assertEquals(2, all.size)
        assertEquals("Imported", all.first { it.timestamp == 200L }.text)
        val unchangedExisting = all.first { it.timestamp == 100L }
        assertEquals("Original", unchangedExisting.text)
        assertEquals(150L, unchangedExisting.trashedAt)
        assertTrue(all.zipWithNext().all { (first, second) -> first.timestamp > second.timestamp })
    }

    @Test
    fun trashCanBeRestoredAndThenPermanentlyDeleted() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StreamDatabase::class.java).build()
        database = db
        val repository = StreamRepository(
            entryDao = db.entryDao(),
            draftStore = DraftStore(context),
            nowMillis = { 500L },
        )
        val entry = Entry(timestamp = 100L, text = "Keep until confirmed", updatedAt = 100L)
        db.entryDao().upsert(entry)

        assertTrue(repository.moveToTrash(entry.timestamp))
        assertTrue(repository.entries.first().isEmpty())
        assertEquals(entry.timestamp, repository.trashedEntries.first().single().timestamp)

        assertTrue(repository.restoreFromTrash(entry.timestamp))
        assertEquals(entry.timestamp, repository.entries.first().single().timestamp)
        assertTrue(repository.trashedEntries.first().isEmpty())

        assertTrue(repository.moveToTrash(entry.timestamp))
        assertTrue(repository.deletePermanently(entry.timestamp))
        assertTrue(db.entryDao().getAll().isEmpty())
    }

    @Test
    fun emptyTrashDeletesOnlyTrashedEntries() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StreamDatabase::class.java).build()
        database = db
        val repository = StreamRepository(
            entryDao = db.entryDao(),
            draftStore = DraftStore(context),
            nowMillis = { 500L },
        )
        db.entryDao().upsert(Entry(timestamp = 100L, text = "Active", updatedAt = 100L))
        db.entryDao().upsert(
            Entry(timestamp = 200L, text = "Trash one", updatedAt = 200L, trashedAt = 300L),
        )
        db.entryDao().upsert(
            Entry(timestamp = 201L, text = "Trash two", updatedAt = 201L, trashedAt = 301L),
        )

        assertEquals(2, repository.emptyTrash())

        val remaining = db.entryDao().getAll().single()
        assertEquals("Active", remaining.text)
        assertNull(remaining.trashedAt)
    }

    @Test
    fun repositoryRejectsBlankDatabaseCommits() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, StreamDatabase::class.java).build()
        database = db
        val repository = StreamRepository(
            entryDao = db.entryDao(),
            draftStore = DraftStore(context),
            nowMillis = { 500L },
        )

        val result = runCatching { repository.commitCapture(" \n\t", timestamp = 100L) }

        assertTrue(result.isFailure)
        assertTrue(db.entryDao().getAll().isEmpty())
    }

    @Test
    fun migrationsKeepRealEntriesAndRemoveLegacyBlankRows() = runBlocking {
        val databaseName = "stream-migration-${System.nanoTime()}.db"
        databaseNameToDelete = databaseName
        val databaseFile = context.getDatabasePath(databaseName)
        databaseFile.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { legacyDatabase ->
            legacyDatabase.execSQL(
                """
                CREATE TABLE IF NOT EXISTS entries (
                    timestamp INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(timestamp)
                )
                """.trimIndent(),
            )
            legacyDatabase.execSQL(
                "INSERT INTO entries(timestamp, text, updatedAt) VALUES(100, 'Existing', 100)",
            )
            legacyDatabase.execSQL(
                "INSERT INTO entries(timestamp, text, updatedAt) VALUES(101, char(32) || char(9) || char(10), 101)",
            )
            legacyDatabase.version = 1
        }

        val migrated = Room.databaseBuilder(context, StreamDatabase::class.java, databaseName)
            .addMigrations(
                StreamDatabase.MIGRATION_1_2,
                StreamDatabase.MIGRATION_2_3,
            )
            .build()
        database = migrated

        val entry = migrated.entryDao().getAll().single()
        assertEquals("Existing", entry.text)
        assertNull(entry.trashedAt)
    }

    @Test
    fun versionTwoExportPreservesTrashAndVersionOneStillImports() {
        val trashed = Entry(
            timestamp = 100L,
            text = "Recoverable",
            updatedAt = 200L,
            trashedAt = 300L,
        )

        val decodedV2 = EntryJsonCodec.decode(
            EntryJsonCodec.encode(listOf(trashed), exportedAtMillis = 400L),
            importedAtMillis = 500L,
        ).single()
        assertEquals(300L, decodedV2.trashedAt)

        val legacyV1 = """
            {
              "format": "stream",
              "version": 1,
              "entries": [{"timestamp": 600, "text": "Legacy"}]
            }
        """.trimIndent()
        val decodedV1 = EntryJsonCodec.decode(legacyV1, importedAtMillis = 700L).single()
        assertEquals("Legacy", decodedV1.text)
        assertNull(decodedV1.trashedAt)
    }
}
