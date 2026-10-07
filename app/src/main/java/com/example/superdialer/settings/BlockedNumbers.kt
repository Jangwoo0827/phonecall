package com.example.superdialer.settings

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BlockedNumber(val id: Long, val number: String)

/** The system block list; only the default dialer (or carrier app) may read and write it. */
class BlockedNumbersRepository(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun canBlock(): Boolean = try {
        BlockedNumberContract.canCurrentUserBlockNumbers(context)
    } catch (e: SecurityException) {
        false
    }

    fun list(): List<BlockedNumber> = try {
        val out = ArrayList<BlockedNumber>()
        resolver.query(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            arrayOf(
                BlockedNumberContract.BlockedNumbers.COLUMN_ID,
                BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
            ),
            null, null, "${BlockedNumberContract.BlockedNumbers.COLUMN_ID} DESC",
        )?.use { c ->
            while (c.moveToNext()) out += BlockedNumber(c.getLong(0), c.getString(1).orEmpty())
        }
        out
    } catch (e: SecurityException) {
        emptyList()
    }

    fun add(number: String): Boolean = try {
        val values = ContentValues().apply {
            put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
        }
        resolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values) != null
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }

    fun remove(id: Long): Boolean = try {
        resolver.delete(
            ContentUris.withAppendedId(BlockedNumberContract.BlockedNumbers.CONTENT_URI, id), null, null
        ) > 0
    } catch (e: SecurityException) {
        false
    }
}

class BlockedNumbersViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BlockedNumbersRepository(application)

    var canBlock by mutableStateOf(false)
        private set
    var numbers by mutableStateOf<List<BlockedNumber>>(emptyList())
        private set

    fun refresh() {
        viewModelScope.launch {
            val (allowed, list) = withContext(Dispatchers.IO) {
                val allowed = repository.canBlock()
                allowed to if (allowed) repository.list() else emptyList()
            }
            canBlock = allowed
            numbers = list
        }
    }

    fun add(number: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repository.add(number) }
            if (ok) refresh()
            onResult(ok)
        }
    }

    fun remove(item: BlockedNumber) {
        viewModelScope.launch {
            if (withContext(Dispatchers.IO) { repository.remove(item.id) }) refresh()
        }
    }
}
