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

class MessagesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SmsRepository(application)

    var messages by mutableStateOf<List<SmsMessage>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    private var job: Job? = null

    fun load(numbers: List<String>) {
        job?.cancel()
        job = viewModelScope.launch {
            loading = true
            messages = withContext(Dispatchers.IO) { repository.loadFor(numbers) }
            loading = false
        }
    }
}
