package com.example.superdialer.music

import java.util.Locale

enum class TrackSource { DEEZER, AUDIUS, LOCAL, LINK }

/**
 * One playable song. [id] is "dz:<deezer id>" or "lc:<MediaStore id>" and is what the queue, likes and playlists key on;
 * [uri] is "deezer://track/<id>" (resolved to a fresh 30-second preview when it plays) or a content:// URI of a file on the phone.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val artUrl: String?,
    val durationSec: Int,
    val uri: String,
    val source: TrackSource,
    val explicit: Boolean = false,
    /** A web page of the song (shared instead of a Deezer link), when the source has one. */
    val link: String? = null,
) {
    val isLocal: Boolean get() = source == TrackSource.LOCAL

    /** A saved web link (YouTube): shown in lists, but opened in the browser instead of being played by the player. */
    val isLink: Boolean get() = source == TrackSource.LINK

    /** Online songs from Deezer are 30-second previews; Audius songs and files on the phone play in full. */
    val isPreview: Boolean get() = source == TrackSource.DEEZER
    val deezerId: Long get() = if (source == TrackSource.DEEZER) id.removePrefix("dz:").toLongOrNull() ?: 0L else 0L
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val artUrl: String?,
    val year: String,
    val trackCount: Int,
)

data class Artist(val id: Long, val name: String, val picUrl: String?, val fans: Int)

data class PlaylistInfo(
    val id: Long,
    val title: String,
    val owner: String,
    val artUrl: String?,
    val trackCount: Int,
    val description: String,
)

data class Genre(val id: Long, val name: String, val picUrl: String?)

data class Chart(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val playlists: List<PlaylistInfo>,
)

data class AlbumDetail(val album: Album, val tracks: List<Track>)

data class ArtistPage(val artist: Artist, val top: List<Track>, val albums: List<Album>, val related: List<Artist>)

data class PlaylistDetail(val info: PlaylistInfo, val tracks: List<Track>)

data class SearchResults(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val playlists: List<PlaylistInfo>,
) {
    val isEmpty: Boolean get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

/** A playlist the user made; lives only on this phone. */
/** A playlist the user made; [loop] = it plays over and over (repeat all) whenever it is started. */
data class UserPlaylist(val id: String, val name: String, val tracks: List<Track>, val created: Long, val loop: Boolean = false)

/** "3:07" / "1:02:09". */
fun formatDuration(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
}

/** 1234 -> "1.2K", 3_400_000 -> "3.4M" (fan counts). */
fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
    n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0).replace(".0K", "K")
    else -> n.toString()
}

/** Deezer artwork URLs end in "/250x250-000000-80-0-0.jpg"; ask for another size by swapping that part. */
fun artAtSize(url: String?, px: Int): String? {
    if (url == null) return null
    return url.replace(Regex("/\\d+x\\d+-"), "/${px}x$px-")
}
