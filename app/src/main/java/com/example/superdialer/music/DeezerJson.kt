package com.example.superdialer.music

import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns Deezer's public API JSON into the app's models. Pure functions (no network), covered by DeezerJsonTest.
 * Everything is parsed leniently: a broken entry is skipped instead of failing the whole list.
 */
object DeezerJson {
    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        val out = ArrayList<JSONObject>(length())
        for (i in 0 until length()) optJSONObject(i)?.let { out.add(it) }
        return out
    }

    /** The cover to use for lists: the middle size is sharp enough and light. */
    private fun cover(o: JSONObject?): String? {
        if (o == null) return null
        val url = listOf("cover_medium", "picture_medium", "cover_big", "picture_big", "cover", "picture")
            .map { o.str(it) }.firstOrNull { it.startsWith("http") }
        return url
    }

    fun album(o: JSONObject?): Album? {
        if (o == null) return null
        val id = o.optLong("id")
        if (id <= 0) return null
        val artist = o.optJSONObject("artist")
        return Album(
            id = id,
            title = o.str("title"),
            artist = artist?.str("name").orEmpty(),
            artistId = artist?.optLong("id") ?: 0L,
            artUrl = cover(o),
            year = o.str("release_date").take(4),
            trackCount = o.optInt("nb_tracks"),
        )
    }

    fun artist(o: JSONObject?): Artist? {
        if (o == null) return null
        val id = o.optLong("id")
        if (id <= 0) return null
        return Artist(id, o.str("name"), cover(o), o.optInt("nb_fan"))
    }

    fun playlist(o: JSONObject?): PlaylistInfo? {
        if (o == null) return null
        val id = o.optLong("id")
        if (id <= 0) return null
        val owner = o.optJSONObject("user")?.str("name").orEmpty().ifEmpty { o.optJSONObject("creator")?.str("name").orEmpty() }
        return PlaylistInfo(id, o.str("title"), owner, cover(o), o.optInt("nb_tracks"), o.str("description"))
    }

    /**
     * A track. Album tracklists leave the album out of each track, so the caller passes it in ([album]).
     * Tracks Deezer cannot preview are dropped: they would just fail when played.
     */
    fun track(o: JSONObject?, album: Album? = null): Track? {
        if (o == null) return null
        val id = o.optLong("id")
        if (id <= 0) return null
        if (o.has("preview") && o.str("preview").isBlank()) return null
        val artistObj = o.optJSONObject("artist")
        val albumObj = o.optJSONObject("album")
        val art = cover(albumObj) ?: album?.artUrl ?: cover(artistObj)
        return Track(
            id = "dz:$id",
            title = o.str("title").ifEmpty { o.str("title_short") },
            artist = artistObj?.str("name").orEmpty().ifEmpty { album?.artist.orEmpty() },
            artistId = artistObj?.optLong("id") ?: album?.artistId ?: 0L,
            album = albumObj?.str("title").orEmpty().ifEmpty { album?.title.orEmpty() },
            albumId = albumObj?.optLong("id") ?: album?.id ?: 0L,
            artUrl = art,
            durationSec = o.optInt("duration"),
            uri = "deezer://track/$id",
            source = TrackSource.DEEZER,
            explicit = o.optBoolean("explicit_lyrics"),
        )
    }

    fun tracks(arr: JSONArray?, album: Album? = null): List<Track> = arr.objects().mapNotNull { track(it, album) }
    fun albums(arr: JSONArray?): List<Album> = arr.objects().mapNotNull { album(it) }
    fun artists(arr: JSONArray?): List<Artist> = arr.objects().mapNotNull { artist(it) }
    fun playlists(arr: JSONArray?): List<PlaylistInfo> = arr.objects().mapNotNull { playlist(it) }

    fun genres(root: JSONObject): List<Genre> =
        root.optJSONArray("data").objects().mapNotNull { g ->
            val id = g.optLong("id")
            if (id <= 0) null else Genre(id, g.str("name"), cover(g))
        }

    /** `GET /chart` and `GET /chart/<genre>`: all four lists in one answer. */
    fun chart(root: JSONObject) = Chart(
        tracks = tracks(root.optJSONObject("tracks")?.optJSONArray("data")),
        albums = albums(root.optJSONObject("albums")?.optJSONArray("data")),
        artists = artists(root.optJSONObject("artists")?.optJSONArray("data")),
        playlists = playlists(root.optJSONObject("playlists")?.optJSONArray("data")),
    )

    fun albumDetail(root: JSONObject): AlbumDetail? {
        val album = album(root) ?: return null
        return AlbumDetail(album, tracks(root.optJSONObject("tracks")?.optJSONArray("data"), album))
    }

    fun playlistDetail(root: JSONObject): PlaylistDetail? {
        val info = playlist(root) ?: return null
        return PlaylistDetail(info, tracks(root.optJSONObject("tracks")?.optJSONArray("data")))
    }

    fun data(root: JSONObject): JSONArray? = root.optJSONArray("data")

    /** Deezer reports problems as {"error": {...}} with HTTP 200. */
    fun errorMessage(root: JSONObject): String? =
        root.optJSONObject("error")?.let { it.str("message").ifEmpty { it.str("type") }.ifEmpty { "error" } }
}
