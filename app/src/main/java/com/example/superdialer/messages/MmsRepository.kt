package com.example.superdialer.messages

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.superdialer.calllog.numberKey

/** One picture/video/audio/file of a multimedia message. Its bytes are read from the provider on demand ([uri]). */
data class MmsAttachment(val partId: Long, val mimeType: String, val name: String) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val uri: Uri get() = Uri.parse("content://mms/part/$partId")

    /** Short text for attachments that are not shown as a picture. */
    val label: String
        get() = when {
            mimeType.startsWith("video/") -> "동영상"
            mimeType.startsWith("audio/") -> "음성"
            mimeType.contains("vcard", ignoreCase = true) -> "연락처 카드"
            name.isNotBlank() -> name
            else -> "첨부 파일"
        }
}

/**
 * Reads MMS (picture / group / long text messages) from the system provider (needs READ_SMS; no default-SMS role is
 * needed to read). Only what the phone's own message app stored there is visible: RCS chats (채팅+) never reach this
 * provider, so they cannot be shown by any other app.
 *
 * The provider is queried without trusting its order or limit (see [SmsRepository]); the thread is found through the
 * canonical-address table, and when that finds nothing the newest messages are scanned by their address rows instead.
 */
class MmsRepository(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun loadFor(numbers: Collection<String>, limit: Int = SmsRepository.DEFAULT_LIMIT): List<SmsMessage> {
        val keys = numbers.map(::numberKey).filter { it.isNotEmpty() }.toSet()
        if (keys.isEmpty()) return emptyList()
        return try {
            val threads = threadIdsFor(keys)
            val rows = if (threads.isNotEmpty()) {
                queryRows("$THREAD_ID IN (${threads.joinToString(",")})")
            } else {
                scanRecent(keys)
            }
            val newest = rows.filter { it.box != BOX_DRAFT }.sortedByDescending { it.dateMillis }.take(limit)
            toMessages(newest, numbers.firstOrNull().orEmpty())
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: RuntimeException) {
            // an odd provider on some phone must never break the SMS list that is shown next to it
            emptyList()
        }
    }

    private fun threadIdsFor(keys: Set<String>): Set<Long> {
        val addressIds = HashSet<Long>()
        resolver.query(CANONICAL_ADDRESSES, arrayOf("_id", "address"), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (numberKey(c.getString(1).orEmpty()) in keys) addressIds += c.getLong(0)
            }
        }
        if (addressIds.isEmpty()) return emptySet()
        val rows = ArrayList<Pair<Long, String>>()
        resolver.query(THREADS, arrayOf("_id", "recipient_ids"), null, null, null)?.use { c ->
            while (c.moveToNext()) rows += c.getLong(0) to c.getString(1).orEmpty()
        }
        return threadsMatching(rows, addressIds)
    }

    private fun queryRows(selection: String?): List<MmsRow> {
        val out = ArrayList<MmsRow>()
        resolver.query(MMS, arrayOf("_id", "date", "msg_box", "m_type", "sub"), selection, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += MmsRow(
                    id = c.getLong(0),
                    dateMillis = mmsDateMillis(c.getLong(1)),
                    box = c.getInt(2),
                    type = c.getInt(3),
                    subject = c.getString(4).orEmpty(),
                )
            }
        }
        return out
    }

    private fun scanRecent(keys: Set<String>): List<MmsRow> {
        val recent = queryRows(null).sortedByDescending { it.dateMillis }.take(SCAN_ROWS)
        return recent.filter { row ->
            var match = false
            resolver.query(Uri.parse("content://mms/${row.id}/addr"), arrayOf("address"), null, null, null)?.use { c ->
                while (c.moveToNext() && !match) match = numberKey(c.getString(0).orEmpty()) in keys
            }
            match
        }
    }

    private fun toMessages(rows: List<MmsRow>, address: String): List<SmsMessage> {
        if (rows.isEmpty()) return emptyList()
        val parts = HashMap<Long, MutableList<Part>>()
        val ids = rows.joinToString(",") { it.id.toString() }
        resolver.query(PARTS, arrayOf("_id", "mid", "ct", "name", "text"), "mid IN ($ids)", null, "seq ASC")?.use { c ->
            while (c.moveToNext()) {
                parts.getOrPut(c.getLong(1)) { ArrayList() } +=
                    Part(c.getLong(0), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getString(4).orEmpty())
            }
        }
        return rows.map { row ->
            val mine = parts[row.id].orEmpty()
            val text = mine.filter { it.type == "text/plain" }.joinToString("\n") { it.text }.trim()
            val attachments = mine.filter { isAttachment(it.type) }.map { MmsAttachment(it.id, it.type, it.name) }
            SmsMessage(
                id = row.id,
                address = address,
                body = bodyFor(row, text),
                dateMillis = row.dateMillis,
                outgoing = isOutgoing(row.box),
                attachments = attachments,
                isMms = true,
            )
        }
    }

    private data class MmsRow(val id: Long, val dateMillis: Long, val box: Int, val type: Int, val subject: String)
    private data class Part(val id: Long, val type: String, val name: String, val text: String)

    companion object {
        private val MMS: Uri get() = Uri.parse("content://mms")
        private val PARTS: Uri get() = Uri.parse("content://mms/part")
        private val CANONICAL_ADDRESSES: Uri get() = Uri.parse("content://mms-sms/canonical-addresses")
        private val THREADS: Uri get() = Uri.parse("content://mms-sms/conversations?simple=true")
        private const val THREAD_ID = "thread_id"

        private const val BOX_INBOX = 1
        private const val BOX_DRAFT = 3

        /** m_type of a notification that only announces an MMS that has not been downloaded yet. */
        private const val TYPE_NOTIFICATION_IND = 130

        /** Safety cap for the fallback scan, which asks the provider once per message. */
        private const val SCAN_ROWS = 300

        private fun bodyFor(row: MmsRow, text: String): String = when {
            text.isNotEmpty() -> text
            row.type == TYPE_NOTIFICATION_IND -> "아직 받지 않은 멀티미디어 메시지"
            else -> row.subject.takeUnless { it.isBlank() }.orEmpty()
        }

        // --- Pure (unit tested) -----------------------------------------------------------------------

        /** The provider stores MMS dates in seconds (SMS uses milliseconds). */
        fun mmsDateMillis(seconds: Long): Long = seconds * 1000

        /** Only the inbox is incoming; sent, outbox and failed are ours. */
        fun isOutgoing(box: Int): Boolean = box != BOX_INBOX

        /** The text and the SMIL layout file are not attachments; everything else (picture, video, audio, card) is. */
        fun isAttachment(mimeType: String): Boolean =
            mimeType.isNotEmpty() && mimeType != "text/plain" && mimeType != "application/smil"

        /** "12 34" -> [12, 34]: the canonical-address ids of a thread's recipients. */
        fun recipientIds(raw: String): List<Long> = raw.split(' ', ',').mapNotNull { it.trim().toLongOrNull() }

        /** Threads (id to raw recipient_ids) with at least one recipient among [addressIds]. */
        fun threadsMatching(threads: List<Pair<Long, String>>, addressIds: Set<Long>): Set<Long> =
            threads.filter { (_, raw) -> recipientIds(raw).any { it in addressIds } }.map { it.first }.toSet()
    }
}
