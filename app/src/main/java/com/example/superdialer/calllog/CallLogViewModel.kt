package com.example.superdialer.calllog

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallLogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CallLogRepository(application)

    var entries by mutableStateOf<List<CallLogEntry>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (entries.isEmpty()) loading = true
            entries = try {
                withContext(Dispatchers.IO) { repository.load() }
            } catch (e: SecurityException) {
                emptyList()
            }
            loading = false
        }
    }

    fun delete(entry: CallLogEntry, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repository.delete(entry.id) }
            if (ok) entries = entries.filterNot { it.id == entry.id }
            onResult(ok)
        }
    }

    fun block(number: String, onResult: (BlockResult) -> Unit) {
        viewModelScope.launch {
            onResult(withContext(Dispatchers.IO) { repository.block(number) })
        }
    }
}
