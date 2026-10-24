package com.example.superdialer.contacts

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.example.superdialer.calllog.numberKey
import org.json.JSONException
import org.json.JSONObject

/**
 * A free-text memo about a contact ("연락처 메모"), shown on the contact's detail screen. The phone's contacts have no
 * field the app may write without extra permission, so the memos live here, keyed by phone number (the same +82/010
 * normalisation as everywhere else) so they survive contacts being merged or re-created. Kept on this phone only
 * (never synced). Call [init] once from the Application.
 */
object ContactMemos {
    private const val PREFS = "contact_memos"
    private const val KEY = "memos"
    private const val MAX_MEMOS = 500

    private var appContext: Context? = null

    /** number key -> memo; observable by Compose. */
    private val memos = mutableStateMapOf<String, String>()

    fun init(context: Context) {
        appContext = context.applicationContext
        memos.clear()
        memos.putAll(decode(appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)))
    }

    /** The memo of a contact with these phone [numbers] (raw, as stored in the contact), or "" if there is none. */
    fun forNumbers(numbers: List<String>): String = find(memos, numbers.map { numberKey(it) })

    /** Saves [text] for the contact with these [numbers]; blank removes the memo. */
    fun setFor(numbers: List<String>, text: String) {
        val updated = store(memos, numbers.map { numberKey(it) }, text, MAX_MEMOS)
        memos.clear()
        memos.putAll(updated)
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, encode(updated))?.apply()
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    /** The first non-blank memo among the contact's number [keys] (a contact can have several numbers). */
    fun find(all: Map<String, String>, keys: List<String>): String =
        keys.firstNotNullOfOrNull { k -> all[k]?.takeIf { it.isNotBlank() } } ?: ""

    /**
     * [all] with the memo of a contact whose numbers are [keys] replaced by [text]. The memo is stored under the first
     * number only and every other number of that contact loses an older memo, so one contact never has two. Blank [text]
     * removes it. At most [max] memos are kept (the oldest entries fall away).
     */
    fun store(all: Map<String, String>, keys: List<String>, text: String, max: Int): Map<String, String> {
        val real = keys.filter { it.isNotEmpty() }
        if (real.isEmpty()) return all
        val result = LinkedHashMap(all)
        real.forEach { result.remove(it) }
        val body = text.trim()
        if (body.isNotEmpty()) result[real.first()] = body
        while (result.size > max) result.remove(result.keys.first())
        return result
    }

    fun encode(all: Map<String, String>): String = JSONObject().also { o -> all.forEach { (k, v) -> o.put(k, v) } }.toString()

    fun decode(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(text)
            val out = LinkedHashMap<String, String>()
            o.keys().forEach { k ->
                val v = o.optString(k)
                if (k.isNotBlank() && v.isNotBlank()) out[k] = v
            }
            out
        } catch (e: JSONException) {
            emptyMap()
        }
    }
}
