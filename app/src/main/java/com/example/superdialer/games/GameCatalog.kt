package com.example.superdialer.games

import android.content.Context
import com.example.superdialer.i18n.Strings
import org.json.JSONArray
import org.json.JSONException

/**
 * One entry of assets/games/games.json. To add a game: put its folder (with an index.html) under
 * assets/games/ and add an entry here — no Kotlin changes needed.
 */
data class GameInfo(
    val id: String,
    val title: String,
    val description: String,
    /** Path of the game's entry file relative to assets/games/, e.g. "2048/index.html". */
    val path: String,
    /** Card accent color as "#RRGGBB". */
    val color: String,
    /** English title and description (games.json "title_en" / "description_en"); empty = use the main ones. */
    val titleEn: String = "",
    val descriptionEn: String = "",
) {
    /** The title in the app's language. */
    val shownTitle: String get() = if (!Strings.isKorean && titleEn.isNotBlank()) titleEn else title

    val shownDescription: String get() = if (!Strings.isKorean && descriptionEn.isNotBlank()) descriptionEn else description
}

object GameCatalog {
    private const val FILE = "games/games.json"

    fun load(context: Context): List<GameInfo> = try {
        parse(context.assets.open(FILE).bufferedReader().use { it.readText() })
    } catch (e: java.io.IOException) {
        emptyList()
    }

    /** Skips malformed entries (and path-traversal attempts) instead of failing the whole list. */
    fun parse(json: String): List<GameInfo> = try {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("id")
                val title = o.optString("title")
                val path = o.optString("path")
                if (id.isBlank() || title.isBlank() || path.isBlank() || path.contains("..") || path.startsWith("/")) continue
                add(GameInfo(id, title, o.optString("description"), path, o.optString("color", "#607D8B"), o.optString("title_en"), o.optString("description_en")))
            }
        }.distinctBy { it.id }
    } catch (e: JSONException) {
        emptyList()
    }
}
