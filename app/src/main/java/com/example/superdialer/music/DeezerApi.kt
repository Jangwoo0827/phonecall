package com.example.superdialer.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Deezer's public catalog API (no account or key): charts, search, artists, albums, playlists and 30-second previews.
 * Answers are cached in memory for a few minutes so moving between screens does not hit the network again.
 */
object DeezerApi {
    private const val BASE = "https://api.deezer.com"
    private const val CACHE_MS = 10 * 60_000L
    private const val PREVIEW_CACHE_MS = 20 * 60_000L

    private class Cached(val at: Long, val json: JSONObject)

    private val cache = HashMap<String, Cached>()
    private val previews = HashMap<Long, Pair<Long, String>>()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Blocking GET + parse; callers wrap it in a coroutine on the IO dispatcher. */
    private fun getBlocking(path: String, useCache: Boolean = true): JSONObject {
        val now = System.currentTimeMillis()
        if (useCache) synchronized(cache) { cache[path]?.takeIf { now - it.at < CACHE_MS }?.let { return it.json } }
        var last: IOException? = null
        repeat(2) {
            try {
                val conn = URL(BASE + path).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "SuperDialer")
                conn.setRequestProperty("Accept", "application/json")
                try {
                    if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    DeezerJson.errorMessage(json)?.let { throw IOException(it) }
                    if (useCache) synchronized(cache) {
                        if (cache.size > 120) cache.clear()
                        cache[path] = Cached(now, json)
                    }
                    return json
                } finally {
                    conn.disconnect()
                }
            } catch (e: IOException) {
                last = e
            } catch (e: org.json.JSONException) {
                last = IOException("bad answer", e)
            }
        }
        throw last ?: IOException("network")
    }

    private suspend fun get(path: String, useCache: Boolean = true): JSONObject = withContext(Dispatchers.IO) { getBlocking(path, useCache) }

    suspend fun chart(genreId: Long = 0): Chart = DeezerJson.chart(get(if (genreId == 0L) "/chart" else "/chart/$genreId"))

    suspend fun genres(): List<Genre> = DeezerJson.genres(get("/genre")).filter { it.id != 0L }

    suspend fun search(query: String): SearchResults = coroutineScope {
        val q = enc(query.trim())
        val t = async { DeezerJson.tracks(DeezerJson.data(get("/search?q=$q&limit=30"))) }
        val a = async { DeezerJson.albums(DeezerJson.data(get("/search/album?q=$q&limit=20"))) }
        val ar = async { DeezerJson.artists(DeezerJson.data(get("/search/artist?q=$q&limit=20"))) }
        val p = async { DeezerJson.playlists(DeezerJson.data(get("/search/playlist?q=$q&limit=20"))) }
        SearchResults(t.await(), a.await(), ar.await(), p.await())
    }

    suspend fun album(id: Long): AlbumDetail =
        DeezerJson.albumDetail(get("/album/$id")) ?: throw IOException("no album")

    suspend fun playlist(id: Long): PlaylistDetail =
        DeezerJson.playlistDetail(get("/playlist/$id")) ?: throw IOException("no playlist")

    suspend fun artist(id: Long): ArtistPage = coroutineScope {
        val info = async { DeezerJson.artist(get("/artist/$id")) ?: throw IOException("no artist") }
        val top = async { DeezerJson.tracks(DeezerJson.data(get("/artist/$id/top?limit=20"))) }
        val albums = async { DeezerJson.albums(DeezerJson.data(get("/artist/$id/albums?limit=40"))) }
        val related = async { runCatching { DeezerJson.artists(DeezerJson.data(get("/artist/$id/related?limit=12"))) }.getOrDefault(emptyList()) }
        ArtistPage(info.await(), top.await(), albums.await(), related.await())
    }

    /** An endless-style mix of songs like this artist's. */
    suspend fun artistRadio(id: Long): List<Track> = DeezerJson.tracks(DeezerJson.data(get("/artist/$id/radio?limit=40", useCache = false)))

    /** One track with its current preview link (used to refresh a saved track). */
    suspend fun track(id: Long): Track? = DeezerJson.track(get("/track/$id"))

    /**
     * Preview links expire after a while, so tracks keep only their id and the player asks for the link when it opens
     * the track. Called on the player's loading thread, hence blocking.
     */
    fun freshPreviewBlocking(id: Long): String? {
        val now = System.currentTimeMillis()
        synchronized(previews) { previews[id]?.takeIf { now - it.first < PREVIEW_CACHE_MS }?.let { return it.second } }
        val url = getBlocking("/track/$id", useCache = false).optString("preview", "")
        if (url.isBlank()) return null
        synchronized(previews) { previews[id] = now to url }
        return url
    }
}
