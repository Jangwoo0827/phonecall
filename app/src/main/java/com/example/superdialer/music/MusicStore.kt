package com.example.superdialer.music

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** JSON forms of the models for the on-phone library. Pure (tested in MusicStoreTest). */
object MusicCodec {
    fun track(t: Track) = JSONObject()
        .put("id", t.id).put("title", t.title).put("artist", t.artist).put("artistId", t.artistId)
        .put("album", t.album).put("albumId", t.albumId).put("art", t.artUrl ?: JSONObject.NULL)
        .put("dur", t.durationSec).put("uri", t.uri).put("src", t.source.name).put("x", t.explicit)

    fun track(o: JSONObject): Track? {
        val id = o.optString("id")
        if (id.isEmpty()) return null
        return Track(
            id = id,
            title = o.optString("title"),
            artist = o.optString("artist"),
            artistId = o.optLong("artistId"),
            album = o.optString("album"),
            albumId = o.optLong("albumId"),
            artUrl = if (o.isNull("art")) null else o.optString("art").ifEmpty { null },
            durationSec = o.optInt("dur"),
            uri = o.optString("uri"),
            source = runCatching { TrackSource.valueOf(o.optString("src")) }.getOrDefault(TrackSource.DEEZER),
            explicit = o.optBoolean("x"),
        )
    }

    fun tracks(list: List<Track>) = JSONArray().also { a -> list.forEach { a.put(track(it)) } }

    fun tracks(a: JSONArray?): List<Track> {
        if (a == null) return emptyList()
        val out = ArrayList<Track>()
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { o -> track(o)?.let(out::add) }
        return out
    }

    fun album(a: Album) = JSONObject().put("id", a.id).put("title", a.title).put("artist", a.artist)
        .put("artistId", a.artistId).put("art", a.artUrl ?: JSONObject.NULL).put("year", a.year).put("n", a.trackCount)

    fun album(o: JSONObject): Album? {
        val id = o.optLong("id")
        if (id <= 0) return null
        return Album(id, o.optString("title"), o.optString("artist"), o.optLong("artistId"),
            if (o.isNull("art")) null else o.optString("art"), o.optString("year"), o.optInt("n"))
    }

    fun artist(a: Artist) = JSONObject().put("id", a.id).put("name", a.name).put("pic", a.picUrl ?: JSONObject.NULL).put("fans", a.fans)

    fun artist(o: JSONObject): Artist? {
        val id = o.optLong("id")
        if (id <= 0) return null
        return Artist(id, o.optString("name"), if (o.isNull("pic")) null else o.optString("pic"), o.optInt("fans"))
    }

    fun playlistInfo(p: PlaylistInfo) = JSONObject().put("id", p.id).put("title", p.title).put("owner", p.owner)
        .put("art", p.artUrl ?: JSONObject.NULL).put("n", p.trackCount).put("desc", p.description)

    fun playlistInfo(o: JSONObject): PlaylistInfo? {
        val id = o.optLong("id")
        if (id <= 0) return null
        return PlaylistInfo(id, o.optString("title"), o.optString("owner"),
            if (o.isNull("art")) null else o.optString("art"), o.optInt("n"), o.optString("desc"))
    }

    fun userPlaylist(p: UserPlaylist) = JSONObject().put("id", p.id).put("name", p.name)
        .put("created", p.created).put("tracks", tracks(p.tracks))

    fun userPlaylist(o: JSONObject): UserPlaylist? {
        val id = o.optString("id")
        if (id.isEmpty()) return null
        return UserPlaylist(id, o.optString("name"), tracks(o.optJSONArray("tracks")), o.optLong("created"))
    }

    fun <T> list(a: JSONArray?, parse: (JSONObject) -> T?): List<T> {
        if (a == null) return emptyList()
        val out = ArrayList<T>()
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { o -> parse(o)?.let(out::add) }
        return out
    }

    fun <T> array(items: List<T>, make: (T) -> JSONObject) = JSONArray().also { a -> items.forEach { a.put(make(it)) } }
}

/**
 * The user's music library, kept on this phone only (SharedPreferences "music_library"): liked songs, own playlists,
 * saved albums/playlists, followed artists, recently played songs and recent searches. Compose lists, so screens
 * update as soon as something changes.
 */
object MusicStore {
    const val MAX_RECENTS = 40
    const val MAX_SEARCHES = 12

