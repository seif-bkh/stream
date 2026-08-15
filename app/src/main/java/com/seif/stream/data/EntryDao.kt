package com.seif.stream.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE trashedAt IS NULL ORDER BY timestamp DESC")
    fun observeActive(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE trashedAt IS NOT NULL ORDER BY timestamp DESC")
    fun observeTrashed(): Flow<List<Entry>>

    @Query("SELECT * FROM entries ORDER BY timestamp DESC")
    suspend fun getAll(): List<Entry>

    @Upsert
    suspend fun upsert(entry: Entry)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringTimestamps(entries: List<Entry>): List<Long>

    @Query("UPDATE entries SET trashedAt = :trashedAt WHERE timestamp = :timestamp AND trashedAt IS NULL")
    suspend fun moveToTrash(timestamp: Long, trashedAt: Long): Int

    @Query("UPDATE entries SET trashedAt = NULL WHERE timestamp = :timestamp AND trashedAt IS NOT NULL")
    suspend fun restoreFromTrash(timestamp: Long): Int

    @Query("DELETE FROM entries WHERE timestamp = :timestamp AND trashedAt IS NOT NULL")
    suspend fun deletePermanently(timestamp: Long): Int
}
