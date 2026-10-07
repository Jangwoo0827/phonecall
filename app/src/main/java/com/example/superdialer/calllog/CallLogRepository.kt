package com.example.superdialer.calllog

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import com.example.superdialer.ui.hasPermission

enum class BlockResult { Blocked, NotAllowed, Failed }

class CallLogRepository(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** Newest first. Blocking I/O: call from a background dispatcher. */
    fun load(limit: Int = DEFAULT_LIMIT): List<CallLogEntry> {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${CallLog.Calls.DATE} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
        }
        val canLookup = context.hasPermission(Manifest.permission.READ_CONTACTS)
        val matchCache = HashMap<String, ContactMatch?>()
        val entries = ArrayList<CallLogEntry>()

        resolver.query(CallLog.Calls.CONTENT_URI, PROJECTION, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numberCol = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeCol = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateCol = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durCol = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val nameCol = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            while (c.moveToNext()) {
                val number = c.getString(numberCol).orEmpty()
                val match = if (canLookup && number.isNotEmpty()) {
                    matchCache.getOrPut(number) { lookupContact(number) }
                } else {
                    null
                }
                val cachedName = c.getString(nameCol)?.takeIf { it.isNotBlank() }
                entries += CallLogEntry(
                    id = c.getLong(idCol),
                    number = number,
                    name = cachedName ?: match?.name,
                    contactId = match?.id,
                    type = CallType.fromCallLog(c.getInt(typeCol)),
                    dateMillis = c.getLong(dateCol),
                    durationSeconds = c.getLong(durCol),
                )
            }
        }
        return entries
    }

    /** Needs WRITE_CALL_LOG. */
    fun delete(id: Long): Boolean = try {
        resolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID}=?", arrayOf(id.toString())) > 0
    } catch (e: SecurityException) {
        false
    }

    /** Only the default dialer / carrier app may write to the block list. */
    fun block(number: String): BlockResult {
        if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) return BlockResult.NotAllowed
        return try {
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
            }
            if (resolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values) != null) {
                BlockResult.Blocked
            } else {
                BlockResult.Failed
            }
        } catch (e: SecurityException) {
            BlockResult.NotAllowed
        } catch (e: IllegalArgumentException) {
            BlockResult.Failed
        }
    }

    private class ContactMatch(val id: Long, val name: String?)

    private fun lookupContact(number: String): ContactMatch? = try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME)
        resolver.query(uri, projection, null, null, null)
            ?.use { if (it.moveToFirst()) ContactMatch(it.getLong(0), it.getString(1)) else null }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private companion object {
        const val DEFAULT_LIMIT = 500
        val PROJECTION = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.CACHED_NAME,
        )
    }
}
