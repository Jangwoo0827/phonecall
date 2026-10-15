package com.example.superdialer.kakao

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One message collected from a KakaoTalk notification (or a reply sent from this app, [outgoing]). */
data class KakaoMessage(
    val room: String,
    val sender: String,
    val text: String,
    val timeMillis: Long,
    val outgoing: Boolean = false,
)

/** A chat room as seen in the collected notifications: its newest message and how many were collected. */
data class KakaoRoom(val name: String, val last: KakaoMessage, val count: Int)

/**
 * KakaoTalk has no API for other apps, so the only thing this app can see of a chat is what KakaoTalk puts in its
 * notifications, through Android's notification access (which the user grants in the system settings). This collects
 * those (sender, room, preview text), keeps them on this phone only, and can answer through the notification's own reply
 * button while that notification is still showing. History from before, or a message whose notification was hidden
 * (preview off) or already dismissed, is not available.
 *
 * Off by default; see [switchCollecting]. Nothing here is uploaded or synced.
 */
object KakaoInbox {
    private const val PREFS = "kakao_inbox"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MESSAGES = "messages"
    const val MAX_MESSAGES = 500

    /** Apps whose notifications are collected. Tests add their own package to feed the inbox. */
    internal val targetPackages: MutableSet<String> = mutableSetOf(KakaoLinks.PACKAGE)

    private var prefs: SharedPreferences? = null

    /** Whether collecting is switched on (the user also has to allow notification access in the system settings). */
    var enabled by mutableStateOf(false)
        private set

    /** Everything collected, oldest first. Observable by Compose. */
    var messages by mutableStateOf<List<KakaoMessage>>(emptyList())
        private set

    /** What it takes to answer a chat through its notification, while that notification is showing. */
    class ReplyHandle(
        val notificationKey: String,
        val reply: PendingIntent,
        val inputs: Array<RemoteInput>,
        val open: PendingIntent?,
    )

