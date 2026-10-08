package com.example.superdialer.dialer

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONException
import org.json.JSONObject

/** A number assigned to a keypad digit ("단축 다이얼"). */
data class QuickDial(val name: String, val number: String)

/**
 * Long-press a digit on the keypad to use its assigned number. Kept on this phone only: it holds phone numbers,
 * which are never synced to the account. Call [init] once from the Application.
 */
object QuickDials {
    private const val PREFS = "quick_dials"
    private const val KEY = "dials"

    private var appContext: Context? = null

    /** Digit '1'..'9' -> assignment. Observable by Compose. */
    val entries = mutableStateMapOf<Char, QuickDial>()

    fun init(context: Context) {
        appContext = context.applicationContext
        val saved = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        entries.clear()
        entries.putAll(decode(saved))
    }

    fun get(digit: Char): QuickDial? = entries[digit]

    fun set(digit: Char, dial: QuickDial) {
        if (digit !in '1'..'9') return
        entries[digit] = dial
        save()
    }

    fun clear(digit: Char) {
        entries.remove(digit)
        save()
    }

    private fun save() {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, encode(entries))?.apply()
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    fun encode(map: Map<Char, QuickDial>): String = JSONObject().also { o ->
        map.toSortedMap().forEach { (digit, dial) -> o.put(digit.toString(), JSONObject().put("name", dial.name).put("number", dial.number)) }
    }.toString()

    fun decode(text: String?): Map<Char, QuickDial> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(text)
            o.keys().asSequence().mapNotNull { key ->
                val digit = key.singleOrNull()?.takeIf { it in '1'..'9' } ?: return@mapNotNull null
                val entry = o.optJSONObject(key) ?: return@mapNotNull null
                val number = entry.optString("number")
                if (number.isBlank()) null else digit to QuickDial(entry.optString("name", number), number)
            }.toMap()
        } catch (e: JSONException) {
            emptyMap()
        }
    }
}