    val liked = mutableStateListOf<Track>()
    val playlists = mutableStateListOf<UserPlaylist>()
    val savedAlbums = mutableStateListOf<Album>()
    val followedArtists = mutableStateListOf<Artist>()
    val savedPlaylists = mutableStateListOf<PlaylistInfo>()
    val recents = mutableStateListOf<Track>()
    val searches = mutableStateListOf<String>()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("music_library", Context.MODE_PRIVATE)
        prefs = p
        fun arr(key: String) = runCatching { JSONArray(p.getString(key, "[]")) }.getOrDefault(JSONArray())
        liked.clear(); liked.addAll(MusicCodec.tracks(arr("liked")))
        recents.clear(); recents.addAll(MusicCodec.tracks(arr("recents")))
        playlists.clear(); playlists.addAll(MusicCodec.list(arr("playlists")) { MusicCodec.userPlaylist(it) })
        savedAlbums.clear(); savedAlbums.addAll(MusicCodec.list(arr("albums")) { MusicCodec.album(it) })
        followedArtists.clear(); followedArtists.addAll(MusicCodec.list(arr("artists")) { MusicCodec.artist(it) })
        savedPlaylists.clear(); savedPlaylists.addAll(MusicCodec.list(arr("savedPlaylists")) { MusicCodec.playlistInfo(it) })
        searches.clear()
        val s = arr("searches")
        for (i in 0 until s.length()) s.optString(i).takeIf { it.isNotBlank() }?.let(searches::add)
    }

    private fun save() {
        prefs?.edit()
            ?.putString("liked", MusicCodec.tracks(liked.toList()).toString())
            ?.putString("recents", MusicCodec.tracks(recents.toList()).toString())
            ?.putString("playlists", MusicCodec.array(playlists.toList()) { MusicCodec.userPlaylist(it) }.toString())
            ?.putString("albums", MusicCodec.array(savedAlbums.toList()) { MusicCodec.album(it) }.toString())
            ?.putString("artists", MusicCodec.array(followedArtists.toList()) { MusicCodec.artist(it) }.toString())
            ?.putString("savedPlaylists", MusicCodec.array(savedPlaylists.toList()) { MusicCodec.playlistInfo(it) }.toString())
            ?.putString("searches", JSONArray(searches.toList()).toString())
            ?.apply()
    }

    // --- liked songs ----------------------------------------------------------------------------------------------

    fun isLiked(track: Track): Boolean = liked.any { it.id == track.id }

    /** Returns true when the song is liked afterwards. */
    fun toggleLike(track: Track): Boolean {
        val i = liked.indexOfFirst { it.id == track.id }
        val nowLiked = i < 0
        if (nowLiked) liked.add(0, track) else liked.removeAt(i)
        save()
        return nowLiked
    }

    // --- own playlists --------------------------------------------------------------------------------------------

    fun createPlaylist(name: String, first: List<Track> = emptyList()): UserPlaylist {
        val p = UserPlaylist(UUID.randomUUID().toString(), name.trim().ifEmpty { "Playlist" }, first.distinctBy { it.id }, System.currentTimeMillis())
        playlists.add(0, p)
        save()
        return p
    }

    fun playlist(id: String): UserPlaylist? = playlists.firstOrNull { it.id == id }

    fun renamePlaylist(id: String, name: String) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i < 0 || name.isBlank()) return
        playlists[i] = playlists[i].copy(name = name.trim())
        save()
    }

    fun deletePlaylist(id: String) {
        playlists.removeAll { it.id == id }
        save()
    }

    /** Adds songs that are not in the playlist yet; returns how many were added. */
    fun addToPlaylist(id: String, tracks: List<Track>): Int {
        val i = playlists.indexOfFirst { it.id == id }
        if (i < 0) return 0
        val have = playlists[i].tracks.map { it.id }.toSet()
        val fresh = tracks.filter { it.id !in have }.distinctBy { it.id }
        if (fresh.isEmpty()) return 0
        playlists[i] = playlists[i].copy(tracks = playlists[i].tracks + fresh)
        save()
        return fresh.size
    }

    fun removeFromPlaylist(id: String, trackId: String) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i < 0) return
        playlists[i] = playlists[i].copy(tracks = playlists[i].tracks.filter { it.id != trackId })
        save()
    }

    fun moveInPlaylist(id: String, from: Int, to: Int) {
        val i = playlists.indexOfFirst { it.id == id }
        if (i < 0) return
        val list = playlists[i].tracks.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        playlists[i] = playlists[i].copy(tracks = list)
        save()
    }

    // --- saved albums / playlists, followed artists ----------------------------------------------------------------

    fun isAlbumSaved(id: Long) = savedAlbums.any { it.id == id }

    fun toggleAlbum(album: Album): Boolean {
        val i = savedAlbums.indexOfFirst { it.id == album.id }
        if (i >= 0) savedAlbums.removeAt(i) else savedAlbums.add(0, album)
        save()
        return i < 0
    }

    fun isArtistFollowed(id: Long) = followedArtists.any { it.id == id }

    fun toggleArtist(artist: Artist): Boolean {
        val i = followedArtists.indexOfFirst { it.id == artist.id }
        if (i >= 0) followedArtists.removeAt(i) else followedArtists.add(0, artist)
        save()
        return i < 0
    }

    fun isPlaylistSaved(id: Long) = savedPlaylists.any { it.id == id }

    fun togglePlaylist(info: PlaylistInfo): Boolean {
        val i = savedPlaylists.indexOfFirst { it.id == info.id }
        if (i >= 0) savedPlaylists.removeAt(i) else savedPlaylists.add(0, info)
        save()
        return i < 0
    }

    // --- history ----------------------------------------------------------------------------------------------------

    fun addRecent(track: Track) {
        recents.removeAll { it.id == track.id }
        recents.add(0, track)
        while (recents.size > MAX_RECENTS) recents.removeAt(recents.size - 1)
        save()
    }

    fun addSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        searches.removeAll { it.equals(q, ignoreCase = true) }
        searches.add(0, q)
        while (searches.size > MAX_SEARCHES) searches.removeAt(searches.size - 1)
        save()
    }

    fun removeSearch(query: String) {
        searches.remove(query)
        save()
    }

    fun clearSearches() {
        searches.clear()
        save()
    }

    fun clearRecents() {
        recents.clear()
        save()
    }

    /** Erases everything the Music tab keeps (settings > clear music data). */
    fun clearAll() {
        liked.clear(); playlists.clear(); savedAlbums.clear(); followedArtists.clear(); savedPlaylists.clear()
        recents.clear(); searches.clear()
        save()
    }
}
