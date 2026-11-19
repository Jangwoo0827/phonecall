package com.example.superdialer.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.music.AudiusApi
import com.example.superdialer.music.DeezerApi
import com.example.superdialer.music.MusicPlayer
import com.example.superdialer.music.MusicStore
import java.util.Calendar

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return str(
        when {
            hour < 5 -> R.string.music_good_evening
            hour < 12 -> R.string.music_good_morning
            hour < 18 -> R.string.music_good_afternoon
            else -> R.string.music_good_evening
        },
    )
}

/** Home: a greeting, quick-play tiles of what was played last, then the charts and genres from Deezer. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val chart = rememberLoad("chart") { DeezerApi.chart() }
    val genres = rememberLoad("genres") { DeezerApi.genres() }
    val full = rememberLoad("audius-trending") { AudiusApi.trending(25) }
    val valorant = rememberLoad("valorant") { com.example.superdialer.music.ValorantChampions.load() }
    LazyColumn(modifier.fillMaxWidth(), state = MusicUi.homeList, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                greeting(),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                    MusicUi.pendingBrowserUrl = "https://open.spotify.com/"
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).background(Color(0xFF1DB954), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, null, tint = Color.Black)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(str(R.string.music_spotify_web), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(str(R.string.music_spotify_web_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val recents = MusicStore.recents.toList()
        if (recents.isNotEmpty()) {
            item {
                QuickPlayGrid(recents.take(6))
            }
            item {
                TrackShelf(str(R.string.music_recently_played), recents, onPlay = { MusicPlayer.play(recents, it) })
            }
        }
        valorant.value?.takeIf { it.isNotEmpty() }?.let { songs ->
            item { TrackShelf(str(R.string.music_valorant_champions), songs, onPlay = { MusicPlayer.play(songs, it) }) }
        }
        full.value?.takeIf { it.isNotEmpty() }?.let { songs ->
            item { TrackShelf(str(R.string.music_trending_full), songs, onPlay = { MusicPlayer.play(songs, it) }) }
        }
        item {
            LoadedContent(chart) { c ->
                Column {
                    TrackShelf(str(R.string.music_top_songs), c.tracks, onPlay = { MusicPlayer.play(c.tracks, it) })
                    AlbumShelf(str(R.string.music_popular_albums), c.albums, onOpen = { MusicUi.open(Dest.Album(it.id)) })
                    ArtistShelf(str(R.string.music_popular_artists), c.artists, onOpen = { MusicUi.open(Dest.Artist(it.id)) })
                    PlaylistShelf(str(R.string.music_popular_playlists), c.playlists, onOpen = { MusicUi.open(Dest.DeezerPlaylist(it.id)) })
                }
            }
        }
        genres.value?.let { list ->
            item { SectionTitle(str(R.string.music_browse_genres)) }
            item { GenreGrid(list) }
        }
    }
}

/** Two columns of small tiles (picture + title) that start playing the song, like Spotify's home shortcuts. */
@Composable
private fun QuickPlayGrid(tracks: List<com.example.superdialer.music.Track>) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tracks.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { t ->
                    Surface(
                        modifier = Modifier.weight(1f).height(56.dp).clickable { MusicPlayer.play(tracks, tracks.indexOf(t)) },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Artwork(t.artUrl, Modifier.size(56.dp), px = 150, shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                            Text(
                                t.title,
                                Modifier.padding(horizontal = 10.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun GenreGrid(genres: List<com.example.superdialer.music.Genre>) {
    val rows = genres.chunked(2)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEachIndexed { r, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { c, g ->
                    GenreTile(g, r * 2 + c, { MusicUi.open(Dest.Genre(g.id, g.name)) }, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
