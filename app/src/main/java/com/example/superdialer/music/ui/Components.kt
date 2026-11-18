package com.example.superdialer.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.music.Album
import com.example.superdialer.music.Artist
import com.example.superdialer.music.Genre
import com.example.superdialer.music.MusicPlayer
import com.example.superdialer.music.PlaylistInfo
import com.example.superdialer.music.Track
import com.example.superdialer.music.formatDuration
import kotlinx.coroutines.CancellationException

// --- loading ---------------------------------------------------------------------------------------------------------

/** What a screen shows while it fetches something from the network. */
class LoadState<T>(val value: T?, val loading: Boolean, val failed: Boolean, val retry: () -> Unit)

@Composable
fun <T> rememberLoad(key: Any?, block: suspend () -> T): LoadState<T> {
    var attempt by remember { mutableIntStateOf(0) }
    var value by remember(key) { mutableStateOf<T?>(null) }
    var failed by remember(key) { mutableStateOf(false) }
    var loading by remember(key) { mutableStateOf(true) }
    LaunchedEffect(key, attempt) {
        loading = true
        failed = false
        try {
            value = block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
        loading = false
    }
    return LoadState(value, loading && value == null, failed && value == null) { attempt++ }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
    }
}

@Composable
fun ErrorBox(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(str(R.string.music_loading_failed), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text(str(R.string.music_retry)) }
    }
}

/** Shows [content] when the data is there, a spinner while loading and a retry box when it failed. */
@Composable
fun <T> LoadedContent(state: LoadState<T>, content: @Composable (T) -> Unit) {
    val v = state.value
    when {
        v != null -> content(v)
        state.failed -> ErrorBox(state.retry)
        else -> LoadingBox()
    }
}

// --- text helpers ------------------------------------------------------------------------------------------------------

fun songsLabel(n: Int): String = if (n == 1) str(R.string.music_one_song) else str(R.string.music_songs_count, n)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
}

// --- rows and cards ------------------------------------------------------------------------------------------------------

/** One song in a list: picture, title, artist; the ⋮ opens the song's menu. [number] replaces the picture in album lists. */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    showArt: Boolean = true,
    showMenu: Boolean = true,
    onMenu: () -> Unit = { MusicUi.menuTrack = track },
) {
    val isCurrent = MusicPlayer.current?.id == track.id
    val accent = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Text(
                number.toString(),
                modifier = Modifier.width(28.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else if (showArt) {
            Artwork(track.artUrl, Modifier.size(48.dp), px = 150)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(track.title, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.explicit) {
                    Box(
                        Modifier.padding(end = 6.dp).size(14.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("E", color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                }
                Text(
                    if (track.isLink) str(R.string.music_link_song) else track.artist,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (showMenu) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = str(R.string.music_more))
            }
        } else {
            Text(formatDuration(track.durationSec), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

/** A square (or round, for artists) picture with a title and one line below it, as used in the horizontal shelves. */
@Composable
fun ArtworkCard(
    url: String?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Column(modifier.width(size).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Artwork(url, Modifier.size(size), px = 500, shape = shape)
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AlbumShelf(title: String, albums: List<Album>, onOpen: (Album) -> Unit) {
    if (albums.isEmpty()) return
    SectionTitle(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(albums, key = { it.id }) { a -> ArtworkCard(a.artUrl, a.title, a.artist, { onOpen(a) }) }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun ArtistShelf(title: String, artists: List<Artist>, onOpen: (Artist) -> Unit) {
    if (artists.isEmpty()) return
    SectionTitle(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(artists, key = { it.id }) { a ->
            Column(Modifier.width(120.dp).clickable { onOpen(a) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Artwork(a.picUrl, Modifier.size(120.dp), px = 500, shape = CircleShape)
                Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun PlaylistShelf(title: String, playlists: List<PlaylistInfo>, onOpen: (PlaylistInfo) -> Unit) {
    if (playlists.isEmpty()) return
    SectionTitle(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(playlists, key = { it.id }) { p -> ArtworkCard(p.artUrl, p.title, p.owner.ifEmpty { songsLabel(p.trackCount) }, { onOpen(p) }) }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun TrackShelf(title: String, tracks: List<Track>, onPlay: (Int) -> Unit) {
    if (tracks.isEmpty()) return
    SectionTitle(title)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(tracks.size) { i ->
            val t = tracks[i]
            ArtworkCard(t.artUrl, t.title, t.artist, { onPlay(i) })
        }
    }
    Spacer(Modifier.height(8.dp))
}

private val genreColors = listOf(
    Color(0xFFE13300), Color(0xFF1E3264), Color(0xFF8D67AB), Color(0xFFBA5D07), Color(0xFF148A08),
    Color(0xFFDC148C), Color(0xFF477D95), Color(0xFF503750), Color(0xFFAF2896), Color(0xFF509BF5),
)

@Composable
fun GenreTile(genre: Genre, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.height(88.dp).background(genreColors[index % genreColors.size], RoundedCornerShape(10.dp)).clickable(onClick = onClick),
    ) {
        Text(genre.name, Modifier.padding(12.dp).fillMaxWidth(0.7f), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Artwork(genre.picUrl, Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp).size(54.dp), px = 150, shape = RoundedCornerShape(6.dp))
    }
}

/** The big round green play / pause button of a collection page. */
@Composable
fun PlayFab(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = str(if (playing) R.string.music_pause else R.string.music_play),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(32.dp),
        )
    }
}
