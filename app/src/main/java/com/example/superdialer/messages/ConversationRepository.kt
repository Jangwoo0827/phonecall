package com.example.superdialer.messages

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.example.superdialer.calllog.numberKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One text conversation (a thread of the system message store): who it is with, and its latest message. */
data class Conversation(
    val threadId: Long,
    /** Every other person in the thread (more than one for a group message). */
    val addresses: List<String>,
    /** Saved contact names, in the same order as [addresses]; null where a number is not saved. */
    val names: List<String?>,
    val contactId: Long?,
    val snippet: String,
    val dateMillis: Long,
    val messageCount: Int,
    val unread: Boolean,
) {
    val isGroup: Boolean get() = addresses.size > 1

    /** The number a one-to-one conversation is with (the first one of a group). */
    val number: String get() = addresses.firstOrNull().orEmpty()

    /** Names where known, numbers otherwise, joined for groups. */
    val title: String
        get() = addresses.mapIndexed { i, address -> names.getOrNull(i) ?: address }.joinToString(", ").ifEmpty { "(알 수 없음)" }
}

/**
 * The conversation list, read from the system provider (needs READ_SMS): threads come from
 * `content://mms-sms/conversations`, the people in them from the canonical-address table. Like the other readers it does
 * not trust the provider's order or limit. RCS (채팅+) chats are not in this store and never show up.
 */
class ConversationRepository(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    /** The [limit] most recently active conversations, newest first. Blocking I/O. */
    fun load(limit: Int = DEFAULT_LIMIT): List<Conversation> = try {
        val addressById = HashMap<Long, String>()
        resolver.query(CANONICAL_ADDRESSES, arrayOf("_id", "address"), null, null, null)?.use { c ->
            while (c.moveToNext()) addressById[c.getLong(0)] = c.getString(1).orEmpty()
        }
        val nameCache = HashMap<String, ContactName?>()
        val found = ArrayList<Conversation>()
        resolver.query(
            THREADS, arrayOf("_id", "date", "message_count", "recipient_ids", "snippet", "read"), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val addresses = MmsRepository.recipientIds(c.getString(3).orEmpty()).mapNotNull { addressById[it] }.filter { it.isNotBlank() }
                if (addresses.isEmpty()) continue
                val matches = addresses.map { address -> nameCache.getOrPut(address) { lookupName(address) } }
                found += Conversation(
                    threadId = c.getLong(0),
                    addresses = addresses,
                    names = matches.map { it?.name },
                    contactId = matches.firstOrNull()?.id,
                    snippet = c.getString(4).orEmpty(),
                    dateMillis = c.getLong(1),
                    messageCount = c.getInt(2),
                    unread = c.getInt(5) == 0,
                )
            }
        }
        newestFirst(found, limit)
    } catch (e: SecurityException) {
        emptyList()
    } catch (e: RuntimeException) {
        emptyList()
    }

    /** The saved contact for [number], or null. Blocking I/O. */
    fun lookupName(number: String): ContactName? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        resolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) ContactName(it.getLong(0), it.getString(1)) else null }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    class ContactName(val id: Long, val name: String?)

    companion object {
        const val DEFAULT_LIMIT = 100
        private val CANONICAL_ADDRESSES: Uri get() = Uri.parse("content://mms-sms/canonical-addresses")
        private val THREADS: Uri get() = Uri.parse("content://mms-sms/conversations?simple=true")

        // --- Pure (unit tested) -----------------------------------------------------------------------

        fun newestFirst(conversations: List<Conversation>, limit: Int): List<Conversation> =
            conversations.sortedByDescending { it.dateMillis }.take(limit)

        /** Matches the title, the numbers (any format) and the latest message; an empty query matches everything. */
        fun matches(conversation: Conversation, query: String): Boolean {
            val q = query.trim()
            if (q.isEmpty()) return true
            if (conversation.title.contains(q, ignoreCase = true)) return true
            if (conversation.snippet.contains(q, ignoreCase = true)) return true
            val digits = q.filter(Char::isDigit)
            return digits.isNotEmpty() && conversation.addresses.any { numberKey(it).contains(digits) }
        }

        private val TIME = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
        private val DAY = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)

        /** "오후 2:33" today, "어제", else "10월 8일". */
        fun timeLabel(millis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
            val moment = Instant.ofEpochMilli(millis).atZone(zone)
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val day: LocalDate = moment.toLocalDate()
            return when (day) {
                today -> moment.format(TIME)
                today.minusDays(1) -> "어제"
                else -> moment.format(DAY)
            }
        }
    }
}
