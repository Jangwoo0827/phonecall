package com.example.superdialer.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.music.MusicPlayer
import com.example.superdialer.music.MusicStore
import com.example.superdialer.music.Track

/**
 * Everything the Music feature draws above the app: the full player, and the sheets and dialogs that open from songs.
 * Lives at the root of the app so they work from the Music tab, the mini player and the full player alike.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicOverlays() {
    AnimatedVisibility(
        visible = MusicUi.nowPlayingOpen && MusicPlayer.current != null,
        enter = slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200)),
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { NowPlayingScreen() }
    }

    MusicUi.menuTrack?.let { track ->
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { MusicUi.menuTrack = null; MusicUi.menuPlaylistId = null }, sheetState = state) {
            TrackMenu(track)
        }
    }

    MusicUi.addTracks?.let { tracks ->
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { MusicUi.addTracks = null }, sheetState = state) {
            AddToPlaylistSheet(tracks)
        }
    }

    if (MusicUi.queueOpen) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { MusicUi.queueOpen = false }, sheetState = state) { QueueSheet() }
    }

    if (MusicUi.sleepOpen) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { MusicUi.sleepOpen = false }, sheetState = state) { SleepSheet() }
    }

    MusicUi.createPlaylistWith?.let { initial -> CreatePlaylistDialog(initial) }
    MusicUi.renamePlaylistId?.let { id -> RenamePlaylistDialog(id) }
    MusicUi.deletePlaylistId?.let { id -> DeletePlaylistDialog(id) }
}

@Composable
private fun SheetAction(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.padding(start = 20.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TrackMenu(track: Track) {
    val context = LocalContext.current
    val playlistId = MusicUi.menuPlaylistId
    fun close() { MusicUi.menuTrack = null; MusicUi.menuPlaylistId = null }
    Column(Modifier.padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artUrl, Modifier.size(56.dp), px = 150)
            Column(Modifier.padding(start = 16.dp)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val liked = MusicStore.isLiked(track)
        SheetAction(
            if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            str(if (liked) R.string.music_remove_from_liked else R.string.music_add_to_liked),
        ) { toggleLike(context, track); close() }
        SheetAction(Icons.Filled.PlaylistAdd, str(R.string.music_add_to_playlist)) { close(); MusicUi.addTracks = listOf(track) }
        if (playlistId != null) {
            SheetAction(Icons.Filled.Delete, str(R.string.music_remove_from_playlist)) { MusicStore.removeFromPlaylist(playlistId, track.id); close() }
        }
        SheetAction(Icons.Filled.SkipNext, str(R.string.music_play_next)) { MusicPlayer.playNext(track); close() }
        SheetAction(Icons.Filled.QueueMusic, str(R.string.music_add_to_queue)) { MusicPlayer.addToQueue(track); close() }
        if (track.artistId > 0) {
            SheetAction(Icons.Filled.Person, str(R.string.music_go_to_artist)) { close(); MusicUi.requestOpen(Dest.Artist(track.artistId)) }
        }
        if (!track.isLocal && track.albumId > 0) {
            SheetAction(Icons.Filled.Album, str(R.string.music_go_to_album)) { close(); MusicUi.requestOpen(Dest.Album(track.albumId)) }
        }
        SheetAction(Icons.Filled.Share, str(R.string.music_share)) { shareTrack(context, track); close() }
        SheetAction(Icons.Filled.OpenInNew, str(R.string.music_open_in_spotify)) { openInSpotify(context, track); close() }
    }
}

@Composable
private fun AddToPlaylistSheet(tracks: List<Track>) {
    val context = LocalContext.current
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(str(R.string.music_add_to_playlist), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        SheetAction(Icons.Filled.Add, str(R.string.music_new_playlist)) { MusicUi.addTracks = null; MusicUi.createPlaylistWith = tracks }
        LazyColumn {
            itemsIndexed(MusicStore.playlists.toList(), key = { _, p -> p.id }) { _, p ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val added = MusicStore.addToPlaylist(p.id, tracks)
                        toast(context, if (added > 0) R.string.music_added_to_playlist else R.string.music_already_in_playlist, p.name)
                        MusicUi.addTracks = null
                    }.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaylistCover(p, 48.dp)
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text(songsLabel(p.tracks.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSheet() {
    val queue = MusicPlayer.queue
    val index = MusicPlayer.currentIndex
    Column(Modifier.padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(str(R.string.music_queue), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (queue.size > index + 1) TextButton(onClick = { MusicPlayer.clearUpcoming() }) { Text(str(R.string.music_clear_queue)) }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            if (index in queue.indices) {
                item { Text(str(R.string.music_now_playing), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
                item { TrackRow(queue[index], onClick = {}, showMenu = false) }
            }
            item { Text(str(R.string.music_next_up), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) }
            if (queue.size <= index + 1) {
                item { Text(str(R.string.music_queue_empty), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            itemsIndexed(queue, key = { i, t -> "$i-${t.id}" }) { i, t ->
                if (i > index) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackRow(t, onClick = { MusicPlayer.skipTo(i) }, modifier = Modifier.weight(1f), showMenu = false)
                        IconButton(onClick = { MusicPlayer.removeFromQueue(i) }) { Icon(Icons.Filled.Close, str(R.string.music_delete)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepSheet() {
    fun close() { MusicUi.sleepOpen = false }
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(str(R.string.music_sleep_timer), Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val active = MusicPlayer.sleepEndsAt > 0L || MusicPlayer.sleepAfterTrack
        if (active) {
            val text = if (MusicPlayer.sleepAfterTrack) str(R.string.music_sleep_after_track_active) else {
                val left = ((MusicPlayer.sleepEndsAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) + 1
                str(R.string.music_sleep_active, str(R.string.music_sleep_minutes, left.toInt()))
            }
            Text(text, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.primary)
            SheetAction(Icons.Filled.Close, str(R.string.music_sleep_off)) { MusicPlayer.cancelSleepTimer(); close() }
        }
        listOf(5, 15, 30, 45, 60).forEach { m ->
            Text(
                str(R.string.music_sleep_minutes, m),
                Modifier.fillMaxWidth().clickable { MusicPlayer.sleepInMinutes(m); close() }.padding(horizontal = 24.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(
            str(R.string.music_sleep_end_of_track),
            Modifier.fillMaxWidth().clickable { MusicPlayer.sleepAfterCurrentTrack(); close() }.padding(horizontal = 24.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CreatePlaylistDialog(initial: List<Track>) {
    val context = LocalContext.current
    val suggested = str(R.string.music_my_playlist, MusicStore.playlists.size + 1)
    var name by remember { mutableStateOf(suggested) }
    fun close() { MusicUi.createPlaylistWith = null }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(str(R.string.music_create_playlist)) },
        text = {
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(str(R.string.music_playlist_name)) }, shape = RoundedCornerShape(12.dp))
        },
        confirmButton = {
            TextButton(onClick = {
                val p = MusicStore.createPlaylist(name.ifBlank { suggested }, initial)
                if (initial.isNotEmpty()) toast(context, R.string.music_added_to_playlist, p.name) else MusicUi.open(Dest.UserPlaylist(p.id))
                close()
            }) { Text(str(R.string.music_create)) }
        },
        dismissButton = { TextButton(onClick = ::close) { Text(str(R.string.cancel)) } },
    )
}

@Composable
private fun RenamePlaylistDialog(id: String) {
    val current = MusicStore.playlist(id)
    var name by remember(id) { mutableStateOf(current?.name.orEmpty()) }
    fun close() { MusicUi.renamePlaylistId = null }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(str(R.string.music_rename)) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(str(R.string.music_playlist_name)) }, shape = RoundedCornerShape(12.dp)) },
        confirmButton = { TextButton(onClick = { MusicStore.renamePlaylist(id, name); close() }) { Text(str(R.string.music_save)) } },
        dismissButton = { TextButton(onClick = ::close) { Text(str(R.string.cancel)) } },
    )
}

@Composable
private fun DeletePlaylistDialog(id: String) {
    val p = MusicStore.playlist(id)
    fun close() { MusicUi.deletePlaylistId = null }
    if (p == null) { close(); return }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(str(R.string.music_delete_playlist)) },
        text = { Text(str(R.string.music_delete_confirm, p.name)) },
        confirmButton = { TextButton(onClick = { MusicStore.deletePlaylist(id); close() }) { Text(str(R.string.music_delete)) } },
        dismissButton = { TextButton(onClick = ::close) { Text(str(R.string.cancel)) } },
    )
}
