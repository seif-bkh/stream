package com.seif.stream.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Entry::class],
    version = 2,
    exportSchema = false,
)
abstract class StreamDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE entries ADD COLUMN trashedAt INTEGER DEFAULT NULL",
                )
            }
        }
    }
}
