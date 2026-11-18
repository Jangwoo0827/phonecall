package com.example.superdialer.music.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.music.MusicStore
import com.example.superdialer.music.Track
import kotlinx.coroutines.launch

/** A screen pushed on top of the Music tab's Home / Search / Library. */
sealed interface Dest {
    data class Album(val id: Long) : Dest
    data class Artist(val id: Long) : Dest
    data class DeezerPlaylist(val id: Long) : Dest
    data class UserPlaylist(val id: String) : Dest
    data class Genre(val id: Long, val name: String) : Dest
    data object Liked : Dest
    data object Local : Dest
}

/**
 * UI state shared by the Music tab, the mini player and the Now Playing screen (which sits above the whole app):
 * which tab is open, the pushed screens, and the sheets/dialogs that can be opened from anywhere.
 */
object MusicUi {
    var tab by mutableIntStateOf(0)
    val stack = mutableStateListOf<Dest>()

    // Kept here (not in the screens) so Home, Search and Library come back as they were after opening a page and going back.
    val homeList = LazyListState()
    val libraryList = LazyListState()
    var searchQuery by mutableStateOf("")
    var searchSubmitted by mutableStateOf("")
    var searchFilter by mutableIntStateOf(0)

    var nowPlayingOpen by mutableStateOf(false)
    var queueOpen by mutableStateOf(false)
    var sleepOpen by mutableStateOf(false)

    /** The ⋮ menu of a song. */
    var menuTrack by mutableStateOf<Track?>(null)

    /** Set with [menuTrack] when the song is in one of the user's own playlists (adds "remove from this playlist"). */
    var menuPlaylistId by mutableStateOf<String?>(null)

    /** The "add to playlist" chooser; also used for several songs at once (album / playlist menus). */
    var addTracks by mutableStateOf<List<Track>?>(null)


    /** Name dialogs: create (with songs to put in it) and rename. */
    var createPlaylistWith by mutableStateOf<List<Track>?>(null)
    var renamePlaylistId by mutableStateOf<String?>(null)
    var deletePlaylistId by mutableStateOf<String?>(null)

    /** Set by screens outside the Music tab (Now Playing, the notification) to open a screen inside it. */
    var pendingDest by mutableStateOf<Dest?>(null)

    fun open(dest: Dest) {
        if (stack.lastOrNull() != dest) stack.add(dest)
    }

    /** Pops one pushed screen; false when there was none. */
    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Opens [dest] in the Music tab from anywhere in the app (the app scaffold switches to the tab). */
    fun requestOpen(dest: Dest) {
        nowPlayingOpen = false
        queueOpen = false
        pendingDest = dest
    }
}

fun toast(context: Context, resId: Int, vararg args: Any) {
    Toast.makeText(context, str(resId, *args), Toast.LENGTH_SHORT).show()
}

/** Likes or un-likes a song and says so. */
fun toggleLike(context: Context, track: Track) {
    val liked = MusicStore.toggleLike(track)
    toast(context, if (liked) R.string.music_added_to_liked else R.string.music_removed_from_liked)
}

/** Shares a link to the song's Deezer page (or just its name for a file on the phone). */
fun shareTrack(context: Context, track: Track) {
    val link = track.link ?: if (track.source == com.example.superdialer.music.TrackSource.DEEZER) "https://www.deezer.com/track/${track.deezerId}" else null
    val text = "${track.title} - ${track.artist}" + (link?.let { "\n$it" } ?: "")
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Opens a search for the song in the Spotify app, or on the Spotify website when the app is not installed. */
fun openInSpotify(context: Context, track: Track) {
    val q = Uri.encode("${track.title} ${track.artist}")
    val app = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$q")).setPackage("com.spotify.music").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(app) }.recoverCatching { context.startActivity(web) }
}

/**
 * Plays the full-length version of a 30-second preview: looks the song up on Audius (full songs) and, when it is there,
 * replaces the current song with it, or says that there is none.
 */
fun playFullVersion(context: Context, scope: kotlinx.coroutines.CoroutineScope, track: Track) {
    toast(context, R.string.music_looking_for_full)
    scope.launch {
        val full = runCatching { com.example.superdialer.music.AudiusApi.fullVersionOf(track) }.getOrNull()
        if (full != null) com.example.superdialer.music.MusicPlayer.replaceCurrent(full) else toast(context, R.string.music_no_full_version)
    }
}
