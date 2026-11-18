package com.example.superdialer.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * YouTube links as songs of the library: the user finds a song on YouTube (in the app's browser) or pastes a link, and
 * it is saved as a "link song". Title, channel and picture come from YouTube's public oEmbed endpoint (no key). A link
 * song is played by opening the video in the app's own browser, i.e. with YouTube's official player; it is not played
 * by the music player (that would mean pulling the audio out of YouTube, which its terms do not allow).
 */
object YoutubeLinks {
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")

    private val watchHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com")

    /** The 11-character video id of a YouTube link (watch, youtu.be, shorts, embed, live), or null. */
    fun videoId(link: String): String? {
        val text = link.trim()
        val uri = runCatching { java.net.URI(if ("://" in text) text else "https://$text") }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val id = when {
            host == "youtu.be" -> uri.path?.trim('/')?.substringBefore('/')
            host in watchHosts -> {
                val path = uri.path.orEmpty()
                when {
                    path == "/watch" -> uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                    Regex("^/(shorts|embed|live|v)/").containsMatchIn(path) -> path.split('/').getOrNull(2)
                    else -> null
                }
            }
            else -> null
        }
        return id?.takeIf { idPattern.matches(it) }
    }

    fun isVideoLink(link: String): Boolean = videoId(link) != null

    fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"

    fun searchUrl(query: String) = "https://m.youtube.com/results?search_query=" + URLEncoder.encode(query.trim(), "UTF-8")

    /** The saved song for a video, from the oEmbed answer. */
    fun fromOEmbed(id: String, json: JSONObject): Track = Track(
        id = "yt:$id",
        title = json.optString("title").ifBlank { watchUrl(id) },
        artist = json.optString("author_name").ifBlank { "YouTube" },
        artistId = 0L,
        album = "",
        albumId = 0L,
        artUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        durationSec = 0,
        uri = watchUrl(id),
        source = TrackSource.LINK,
        link = watchUrl(id),
    )

    /** Looks the video up on YouTube and returns it as a link song; null for a link that is not a (public) video. */
    suspend fun fetch(link: String): Track? {
        val id = videoId(link) ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val api = "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(watchUrl(id), "UTF-8")
                val conn = URL(api).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                try {
                    if (conn.responseCode !in 200..299) return@withContext null
                    fromOEmbed(id, JSONObject(conn.inputStream.bufferedReader().use { it.readText() }))
                } finally {
                    conn.disconnect()
                }
            } catch (e: IOException) {
                null
            } catch (e: org.json.JSONException) {
                null
            }
        }
    }
}
