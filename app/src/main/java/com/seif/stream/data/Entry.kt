package com.seif.stream.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey val timestamp: Long,
    val text: String,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "NULL") val trashedAt: Long? = null,
)
