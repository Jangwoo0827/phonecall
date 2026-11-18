package com.example.superdialer.music.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.music.Album
import com.example.superdialer.music.AlbumDetail
import com.example.superdialer.music.Artist
import com.example.superdialer.music.ArtistPage
import com.example.superdialer.music.DeezerApi
import com.example.superdialer.music.LocalMusic
import com.example.superdialer.music.MusicPlayer
import com.example.superdialer.music.MusicStore
import com.example.superdialer.music.PlaylistDetail
import com.example.superdialer.music.Track
import kotlinx.coroutines.launch

/** Renders whichever screen is pushed on top of the Music tab. */
@Composable
fun DestScreen(dest: Dest, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        IconButton(onClick = { MusicUi.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, str(R.string.music_back)) }
        when (dest) {
            is Dest.Album -> AlbumScreen(dest.id)
            is Dest.Artist -> ArtistScreen(dest.id)
            is Dest.DeezerPlaylist -> DeezerPlaylistScreen(dest.id)
            is Dest.UserPlaylist -> UserPlaylistScreen(dest.id)
            is Dest.Genre -> GenreScreen(dest.id, dest.name)
            Dest.Liked -> LikedScreen()
            Dest.Local -> LocalScreen()
        }
    }
}

// --- the shared collection page ------------------------------------------------------------------------------------------

@Composable
fun HeartButton(selected: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (selected) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = str(if (selected) R.string.music_unlike else R.string.music_like),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Album / playlist / liked-songs page: cover, title, lines of text, action buttons with the green play button, then
 * the songs. [extra] adds more lists below the songs (an artist's albums, for example).
 */
@Composable
private fun CollectionPage(
    cover: @Composable () -> Unit,
    title: String,
    meta: @Composable () -> Unit,
    tracks: List<Track>,
    actions: @Composable RowScope.() -> Unit,
    numbered: Boolean = false,
    accent: Color? = null,
    emptyText: String? = null,
    playlistId: String? = null,
    loop: Boolean = false,
    firstTracks: Int = Int.MAX_VALUE,
    extra: LazyListScope.() -> Unit = {},
) {
    val current = MusicPlayer.current
    val playingHere = MusicPlayer.isPlaying && current != null && tracks.any { it.id == current.id }
    var showAll by remember { mutableStateOf(false) }
    val shown = if (showAll) tracks else tracks.take(firstTracks)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            val top = accent ?: MaterialTheme.colorScheme.primary
            Column(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(top.copy(alpha = 0.35f), Color.Transparent))).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(4.dp))
                cover()
                Spacer(Modifier.height(12.dp))
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                meta()
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    actions()
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { MusicPlayer.playShuffled(tracks, loop) }, enabled = tracks.isNotEmpty()) {
                        Icon(Icons.Filled.Shuffle, str(R.string.music_shuffle_play))
                    }
                    PlayFab(
                        playing = playingHere,
                        onClick = {
                            if (tracks.isEmpty()) return@PlayFab
                            if (playingHere) MusicPlayer.togglePlay() else MusicPlayer.play(tracks, repeatAll = loop)
                        },
                    )
                }
            }
        }
        if (tracks.isEmpty() && emptyText != null) {
            item {
                Text(emptyText, Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(shown.size, key = { "${shown[it].id}#$it" }) { i ->
            val t = shown[i]
            TrackRow(
                t,
                onClick = { MusicPlayer.play(tracks, tracks.indexOf(t).coerceAtLeast(0), repeatAll = loop) },
                number = if (numbered) i + 1 else null,
                onMenu = { MusicUi.menuPlaylistId = playlistId; MusicUi.menuTrack = t },
            )
        }
        if (tracks.size > firstTracks) {
            item {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(str(if (showAll) R.string.music_show_less else R.string.music_show_more))
                }
            }
        }
        extra()
    }
}

// --- album ----------------------------------------------------------------------------------------------------------------

@Composable
fun AlbumScreen(id: Long) {
    val load = rememberLoad(id) { DeezerApi.album(id) }
    LoadedContent(load) { d -> AlbumPage(d) }
}

