package com.example.superdialer.contacts

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ContactsRepository(application)

    private var all by mutableStateOf<List<Contact>>(emptyList())

    var query by mutableStateOf("")
        private set

    var loading by mutableStateOf(false)
        private set

    private val filtered by derivedStateOf { all.filter { ContactSorting.matches(it, query) } }

    /** Pinned on top; hidden while searching so results are not duplicated. */
    val favorites: List<Contact> by derivedStateOf {
        if (query.isBlank()) filtered.filter { it.starred } else emptyList()
    }

    /** Every matching contact grouped by initial, in 가나다순. */
    val sections: List<ContactSection> by derivedStateOf {
        filtered.groupBy { ContactSorting.initialOf(it.name) }
            .map { (header, list) -> ContactSection(header, list) }
    }

    val isEmpty: Boolean get() = filtered.isEmpty()

    var detail by mutableStateOf<ContactDetail?>(null)
        private set

    private var loadJob: Job? = null

    fun onQueryChange(value: String) {
        query = value
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (all.isEmpty()) loading = true
            all = try {
                withContext(Dispatchers.IO) { repository.loadAll() }
            } catch (e: SecurityException) {
                emptyList()
            }
            loading = false
        }
    }

    fun loadDetail(contactId: Long) {
        if (detail?.id != contactId) detail = null
        viewModelScope.launch {
            detail = try {
                withContext(Dispatchers.IO) { repository.loadDetail(contactId) }
            } catch (e: SecurityException) {
                null
            }
        }
    }
}
