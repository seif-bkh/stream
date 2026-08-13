package com.seif.stream

import android.app.Application
import androidx.room.Room
import com.seif.stream.data.DraftStore
import com.seif.stream.data.StreamDatabase
import com.seif.stream.data.StreamRepository

class StreamApplication : Application() {
    private val database: StreamDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            StreamDatabase::class.java,
            "stream_entries.db",
        ).build()
    }

    val repository: StreamRepository by lazy {
        StreamRepository(
            entryDao = database.entryDao(),
            draftStore = DraftStore(applicationContext),
        )
    }
}
