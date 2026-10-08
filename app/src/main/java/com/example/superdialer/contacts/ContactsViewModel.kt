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

    /** Every contact with a number, 가나다순. Used by the keypad autocomplete. */
    val contacts: List<Contact> get() = all

    var query by mutableStateOf("")
        private set

    var loading by mutableStateOf(false)
        private set

    /** Id of the app-side group the list is limited to, or null for everyone. */
    var selectedGroupId by mutableStateOf<String?>(null)
        private set

    private val filtered by derivedStateOf {
        val members = ContactGroups.get(selectedGroupId)?.members
        all.filter { ContactSorting.matches(it, query) && (members == null || it.id in members) }
    }

    /** Pinned on top; hidden while searching or inside a group so results are not duplicated. */
    val favorites: List<Contact> by derivedStateOf {
        if (query.isBlank() && selectedGroupId == null) filtered.filter { it.starred } else emptyList()
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

    fun selectGroup(id: String?) {
        selectedGroupId = id
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
            if (all.isNotEmpty()) {
                ContactGroups.prune(all.map { it.id }.toSet())
                if (ContactGroups.get(selectedGroupId) == null) selectedGroupId = null
            }
        }
    }

    fun mergeContacts(contactIds: List<Long>, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repository.merge(contactIds) }
            if (ok) refresh()
            onResult(ok)
        }
    }

    fun deleteContact(contactId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repository.delete(contactId) }
            if (ok) refresh()
            onResult(ok)
        }
    }

    fun toggleStar(contactId: Long, starred: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repository.setStarred(contactId, starred) }
            if (ok) {
                all = all.map { if (it.id == contactId) it.copy(starred = starred) else it }
                detail = detail?.takeIf { it.id == contactId }?.copy(starred = starred) ?: detail
            }
            onResult(ok)
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
