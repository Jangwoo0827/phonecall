package com.example.superdialer.messages

import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.Telephony
import com.example.superdialer.calllog.numberKey

data class SmsMessage(
    val id: Long,
    val address: String,
    val body: String,
    val dateMillis: Long,
    /** True for messages this phone sent (or tried to send). */
    val outgoing: Boolean,
)

/**
 * Reads SMS from the system provider (needs READ_SMS). Only plain SMS lives there: MMS and RCS chat
 * messages (e.g. 채팅+, KakaoTalk) are stored elsewhere and do not show up.
 */
class SmsRepository(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    /**
     * The most recent [limit] messages to or from any of [numbers], oldest first.
     * Numbers match across formats (+82 10-1234-5678 = 01012345678). Blocking I/O.
     */
    fun loadFor(numbers: Collection<String>, limit: Int = DEFAULT_LIMIT): List<SmsMessage> {
        val keys = numbers.map(::numberKey).filter { it.isNotEmpty() }.toSet()
        if (keys.isEmpty()) return emptyList()

        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${Telephony.Sms.DATE} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, SCAN_LIMIT)
        }
        val found = ArrayList<SmsMessage>()
        try {
            resolver.query(Telephony.Sms.CONTENT_URI, PROJECTION, args, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressCol = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeCol = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (c.moveToNext() && found.size < limit) {
                    val type = c.getInt(typeCol)
                    if (type == Telephony.Sms.MESSAGE_TYPE_DRAFT) continue
                    val address = c.getString(addressCol).orEmpty()
                    if (numberKey(address) !in keys) continue
                    found += SmsMessage(
                        id = c.getLong(idCol),
                        address = address,
                        body = c.getString(bodyCol).orEmpty(),
                        dateMillis = c.getLong(dateCol),
                        outgoing = isOutgoing(type),
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return found.reversed()
    }

    companion object {
        const val DEFAULT_LIMIT = 30

        /** How many of the newest messages are scanned for a match; keeps the lookup quick on big inboxes. */
        private const val SCAN_LIMIT = 5000

        private val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
        )

        /** Everything except the inbox counts as ours: sent, outbox, queued and failed. */
        fun isOutgoing(type: Int): Boolean = type != Telephony.Sms.MESSAGE_TYPE_INBOX
    }
}
