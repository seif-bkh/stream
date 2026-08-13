package com.seif.stream.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.seif.stream.data.Entry
import com.seif.stream.data.StreamRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LogViewModel(repository: StreamRepository) : ViewModel() {
    val entries: StateFlow<List<Entry>> = repository.entries.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        initialValue = emptyList(),
    )

    class Factory(
        private val repository: StreamRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LogViewModel::class.java))
            return LogViewModel(repository) as T
        }
    }
}
