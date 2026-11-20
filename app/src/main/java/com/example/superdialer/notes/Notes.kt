package com.example.superdialer.notes

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One free-text note of the Notes section. The first line is its title. */
data class Note(val id: Long, val text: String, val updatedAt: Long, val pinned: Boolean)

/**
 * The notepad ("메모장") of the Apps tab. Kept on this phone only (SharedPreferences, never synced or uploaded). Call [init]
 * once from the Application.
 */
object Notes {
    private const val PREFS = "notes"
    private const val KEY = "notes"
    const val MAX_NOTES = 1000
    const val MAX_LENGTH = 20_000

    private var appContext: Context? = null

    /** Observable by Compose. */
    private val items = mutableStateListOf<Note>()

    fun init(context: Context) {
        appContext = context.applicationContext
        items.clear()
        items.addAll(decode(appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)))
    }

    /** All notes, pinned first and newest first. */
    fun all(): List<Note> = sortForDisplay(items)

    fun get(id: Long): Note? = items.firstOrNull { it.id == id }

    /**
     * Saves [text] as note [id], or as a new note when [id] is null; blank text removes it (or creates nothing).
     * Returns the note's id, or null if nothing is kept.
     */
    fun save(id: Long?, text: String, pinned: Boolean? = null): Long? {
        val result = upsert(items, id, text, pinned, System.currentTimeMillis(), MAX_NOTES)
        replaceAll(result.list)
        return result.id
    }

    fun setPinned(id: Long, pinned: Boolean) {
        val note = get(id) ?: return
        replaceAll(items.map { if (it.id == id) note.copy(pinned = pinned) else it })
    }

    fun delete(id: Long) {
        replaceAll(items.filterNot { it.id == id })
    }

    private fun replaceAll(list: List<Note>) {
        items.clear()
        items.addAll(list)
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, encode(list))?.apply()
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    class UpsertResult(val list: List<Note>, val id: Long?)

    /**
     * [all] with note [id] set to [text] (a new note when [id] is null or unknown). Text is trimmed and cut to [MAX_LENGTH];
     * blank text removes the note. [pinned] null keeps the old pin state (false for a new note). At most [max] notes are
     * kept: the oldest unpinned ones fall away first.
     */
    fun upsert(all: List<Note>, id: Long?, text: String, pinned: Boolean?, now: Long, max: Int): UpsertResult {
        val body = text.trim().take(MAX_LENGTH)
        val old = id?.let { wanted -> all.firstOrNull { it.id == wanted } }
        if (body.isEmpty()) return UpsertResult(all.filterNot { it.id == old?.id }, null)
        val newId = old?.id ?: ((all.maxOfOrNull { it.id } ?: 0L) + 1)
        val note = Note(newId, body, now, pinned ?: old?.pinned ?: false)
        var list = all.filterNot { it.id == newId } + note
        while (list.size > max) {
            val drop = list.filter { !it.pinned && it.id != newId }.minByOrNull { it.updatedAt } ?: break
            list = list - drop
        }
        return UpsertResult(list, newId)
    }

    fun sortForDisplay(list: List<Note>): List<Note> =
        list.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })

    /** The first non-blank line. */
    fun titleOf(text: String): String = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""

    /** What follows the title: the next non-blank lines joined into one line, for the list row. */
    fun previewOf(text: String): String {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        return lines.drop(1).joinToString(" ")
    }

    /** Case-insensitive match on the whole text; a blank [query] matches everything. */
    fun matches(text: String, query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || text.contains(q, ignoreCase = true)
    }

    fun encode(list: List<Note>): String = JSONArray().also { array ->
        list.forEach { array.put(JSONObject().put("i", it.id).put("t", it.updatedAt).put("x", it.text).put("p", it.pinned)) }
    }.toString()

    fun decode(text: String?): List<Note> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(text)
            val seen = HashSet<Long>()
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val body = o.optString("x")
                val id = o.optLong("i", -1)
                if (body.isBlank() || id < 0 || !seen.add(id)) null else Note(id, body, o.optLong("t"), o.optBoolean("p"))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}
