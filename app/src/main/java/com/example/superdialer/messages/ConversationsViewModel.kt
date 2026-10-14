package com.example.superdialer.messages

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

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConversationRepository(application)

    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            if (conversations.isEmpty()) loading = true
            conversations = withContext(Dispatchers.IO) { repository.load() }
            loading = false
        }
    }
}
