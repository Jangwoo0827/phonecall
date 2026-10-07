package com.example.superdialer.calllog

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
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

    var query by mutableStateOf("")
        private set

    var filter by mutableStateOf(CallFilter.All)
        private set

    /** Entries after filter + search, merged into per-contact groups. */
    val groups: List<CallGroup> by derivedStateOf {
        groupCalls(entries.filter { filter.accepts(it) && matchesCallQuery(it, query) })
    }

    // Multi-select mode: holds [CallGroup.id]s.
    var selecting by mutableStateOf(false)
        private set
    val selected = mutableStateSetOf<Long>()

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

    fun onQueryChange(value: String) {
        query = value
    }

    fun onFilterChange(value: CallFilter) {
        filter = value
    }

    fun startSelecting(groupId: Long) {
        selecting = true
        selected.clear()
        selected.add(groupId)
    }

    fun toggleSelected(groupId: Long) {
        if (!selected.remove(groupId)) selected.add(groupId)
        if (selected.isEmpty()) selecting = false
    }

    fun selectAll() {
        selected.addAll(groups.map { it.id })
    }

    fun stopSelecting() {
        selecting = false
        selected.clear()
    }

    fun deleteGroup(group: CallGroup, onResult: (Boolean) -> Unit) = deleteEntries(group.entries, onResult)

    fun deleteSelected(onResult: (Boolean) -> Unit) {
        val victims = groups.filter { it.id in selected }.flatMap { it.entries }
        deleteEntries(victims) { ok ->
            if (ok) stopSelecting()
            onResult(ok)
        }
    }

    private fun deleteEntries(victims: List<CallLogEntry>, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { repository.deleteMany(victims.map { it.id }) }
            val ids = victims.map { it.id }.toSet()
            if (removed > 0) entries = entries.filterNot { it.id in ids }
            onResult(removed == victims.size)
        }
    }

    fun deleteAll(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val removed = withContext(Dispatchers.IO) { repository.deleteAll() }
            if (removed > 0) entries = emptyList()
            stopSelecting()
            onResult(removed > 0 || entries.isEmpty())
        }
    }

    fun block(number: String, onResult: (BlockResult) -> Unit) {
        viewModelScope.launch {
            onResult(withContext(Dispatchers.IO) { repository.block(number) })
        }
    }
}