    private val handles = ConcurrentHashMap<String, ReplyHandle>()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean(KEY_ENABLED, false)
        messages = decode(p.getString(KEY_MESSAGES, null))
    }

    fun switchCollecting(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }

    /** Adds new messages (duplicates and repeats of a notification update are dropped) and keeps the newest [MAX_MESSAGES]. */
    fun add(incoming: List<KakaoMessage>) {
        if (incoming.isEmpty()) return
        val merged = merge(messages, incoming, MAX_MESSAGES)
        if (merged == messages) return
        messages = merged
        prefs?.edit()?.putString(KEY_MESSAGES, encode(merged))?.apply()
    }

    fun clear() {
        messages = emptyList()
        prefs?.edit()?.remove(KEY_MESSAGES)?.apply()
        handles.clear()
    }

    fun rooms(): List<KakaoRoom> = groupRooms(messages)

    fun messagesIn(room: String): List<KakaoMessage> = messages.filter { it.room == room }

    // --- Answering through the notification ---------------------------------------------------------

    fun setReplyHandle(room: String, handle: ReplyHandle) {
        handles[room] = handle
    }

    /** The notification went away: its reply button is no longer usable. */
    fun dropReplyHandle(notificationKey: String) {
        handles.entries.removeAll { it.value.notificationKey == notificationKey }
    }

    fun canReply(room: String): Boolean = handles.containsKey(room)

    /** Opens that chat in KakaoTalk through the notification's own tap action; false when it is gone. */
    fun openChat(room: String): Boolean = try {
        handles[room]?.open?.send() != null
    } catch (e: PendingIntent.CanceledException) {
        false
    }

    /** Sends [text] as the reply of the notification of [room]. False when that notification is gone or KakaoTalk refused. */
    fun reply(context: Context, room: String, text: String, now: Long = System.currentTimeMillis()): Boolean {
        val handle = handles[room] ?: return false
        val clean = text.trim()
        if (clean.isEmpty()) return false
        return try {
            val results = Bundle()
            handle.inputs.forEach { results.putCharSequence(it.resultKey, clean) }
            val fill = Intent()
            RemoteInput.addResultsToIntent(handle.inputs, fill, results)
            handle.reply.send(context, 0, fill)
            add(listOf(KakaoMessage(room, "나", clean, now, outgoing = true)))
            true
        } catch (e: PendingIntent.CanceledException) {
            handles.remove(room)
            false
        }
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    /** Messages of the same room, sender and text within this many ms are one message (a notification updated again). */
    const val REPEAT_WINDOW_MS = 60_000L

    fun merge(existing: List<KakaoMessage>, incoming: List<KakaoMessage>, limit: Int): List<KakaoMessage> {
        val result = ArrayList(existing)
        for (message in incoming) {
            val repeat = result.any {
                it.room == message.room && it.sender == message.sender && it.text == message.text &&
                    it.outgoing == message.outgoing && kotlin.math.abs(it.timeMillis - message.timeMillis) <= REPEAT_WINDOW_MS
            }
            if (!repeat) result += message
        }
        result.sortBy { it.timeMillis }
        return if (result.size > limit) result.takeLast(limit) else result
    }

    /** Rooms newest first, each with its newest message and how many messages were collected for it. */
    fun groupRooms(messages: List<KakaoMessage>): List<KakaoRoom> =
        messages.groupBy { it.room }
            .map { (room, list) -> KakaoRoom(room, list.maxBy { it.timeMillis }, list.size) }
            .sortedByDescending { it.last.timeMillis }

    fun encode(messages: List<KakaoMessage>): String {
        val array = JSONArray()
        messages.forEach {
            array.put(JSONObject().put("r", it.room).put("s", it.sender).put("t", it.text).put("m", it.timeMillis).put("o", it.outgoing))
        }
        return array.toString()
    }

    fun decode(json: String?): List<KakaoMessage> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val room = o.optString("r")
                if (room.isEmpty()) null else KakaoMessage(room, o.optString("s"), o.optString("t"), o.optLong("m"), o.optBoolean("o"))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}

/** Turns what a KakaoTalk notification shows into messages. Pure, so it can be tested without notifications. */
object KakaoNotificationParser {
    /** One message of a messaging-style notification: who, what, when. */
    class Line(val sender: String?, val text: String, val timeMillis: Long)

    private val GROUP_LINE = Regex("^(.{1,40}?) : (.+)$", RegexOption.DOT_MATCHES_ALL)

    /**
     * [title] is the person (or the room in a group chat), [text] the preview, [conversationTitle] the room name when the
     * notification has one, [lines] the messages when it is messaging-style. A group chat shows "name : message".
     */
    fun parse(
        title: String?,
        text: String?,
        conversationTitle: String?,
        lines: List<Line>,
        postTime: Long,
    ): List<KakaoMessage> {
        val room = (conversationTitle?.takeIf { it.isNotBlank() } ?: title?.takeIf { it.isNotBlank() } ?: "카카오톡")
        if (lines.isNotEmpty()) {
            return lines.filter { it.text.isNotBlank() }.map {
                KakaoMessage(room, it.sender?.takeIf { s -> s.isNotBlank() } ?: title.orEmpty().ifBlank { room }, it.text.trim(), it.timeMillis)
            }
        }
        val preview = text?.trim().orEmpty()
        if (preview.isEmpty()) return emptyList()
        val group = GROUP_LINE.matchEntire(preview)
        return if (group != null && conversationTitle.isNullOrBlank() && title == room && group.groupValues[1] != room) {
            // "name : message" under the room name: a group chat
            listOf(KakaoMessage(room, group.groupValues[1], group.groupValues[2].trim(), postTime))
        } else {
            listOf(KakaoMessage(room, title?.takeIf { it.isNotBlank() } ?: room, preview, postTime))
        }
    }
}
