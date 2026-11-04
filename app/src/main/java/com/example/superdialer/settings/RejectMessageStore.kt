package com.example.superdialer.settings

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray

/** "Reject with message" presets, user editable. */
object RejectMessageStore {
    private const val PREFS = "reject_messages"
    private const val KEY = "messages"

    private val DEFAULTS = listOf(
        str(R.string.t_i_can_t_talk_right_now),
        str(R.string.t_i_m_in_a_meeting_i),
        str(R.string.t_i_m_driving_i_ll_call),
        str(R.string.t_please_send_me_a_text),
    )

    private var appContext: Context? = null

    /** Observable by Compose. */
    val messages = mutableStateListOf<String>()

    fun init(context: Context) {
        appContext = context.applicationContext
        val saved = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        messages.clear()
        messages.addAll(saved?.let(::parse) ?: DEFAULTS)
    }

    fun add(text: String) {
        val t = text.trim()
        if (t.isNotEmpty()) { messages.add(t); save() }
    }

    fun update(index: Int, text: String) {
        val t = text.trim()
        if (index in messages.indices && t.isNotEmpty()) { messages[index] = t; save() }
    }

    fun remove(index: Int) {
        if (index in messages.indices) { messages.removeAt(index); save() }
    }

    /** Replaces all presets (account sync); an empty list falls back to the defaults. */
    fun replaceAll(texts: List<String>) {
        messages.clear()
        messages.addAll(texts.map(String::trim).filter(String::isNotEmpty).ifEmpty { DEFAULTS })
        save()
    }

    fun resetToDefaults() {
        messages.clear()
        messages.addAll(DEFAULTS)
        save()
    }

    private fun save() {
        val json = JSONArray(messages.toList()).toString()
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY, json)?.apply()
    }

    private fun parse(json: String): List<String> = try {
        val array = JSONArray(json)
        List(array.length()) { array.getString(it) }
    } catch (e: org.json.JSONException) {
        DEFAULTS
    }
}
