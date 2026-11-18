package com.example.superdialer.music

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Audius (audius.co): a free, open music platform whose public API streams the **full-length** songs of the artists who
 * upload there (no account or key). It is the source of the app's full songs; Deezer, whose catalog is much bigger,
 * only allows 30-second previews. Pure parsing lives in [AudiusJson] (tested in AudiusJsonTest).
 */
object AudiusJson {
    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

    const val STREAM_BASE = "https://api.audius.co/v1/tracks/"
    const val APP_NAME = "SuperDialer"

    /** The URL the player opens; it answers with a redirect to the node that holds the audio. */
    fun streamUrl(id: String) = "$STREAM_BASE$id/stream?app_name=$APP_NAME"

    fun track(o: JSONObject?): Track? {
        if (o == null) return null
        val id = o.str("id")
        if (id.isEmpty()) return null
        // Tracks the artist has gated (paid / followers only) cannot be streamed by everyone.
        if (o.has("is_streamable") && !o.optBoolean("is_streamable", true)) return null
        o.optJSONObject("access")?.let { if (!it.optBoolean("stream", true)) return null }
        val duration = o.optInt("duration")
        if (duration <= 0) return null
        val art = o.optJSONObject("artwork")
        val cover = listOf("480x480", "1000x1000", "150x150").map { art?.str(it).orEmpty() }.firstOrNull { it.startsWith("http") }
        val user = o.optJSONObject("user")
        return Track(
            id = "au:$id",
            title = o.str("title"),
            artist = user?.str("name").orEmpty().ifEmpty { "Audius" },
            artistId = 0L,
            album = "",
            albumId = 0L,
            artUrl = cover,
            durationSec = duration,
            uri = streamUrl(id),
            source = TrackSource.AUDIUS,
            explicit = false,
            link = o.str("permalink").takeIf { it.isNotEmpty() }?.let { "https://audius.co$it" },
        )
    }

    fun tracks(root: JSONObject): List<Track> {
        val arr: JSONArray = root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<Track>(arr.length())
        for (i in 0 until arr.length()) track(arr.optJSONObject(i))?.let(out::add)
        return out
    }

    private fun normalize(s: String): String =
        s.lowercase(Locale.ROOT).replace(Regex("\\(.*?\\)|\\[.*?]"), " ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** [full] (Audius audio) dressed with the display details of [wanted] (the Deezer song it stands in for). */
    fun asFullVersionOf(wanted: Track, full: Track): Track = full.copy(
        title = wanted.title,
        artist = wanted.artist,
        artistId = wanted.artistId,
        album = wanted.album,
        albumId = wanted.albumId,
        artUrl = wanted.artUrl ?: full.artUrl,
        explicit = wanted.explicit,
    )

    /**
     * Picks the full version of a Deezer song among Audius search results: the title must match (ignoring brackets such
     * as "(feat. X)" and punctuation), and the artist is preferred when the names agree. Returns null when nothing fits,
     * because a different song would be worse than no answer.
     */
    fun bestMatch(wanted: Track, found: List<Track>): Track? {
        val title = normalize(wanted.title)
        if (title.isEmpty()) return null
        val sameTitle = found.filter { val t = normalize(it.title); t == title || t.startsWith("$title ") }
        if (sameTitle.isEmpty()) return null
        val artist = normalize(wanted.artist)
        return sameTitle.firstOrNull { val a = normalize(it.artist); a.isNotEmpty() && (a.contains(artist) || artist.contains(a)) }
            ?: sameTitle.firstOrNull { normalize(it.title).contains(artist) }
    }
}

object AudiusApi {
    private const val BASE = "https://api.audius.co/v1"
    private const val CACHE_MS = 10 * 60_000L

    private class Cached(val at: Long, val json: JSONObject)

    private val cache = HashMap<String, Cached>()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun getBlocking(path: String): JSONObject {
        val now = System.currentTimeMillis()
        synchronized(cache) { cache[path]?.takeIf { now - it.at < CACHE_MS }?.let { return it.json } }
        var last: IOException? = null
        repeat(2) {
            try {
                val sep = if (path.contains('?')) '&' else '?'
                val conn = URL("$BASE$path${sep}app_name=${AudiusJson.APP_NAME}").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 12000
                conn.setRequestProperty("Accept", "application/json")
                try {
                    if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    synchronized(cache) {
                        if (cache.size > 60) cache.clear()
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

    private suspend fun get(path: String) = withContext(Dispatchers.IO) { getBlocking(path) }

    suspend fun trending(limit: Int = 25): List<Track> = AudiusJson.tracks(get("/tracks/trending?limit=$limit"))

    suspend fun search(query: String, limit: Int = 30): List<Track> =
        AudiusJson.tracks(get("/tracks/search?query=${enc(query.trim())}&limit=$limit"))

    /**
     * The full-length version of [track], or null when Audius does not have it. The audio is Audius's, but the song keeps
     * the title, artist and cover of the one it replaces (uploads often have long file-name-like titles).
     */
    suspend fun fullVersionOf(track: Track): Track? {
        val found = search("${track.title} ${track.artist}", 10)
        return AudiusJson.bestMatch(track, found)?.let { AudiusJson.asFullVersionOf(track, it) }
    }
}
