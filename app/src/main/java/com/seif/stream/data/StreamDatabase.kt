package com.seif.stream.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Entry::class],
    version = 3,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    DELETE FROM entries
                    WHERE length(
                        trim(
                            text,
                            char(9) || char(10) || char(11) || char(12) || char(13) ||
                            char(32) || char(160) || char(5760) || char(8192) || char(8193) ||
                            char(8194) || char(8195) || char(8196) || char(8197) || char(8198) ||
                            char(8199) || char(8200) || char(8201) || char(8202) || char(8232) ||
                            char(8233) || char(8239) || char(8287) || char(12288)
                        )
                    ) = 0
                    """.trimIndent(),
                )
            }
        }
    }
}
