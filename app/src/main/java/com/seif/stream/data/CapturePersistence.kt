package com.seif.stream.data

interface CapturePersistence {
    fun recoverDraft(): RecoveredDraft?

    suspend fun writeDraft(text: String, timestamp: Long)

    suspend fun discardBlankDraft(text: String, timestamp: Long)

    suspend fun commitCapture(text: String, timestamp: Long)
}
