package com.seif.stream.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.seif.stream.data.StreamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val busy: Boolean = false,
    val status: String? = null,
)

class SettingsViewModel(
    private val repository: StreamRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    suspend fun prepareExport(): String? {
        _state.value = SettingsUiState(busy = true, status = "preparing export")
        return runCatching { repository.exportJson() }
            .onFailure { _state.value = SettingsUiState(status = "export failed") }
            .getOrNull()
    }

    fun exportFinished(success: Boolean) {
        _state.value = SettingsUiState(
            status = if (success) "export complete" else "export failed",
        )
    }

    fun operationCancelled() {
        _state.value = SettingsUiState()
    }

    fun importStarted() {
        _state.value = SettingsUiState(busy = true, status = "reading import")
    }

    suspend fun import(raw: String) {
        runCatching { repository.importJson(raw) }
            .onSuccess { importedCount ->
                _state.value = SettingsUiState(
                    status = when (importedCount) {
                        0 -> "nothing new to import"
                        1 -> "imported 1 entry"
                        else -> "imported $importedCount entries"
                    },
                )
            }
            .onFailure {
                _state.value = SettingsUiState(status = "import failed — invalid Stream file")
            }
    }

    fun importReadFailed() {
        _state.value = SettingsUiState(status = "import failed — file could not be read")
    }

    class Factory(
        private val repository: StreamRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
            return SettingsViewModel(repository) as T
        }
    }
}
