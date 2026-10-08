package com.example.superdialer.settings

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray

/** "Reject with message" presets, user editable. */
object RejectMessageStore {
    private const val PREFS = "reject_messages"
    private const val KEY = "messages"

    private val DEFAULTS = listOf(
        "지금 통화할 수 없습니다. 나중에 연락드리겠습니다.",
        "회의 중입니다. 잠시 후 연락드릴게요.",
        "운전 중입니다. 나중에 전화드리겠습니다.",
        "문자로 말씀해 주세요.",
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
