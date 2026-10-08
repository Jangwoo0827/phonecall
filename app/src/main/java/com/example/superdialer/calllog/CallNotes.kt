package com.example.superdialer.calllog

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A memo about one call. [atMillis] is when it was written during the call, or the call's start for memos added later. */
data class CallNote(val numberKey: String, val atMillis: Long, val text: String)

/**
 * Memos on calls ("통화 중/후 메모"). The system call log has no field for them, so they live here and are matched to a
 * call log entry by number and time. Kept on this phone only (never synced). Call [init] once from the Application.
 */
object CallNotes {
    private const val PREFS = "call_notes"
    private const val KEY = "notes"
    private const val MAX_NOTES = 500

    private var appContext: Context? = null

    /** Observable by Compose. */
    private val notes = mutableStateListOf<CallNote>()

    fun init(context: Context) {
        appContext = context.applicationContext
        notes.clear()
        notes.addAll(decode(appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)))
    }

    fun noteFor(entry: CallLogEntry): CallNote? =
        notes.firstOrNull { matches(it, numberKey(entry.number), entry.dateMillis, entry.durationSeconds) }

    /** Sets, replaces or (blank text) removes the memo of [entry]. */
    fun setFor(entry: CallLogEntry, text: String) {
        val existing = noteFor(entry)
        replace(existing, CallNote(numberKey(entry.number), existing?.atMillis ?: entry.dateMillis, text.trim()))
    }

    /** The memo written during the call that connected at [connectMillis], if any. */
    fun duringCall(number: String, connectMillis: Long): CallNote? =
        notes.lastOrNull { it.numberKey == numberKey(number) && it.atMillis >= connectMillis }

    fun saveDuringCall(number: String, connectMillis: Long, text: String) {
        val existing = duringCall(number, connectMillis)
        replace(existing, CallNote(numberKey(number), existing?.atMillis ?: System.currentTimeMillis(), text.trim()))
    }

    private fun replace(old: CallNote?, new: CallNote) {
        if (old != null) notes.remove(old)
        if (new.text.isNotEmpty() && new.numberKey.isNotEmpty()) notes.add(new)
        while (notes.size > MAX_NOTES) notes.removeAt(0)
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, encode(notes))?.apply()
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    /** Slack after the talk time: a memo can be written while the call is still ringing out or just after it ends. */
    private const val SLACK_MS = 90_000L

    fun matches(note: CallNote, entryKey: String, entryDateMillis: Long, entryDurationSeconds: Long): Boolean =
        entryKey.isNotEmpty() && note.numberKey == entryKey &&
            note.atMillis >= entryDateMillis && note.atMillis <= entryDateMillis + entryDurationSeconds * 1000L + SLACK_MS

    fun encode(list: List<CallNote>): String = JSONArray().also { array ->
        list.forEach { array.put(JSONObject().put("k", it.numberKey).put("t", it.atMillis).put("x", it.text)) }
    }.toString()

    fun decode(text: String?): List<CallNote> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val body = o.optString("x")
                val key = o.optString("k")
                if (body.isBlank() || key.isBlank()) null else CallNote(key, o.optLong("t"), body)
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}
