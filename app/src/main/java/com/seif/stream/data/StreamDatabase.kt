package com.seif.stream.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Entry::class],
    version = 1,
    exportSchema = false,
)
abstract class StreamDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
}