@Composable
private fun AlbumPage(d: AlbumDetail) {
    val context = LocalContext.current
    val a = d.album
    val color = rememberArtColor(a.artUrl)
    CollectionPage(
        cover = { Artwork(a.artUrl, Modifier.size(210.dp), px = 500, shape = RoundedCornerShape(8.dp)) },
        title = a.title,
        meta = {
            Text(
                a.artist,
                Modifier.clickable { if (a.artistId > 0) MusicUi.open(Dest.Artist(a.artistId)) },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(str(R.string.music_year_songs, a.year.ifEmpty { "-" }, d.tracks.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        },
        tracks = d.tracks,
        accent = color,
        numbered = true,
        actions = {
            HeartButton(MusicStore.isAlbumSaved(a.id)) {
                val saved = MusicStore.toggleAlbum(a)
                toast(context, if (saved) R.string.music_saved_to_library else R.string.music_removed_from_library)
            }
            IconButton(onClick = { MusicUi.addTracks = d.tracks }) { Icon(Icons.Filled.PlaylistAdd, str(R.string.music_add_to_playlist)) }
        },
    )
}

// --- artist -----------------------------------------------------------------------------------------------------------------

@Composable
fun ArtistScreen(id: Long) {
    val load = rememberLoad(id) { DeezerApi.artist(id) }
    LoadedContent(load) { p -> ArtistPageView(p) }
}

@Composable
private fun ArtistPageView(p: ArtistPage) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val artist = p.artist
    val color = rememberArtColor(artist.picUrl)
    CollectionPage(
        cover = { Artwork(artist.picUrl, Modifier.size(180.dp), px = 500, shape = CircleShape) },
        title = artist.name,
        meta = {
            Text(str(R.string.music_fans_count, com.example.superdialer.music.compactCount(artist.fans)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        },
        tracks = p.top,
        accent = color,
        firstTracks = 5,
        numbered = true,
        actions = {
            val following = MusicStore.isArtistFollowed(artist.id)
            OutlinedButton(onClick = { MusicStore.toggleArtist(artist) }, shape = RoundedCornerShape(20.dp)) {
                Text(str(if (following) R.string.music_following else R.string.music_follow))
            }
            IconButton(onClick = {
                scope.launch {
                    runCatching { DeezerApi.artistRadio(artist.id) }.onSuccess { MusicPlayer.play(it.ifEmpty { p.top }) }
                        .onFailure { Toast.makeText(context, str(R.string.music_loading_failed), Toast.LENGTH_SHORT).show() }
                }
            }) { Icon(Icons.Filled.Radio, str(R.string.music_start_radio)) }
        },
        extra = {
            item { AlbumShelf(str(R.string.music_albums), p.albums, onOpen = { MusicUi.open(Dest.Album(it.id)) }) }
            item { ArtistShelf(str(R.string.music_related_artists), p.related, onOpen = { MusicUi.open(Dest.Artist(it.id)) }) }
        },
    )
}

// --- Deezer playlist ---------------------------------------------------------------------------------------------------------

@Composable
fun DeezerPlaylistScreen(id: Long) {
    val load = rememberLoad(id) { DeezerApi.playlist(id) }
    LoadedContent(load) { d -> DeezerPlaylistPage(d) }
}

@Composable
private fun DeezerPlaylistPage(d: PlaylistDetail) {
    val context = LocalContext.current
    val info = d.info
    val color = rememberArtColor(info.artUrl)
    CollectionPage(
        cover = { Artwork(info.artUrl, Modifier.size(210.dp), px = 500, shape = RoundedCornerShape(8.dp)) },
        title = info.title,
        meta = {
            if (info.description.isNotBlank()) {
                Text(info.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Text(
                str(R.string.music_playlist_by, info.owner.ifEmpty { songsLabel(d.tracks.size) }) + " • " + songsLabel(d.tracks.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        tracks = d.tracks,
        accent = color,
        actions = {
            HeartButton(MusicStore.isPlaylistSaved(info.id)) {
                val saved = MusicStore.togglePlaylist(info)
                toast(context, if (saved) R.string.music_saved_to_library else R.string.music_removed_from_library)
            }
            IconButton(onClick = { MusicUi.addTracks = d.tracks }) { Icon(Icons.Filled.PlaylistAdd, str(R.string.music_add_to_playlist)) }
        },
    )
}

// --- the user's own playlist and liked songs ----------------------------------------------------------------------------------

@Composable
fun UserPlaylistScreen(id: String) {
    val playlist = MusicStore.playlists.firstOrNull { it.id == id }
    if (playlist == null) {
        // Deleted while open: leave the page.
        androidx.compose.runtime.LaunchedEffect(Unit) { MusicUi.back() }
        return
    }
    var menu by remember { mutableStateOf(false) }
    val color = rememberArtColor(playlist.tracks.firstOrNull()?.artUrl)
    CollectionPage(
        cover = { PlaylistCover(playlist, 210.dp, Modifier.clip(RoundedCornerShape(8.dp))) },
        title = playlist.name,
        meta = {
            Text(songsLabel(playlist.tracks.size) + if (playlist.loop) " \u2022 " + str(R.string.music_loop_on) else "", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        },
        tracks = playlist.tracks,
        accent = color,
        emptyText = str(R.string.music_empty_playlist),
        playlistId = playlist.id,
        loop = playlist.loop,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, str(R.string.music_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(str(R.string.music_loop)) },
                        trailingIcon = { if (playlist.loop) Icon(Icons.Filled.Check, str(R.string.music_loop_on)) },
                        onClick = { menu = false; MusicStore.setPlaylistLoop(playlist.id, !playlist.loop) },
                    )
                    DropdownMenuItem(text = { Text(str(R.string.music_add_link)) }, onClick = { menu = false; MusicUi.addLinkOpen = true })
                    DropdownMenuItem(text = { Text(str(R.string.music_rename)) }, onClick = { menu = false; MusicUi.renamePlaylistId = playlist.id })
                    DropdownMenuItem(text = { Text(str(R.string.music_delete_playlist)) }, onClick = { menu = false; MusicUi.deletePlaylistId = playlist.id })
                }
            }
            IconButton(onClick = { MusicUi.tab = 1; MusicUi.stack.clear() }) { Icon(Icons.Filled.Search, str(R.string.music_find_songs)) }
        },
    )
}

@Composable
fun LikedScreen() {
    val tracks = MusicStore.liked.toList()
    CollectionPage(
        cover = { LikedCover(210.dp) },
        title = str(R.string.music_liked_songs),
        meta = { Text(songsLabel(tracks.size), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) },
        tracks = tracks,
        accent = Color(0xFF4505F8),
        emptyText = str(R.string.music_library_empty),
        actions = {},
    )
}

// --- genre ------------------------------------------------------------------------------------------------------------------------

@Composable
fun GenreScreen(id: Long, name: String) {
    val load = rememberLoad(id) { DeezerApi.chart(id) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(name, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            LoadedContent(load) { c ->
                Column {
                    TrackShelf(str(R.string.music_top_songs), c.tracks, onPlay = { MusicPlayer.play(c.tracks, it) })
                    AlbumShelf(str(R.string.music_popular_albums), c.albums, onOpen = { MusicUi.open(Dest.Album(it.id)) })
                    ArtistShelf(str(R.string.music_popular_artists), c.artists, onOpen = { MusicUi.open(Dest.Artist(it.id)) })
                    PlaylistShelf(str(R.string.music_popular_playlists), c.playlists, onOpen = { MusicUi.open(Dest.DeezerPlaylist(it.id)) })
                }
            }
        }
    }
}

// --- music on this phone ------------------------------------------------------------------------------------------------------

@Composable
fun LocalScreen() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LocalMusic.hasPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (!granted) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(str(R.string.music_local_permission), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = { launcher.launch(LocalMusic.permission()) }) { Text(str(R.string.music_grant_access)) }
        }
        return
    }
    val loaded by produceState<List<Track>?>(initialValue = null) { value = LocalMusic.load(context) }
    var query by remember { mutableStateOf("") }
    val all = loaded
    if (all == null) { LoadingBox(); return }
    val tracks = if (query.isBlank()) all else all.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    Column(Modifier.fillMaxSize()) {
        Text(str(R.string.music_on_this_device), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(songsLabel(all.size), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (all.isEmpty()) {
            Text(str(R.string.music_local_empty), Modifier.fillMaxWidth().padding(32.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(str(R.string.music_local_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { MusicPlayer.playShuffled(tracks) }, enabled = tracks.isNotEmpty()) {
                Icon(Icons.Filled.Shuffle, null, Modifier.size(18.dp))
                Text(str(R.string.music_shuffle_play), Modifier.padding(start = 8.dp))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
            items(tracks.size, key = { tracks[it].id }) { i ->
                TrackRow(tracks[i], onClick = { MusicPlayer.play(tracks, i) })
            }
        }
    }
}
