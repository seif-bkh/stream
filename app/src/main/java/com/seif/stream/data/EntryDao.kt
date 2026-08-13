package com.seif.stream.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Entry>>

    @Query("SELECT * FROM entries ORDER BY timestamp DESC")
    suspend fun getAll(): List<Entry>

    @Upsert
    suspend fun upsert(entry: Entry)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringTimestamps(entries: List<Entry>): List<Long>
}
