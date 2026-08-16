package com.seif.stream.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.seif.stream.data.CapturePersistence
import com.seif.stream.data.Entry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

private const val DRAFT_DEBOUNCE_MILLIS = 275L
private const val REAL_SAVE_DEBOUNCE_MILLIS = 2_000L

enum class CaptureSaveStatus(val label: String) {
    Ready("ready"),
    Saving("saving"),
    Saved("saved"),
    NotSaved("not saved"),
}

data class CaptureUiState(
    val text: String = "",
    val timestamp: Long? = null,
    val saveStatus: CaptureSaveStatus = CaptureSaveStatus.Ready,
    val recovered: Boolean = false,
    val dirty: Boolean = false,
    val sessionId: Long = 0L,
)

class CaptureViewModel(
    private val repository: CapturePersistence,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    /** Stable across configuration changes, new after process death. */
    val uiSessionKey: String = UUID.randomUUID().toString()

    private val recoveredDraft = repository.recoverDraft()

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(
        recoveredDraft?.let { draft ->
            CaptureUiState(
                text = draft.text,
                timestamp = draft.timestamp,
                saveStatus = CaptureSaveStatus.Saving,
                recovered = true,
                dirty = true,
            )
        } ?: CaptureUiState(),
    )
    val state: kotlinx.coroutines.flow.StateFlow<CaptureUiState> = _state

    private var lastCommittedText: String? = null
    private var revision = 0L
    private var draftJob: Job? = null
    private var realSaveJob: Job? = null

    init {
        if (recoveredDraft != null) {
            scheduleRealSave(
                snapshot = _state.value,
                snapshotRevision = revision,
            )
        }
    }

    fun onTextChanged(text: String) {
        val current = _state.value
        if (text == current.text) return

        val timestamp = current.timestamp ?: if (text.isNotEmpty()) nowMillis() else null
        if (timestamp == null) {
            _state.value = current.copy(text = text)
            return
        }

        revision += 1L
        val snapshotRevision = revision
        val next = current.copy(
            text = text,
            timestamp = timestamp,
            saveStatus = CaptureSaveStatus.Saving,
            dirty = true,
        )
        _state.value = next

        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MILLIS)
            try {
                repository.writeDraft(next.text, timestamp)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                markNotSavedIfCurrent(snapshotRevision)
            }
        }

        scheduleRealSave(next, snapshotRevision)
    }

    private fun scheduleRealSave(snapshot: CaptureUiState, snapshotRevision: Long) {
        val timestamp = snapshot.timestamp ?: return
        realSaveJob?.cancel()
        realSaveJob = viewModelScope.launch {
            delay(REAL_SAVE_DEBOUNCE_MILLIS)
            if (revision != snapshotRevision) return@launch
            // Make the two tiers ordered even if the IO dispatcher is under load.
            draftJob?.join()
            if (revision != snapshotRevision) return@launch

            try {
                if (snapshot.text.isBlank()) {
                    repository.discardBlankDraft(snapshot.text, timestamp)
                    if (revision == snapshotRevision) {
                        restoreAfterBlank(snapshot)
                    }
                } else {
                    repository.commitCapture(snapshot.text, timestamp)
                    lastCommittedText = snapshot.text
                    if (revision == snapshotRevision) {
                        _state.value = _state.value.copy(
                            saveStatus = CaptureSaveStatus.Saved,
                            recovered = false,
                            dirty = false,
                        )
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                markNotSavedIfCurrent(snapshotRevision)
            }
        }
    }

    private fun markNotSavedIfCurrent(snapshotRevision: Long) {
        if (revision == snapshotRevision) {
            _state.value = _state.value.copy(saveStatus = CaptureSaveStatus.NotSaved)
        }
    }

    private fun restoreAfterBlank(snapshot: CaptureUiState) {
        val previousText = lastCommittedText
        _state.value = if (previousText != null && snapshot.timestamp != null) {
            CaptureUiState(
                text = previousText,
                timestamp = snapshot.timestamp,
                saveStatus = CaptureSaveStatus.Saved,
                sessionId = snapshot.sessionId + 1L,
            )
        } else {
            CaptureUiState(sessionId = snapshot.sessionId + 1L)
        }
    }

    /**
     * `onStop()` is the lifecycle durability boundary. This intentionally blocks for one small
     * local file write and one Room upsert; no work is delegated to a background service.
     */
    fun flushOnStop() {
        val snapshot = _state.value
        val timestamp = snapshot.timestamp ?: return
        if (!snapshot.dirty) return

        draftJob?.cancel()
        realSaveJob?.cancel()
        val snapshotRevision = revision
        runCatching {
            runBlocking {
                if (snapshot.text.isBlank()) {
                    repository.discardBlankDraft(snapshot.text, timestamp)
                } else {
                    repository.commitCapture(snapshot.text, timestamp)
                }
            }
        }.onSuccess {
            if (revision == snapshotRevision) {
                if (snapshot.text.isBlank()) {
                    restoreAfterBlank(snapshot)
                } else {
                    lastCommittedText = snapshot.text
                    _state.value = _state.value.copy(
                        saveStatus = CaptureSaveStatus.Saved,
                        recovered = false,
                        dirty = false,
                    )
                }
            }
        }.onFailure {
            markNotSavedIfCurrent(snapshotRevision)
        }
    }

    /** Saves the current entry before replacing it with the fresh Capture requested by the FAB. */
    suspend fun startFresh(): Boolean {
        val snapshot = _state.value
        if (!prepareToReplace(snapshot)) return false

        lastCommittedText = null
        _state.value = CaptureUiState(sessionId = snapshot.sessionId + 1L)
        return true
    }

    /** Opens an existing entry without ever replacing its original first-keystroke timestamp. */
    suspend fun openEntry(entry: Entry): Boolean {
        require(entry.trashedAt == null) { "A trashed entry cannot be opened for editing" }
        val snapshot = _state.value

        if (snapshot.timestamp == entry.timestamp) {
            // Keep newer in-memory text if the user returns before its debounce has completed.
            if (lastCommittedText == null) lastCommittedText = entry.text
            _state.value = snapshot.copy(sessionId = snapshot.sessionId + 1L)
            return true
        }

        if (!prepareToReplace(snapshot)) return false
        lastCommittedText = entry.text
        _state.value = CaptureUiState(
            text = entry.text,
            timestamp = entry.timestamp,
            saveStatus = CaptureSaveStatus.Saved,
            sessionId = snapshot.sessionId + 1L,
        )
        return true
    }

    /**
     * Saves the latest visible revision, then clears Capture before the repository moves this
     * timestamp to Trash. Clearing first prevents a later lifecycle flush from recreating it.
     */
    suspend fun prepareEntryForTrash(timestamp: Long): Boolean {
        val snapshot = _state.value
        if (snapshot.timestamp != timestamp) return true
        if (!prepareToReplace(snapshot)) return false

        lastCommittedText = null
        _state.value = CaptureUiState(sessionId = snapshot.sessionId + 1L)
        return true
    }

    private suspend fun prepareToReplace(snapshot: CaptureUiState): Boolean {
        revision += 1L
        draftJob?.cancel()
        realSaveJob?.cancel()

        if (snapshot.dirty && snapshot.timestamp != null) {
            try {
                if (snapshot.text.isBlank()) {
                    repository.discardBlankDraft(snapshot.text, snapshot.timestamp)
                } else {
                    repository.commitCapture(snapshot.text, snapshot.timestamp)
                    lastCommittedText = snapshot.text
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _state.value = snapshot.copy(saveStatus = CaptureSaveStatus.NotSaved)
                return false
            }
        }
        return true
    }

    class Factory(
        private val repository: CapturePersistence,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CaptureViewModel::class.java))
            return CaptureViewModel(repository) as T
        }
    }
}
