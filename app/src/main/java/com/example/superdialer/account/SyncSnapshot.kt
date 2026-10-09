package com.example.superdialer.account

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Everything that follows the user between phones: speed-dial tiles, bookmarks, game best scores,
 * a few settings, reject-message presets and the checklist site's sync id.
 * Calls, contacts and text messages are never part of it.
 */
data class SyncSnapshot(
    val speedDials: List<Link> = emptyList(),
    val bookmarks: List<Link> = emptyList(),
    val gameScores: Map<String, Int> = emptyMap(),
    val dtmfEnabled: Boolean = true,
    val dynamicColor: Boolean = false,
    val themeMode: String = "system",
    val accent: String = "green",
    val textSize: String = "normal",
    val rejectMessages: List<String> = emptyList(),
    val checklistId: String? = null,
) {
    /** A tile or bookmark. For tiles, [folder] marks a folder and [parent] is the index of its folder in the same list (-1 = top level). */
    data class Link(val title: String, val url: String, val folder: Boolean = false, val parent: Int = -1)

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", VERSION)
        put("speedDials", linksToJson(speedDials))
        put("bookmarks", linksToJson(bookmarks))
        put("gameScores", JSONObject().also { o -> gameScores.toSortedMap().forEach { (k, v) -> o.put(k, v) } })
        put("dtmfEnabled", dtmfEnabled)
        put("dynamicColor", dynamicColor)
        put("themeMode", themeMode)
        put("accent", accent)
        put("textSize", textSize)
        put("rejectMessages", JSONArray(rejectMessages))
        if (checklistId != null) put("checklistId", checklistId)
    }

    /** Stable text form, used to tell whether anything changed since the last sync. */
    fun fingerprint(): String = toJson().toString()

    /** Scores only ever go up: keep the better of this device's and [other]'s for every game. */
    fun withBestScores(other: Map<String, Int>): SyncSnapshot =
        copy(gameScores = (gameScores.keys + other.keys).associateWith { maxOf(gameScores[it] ?: 0, other[it] ?: 0) })

    companion object {
        const val VERSION = 1

        /** Parses what the server stored; missing or broken parts fall back to empty so one bad field never blocks sync. */
        fun fromJson(json: JSONObject): SyncSnapshot = SyncSnapshot(
            speedDials = linksFrom(json.optJSONArray("speedDials")),
            bookmarks = linksFrom(json.optJSONArray("bookmarks")),
            gameScores = scoresFrom(json.optJSONObject("gameScores")),
            dtmfEnabled = json.optBoolean("dtmfEnabled", true),
            dynamicColor = json.optBoolean("dynamicColor", false),
            themeMode = json.optString("themeMode", "system"),
            accent = json.optString("accent", "green"),
            textSize = json.optString("textSize", "normal"),
            rejectMessages = stringsFrom(json.optJSONArray("rejectMessages")),
            checklistId = json.optString("checklistId", "").takeIf { isValidChecklistId(it) },
        )

        fun parse(text: String): SyncSnapshot? = try {
            fromJson(JSONObject(text))
        } catch (e: JSONException) {
            null
        }

        /** The checklist site only accepts ids like this; anything else is never injected into a page. */
        fun isValidChecklistId(id: String): Boolean = Regex("^[a-z0-9가-힣_-]{2,32}$").matches(id)

        private fun linksToJson(links: List<Link>) = JSONArray().also { array ->
            links.forEach {
                val o = JSONObject().put("title", it.title).put("url", it.url)
                if (it.folder) o.put("folder", true)
                if (it.parent >= 0) o.put("parent", it.parent)
                array.put(o)
            }
        }

        private fun linksFrom(array: JSONArray?): List<Link> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url")
                val folder = o.optBoolean("folder", false)
                if (url.isBlank() && !folder) null
                else Link(o.optString("title", url), url, folder = folder, parent = o.optInt("parent", -1))
            }
        }

        private fun scoresFrom(o: JSONObject?): Map<String, Int> {
            if (o == null) return emptyMap()
            return o.keys().asSequence().mapNotNull { key ->
                val score = o.optInt(key, 0)
                if (score > 0) key to score else null
            }.toMap()
        }

        private fun stringsFrom(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }
    }
}
