package com.example.superdialer.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.music.MusicStore
import com.example.superdialer.music.UserPlaylist

/** Your Library: liked songs, music on this phone, your playlists, and the albums / playlists / artists you saved. */
@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(R.string.music_tab_library, R.string.music_filter_playlists, R.string.music_filter_albums, R.string.music_filter_artists)
    Column(modifier.fillMaxSize()) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(labels.size) { i ->
                FilterChip(
                    selected = filter == i,
                    onClick = { filter = i },
                    label = { Text(if (i == 0) str(R.string.music_filter_all) else str(labels[i])) },
                )
            }
        }
        LazyColumn(state = MusicUi.libraryList, contentPadding = PaddingValues(bottom = 24.dp)) {
            val showPlaylists = filter == 0 || filter == 1
            if (showPlaylists) {
                item {
                    LibraryRow(
                        title = str(R.string.music_create_playlist),
                        subtitle = null,
                        onClick = { MusicUi.createPlaylistWith = emptyList() },
                        cover = {
                            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, null)
                            }
                        },
                    )
                }
                item {
                    LibraryRow(
                        title = str(R.string.music_add_link),
                        subtitle = null,
                        onClick = { MusicUi.addLinkOpen = true },
                        cover = {
                            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Link, null)
                            }
                        },
                    )
                }
                item {
                    LibraryRow(
                        title = str(R.string.music_liked_songs),
                        subtitle = songsLabel(MusicStore.liked.size),
                        onClick = { MusicUi.open(Dest.Liked) },
                        cover = { LikedCover(56.dp) },
                    )
                }
                item {
                    LibraryRow(
                        title = str(R.string.music_on_this_device),
                        subtitle = null,
                        onClick = { MusicUi.open(Dest.Local) },
                        cover = {
                            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.PhoneAndroid, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        },
                    )
                }
                items(MusicStore.playlists.toList(), key = { "u${it.id}" }) { p ->
                    LibraryRow(
                        title = p.name,
                        subtitle = str(R.string.music_playlist_by, songsLabel(p.tracks.size)) + if (p.loop) " \u2022 \u221E" else "",
                        onClick = { MusicUi.open(Dest.UserPlaylist(p.id)) },
                        cover = { PlaylistCover(p, 56.dp) },
                    )
                }
                items(MusicStore.savedPlaylists.toList(), key = { "s${it.id}" }) { p ->
                    LibraryRow(
                        title = p.title,
                        subtitle = str(R.string.music_playlist_by, p.owner.ifEmpty { songsLabel(p.trackCount) }),
                        onClick = { MusicUi.open(Dest.DeezerPlaylist(p.id)) },
                        cover = { Artwork(p.artUrl, Modifier.size(56.dp), px = 150) },
                    )
                }
            }
            if (filter == 0 || filter == 2) {
                items(MusicStore.savedAlbums.toList(), key = { "a${it.id}" }) { a ->
                    LibraryRow(
                        title = a.title,
                        subtitle = str(R.string.music_album_by, a.artist),
                        onClick = { MusicUi.open(Dest.Album(a.id)) },
                        cover = { Artwork(a.artUrl, Modifier.size(56.dp), px = 150) },
                    )
                }
            }
            if (filter == 0 || filter == 3) {
                items(MusicStore.followedArtists.toList(), key = { "r${it.id}" }) { a ->
                    LibraryRow(
                        title = a.name,
                        subtitle = str(R.string.music_artist_label),
                        onClick = { MusicUi.open(Dest.Artist(a.id)) },
                        cover = { Artwork(a.picUrl, Modifier.size(56.dp), px = 150, shape = CircleShape) },
                    )
                }
            }
            val empty = MusicStore.playlists.isEmpty() && MusicStore.savedAlbums.isEmpty() && MusicStore.followedArtists.isEmpty() &&
                MusicStore.savedPlaylists.isEmpty() && MusicStore.liked.isEmpty()
            if (empty) {
                item {
                    Text(
                        str(R.string.music_library_empty),
                        Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(title: String, subtitle: String?, onClick: () -> Unit, cover: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        cover()
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The purple heart tile of "Liked Songs". */
@Composable
fun LikedCover(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(Brush.linearGradient(listOf(Color(0xFF4505F8), Color(0xFFB9C6E8))), RoundedCornerShape(if (size > 100.dp) 8.dp else 6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(size * 0.45f))
    }
}

/** A playlist's cover: the first song's picture, or a 2x2 mosaic of the first four. */
@Composable
fun PlaylistCover(playlist: UserPlaylist, size: Dp, modifier: Modifier = Modifier) {
    val arts = playlist.tracks.mapNotNull { it.artUrl }.distinct().take(4)
    when {
        arts.size >= 4 -> Column(modifier.size(size)) {
            for (r in 0..1) Row {
                for (c in 0..1) Artwork(arts[r * 2 + c], Modifier.size(size / 2), px = 150, shape = RoundedCornerShape(0.dp))
            }
        }
        arts.isNotEmpty() -> Artwork(arts[0], modifier.size(size), px = 250)
        else -> Artwork(null, modifier.size(size), px = 150)
    }
}
