package com.example.superdialer.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.net.HttpURLConnection
import java.net.URL

/** The change list of one released version. [date] is "2026.10.24" ("" when unknown). */
data class ReleaseEntry(val version: String, val date: String, val lines: List<String>)

/**
 * The patch notes of every release ("패치노트"), taken from the GitHub releases of this app. The last answer is kept on the
 * phone so the list also opens without a network.
 */
object ReleaseNotes {
    private const val LIST_URL = "https://api.github.com/repos/Jangwoo0827/phonecall/releases?per_page=100"
    private const val PREFS = "release_notes"
    private const val KEY_JSON = "json"
    private const val MAX_LINES = 40

    /** What was fetched last time (empty before the first successful fetch). */
    fun cached(context: Context): List<ReleaseEntry> =
        parse(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_JSON, null))

    /** Fetches the list; null when there is no network or GitHub does not answer. A good answer is stored for offline use. */
    suspend fun refresh(context: Context): List<ReleaseEntry>? = withContext(Dispatchers.IO) {
        val connection = runCatching { URL(LIST_URL).openConnection() as HttpURLConnection }.getOrNull() ?: return@withContext null
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "SuperDialer")
            if (connection.responseCode != 200) return@withContext null
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val entries = parse(body)
            if (entries.isEmpty()) return@withContext null
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_JSON, body).apply()
            entries
        } catch (e: java.io.IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    /** Newest first, as GitHub lists them; drafts and pre-releases are left out. Broken input gives an empty list. */
    fun parse(json: String?): List<ReleaseEntry> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                if (o.optBoolean("draft") || o.optBoolean("prerelease")) return@mapNotNull null
                val tag = o.optString("tag_name").removePrefix("v").trim()
                if (tag.isEmpty()) return@mapNotNull null
                ReleaseEntry(
                    version = tag,
                    date = formatDate(o.optString("published_at")),
                    lines = WhatsNew.cleanNotes(o.optString("body"), MAX_LINES),
                )
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** "2026-10-24T08:15:00Z" -> "2026.10.24"; anything else -> "". */
    fun formatDate(iso: String): String {
        val day = iso.take(10)
        return if (Regex("""\d{4}-\d{2}-\d{2}""").matches(day)) day.replace('-', '.') else ""
    }
}
