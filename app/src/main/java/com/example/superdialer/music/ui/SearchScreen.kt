package com.example.superdialer.music.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.music.Album
import com.example.superdialer.music.Artist
import com.example.superdialer.music.AudiusApi
import com.example.superdialer.music.DeezerApi
import com.example.superdialer.music.MusicPlayer
import com.example.superdialer.music.MusicStore
import com.example.superdialer.music.PlaylistInfo
import com.example.superdialer.music.SearchResults
import com.example.superdialer.music.Track
import kotlinx.coroutines.delay

/** Search: genres to browse while the box is empty, results (all / songs / albums / artists / playlists) as you type. */
@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    var query by MusicUi::searchQuery
    var submitted by MusicUi::searchSubmitted
    var filter by MusicUi::searchFilter
    val focus = LocalFocusManager.current

    // Debounce: search 400 ms after typing stops.
    LaunchedEffect(query) {
        if (query.isBlank()) { submitted = ""; return@LaunchedEffect }
        delay(400)
        submitted = query.trim()
    }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(str(R.string.music_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, str(R.string.music_close)) }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                submitted = query.trim()
                MusicStore.addSearch(submitted)
                focus.clearFocus()
            }),
        )
        if (submitted.isEmpty()) {
            BrowseSearch(onPick = { query = it; submitted = it; MusicStore.addSearch(it) })
        } else {
            SearchResultsView(submitted, filter, onFilter = { filter = it })
        }
    }
}

@Composable
private fun BrowseSearch(onPick: (String) -> Unit) {
    val genres = rememberLoad("genres") { DeezerApi.genres() }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (MusicStore.searches.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(str(R.string.music_recent_searches), Modifier.weight(1f))
                    TextButton(onClick = { MusicStore.clearSearches() }) { Text(str(R.string.music_clear_all)) }
                }
            }
            items(MusicStore.searches.toList(), key = { it }) { q ->
                Row(Modifier.fillMaxWidth().clickable { onPick(q) }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(q, Modifier.weight(1f).padding(start = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { MusicStore.removeSearch(q) }) { Icon(Icons.Filled.Close, str(R.string.music_close)) }
                }
            }
        }
        item { SectionTitle(str(R.string.music_browse_genres)) }
        item {
            LoadedContent(genres) { GenreGrid(it) }
        }
    }
}

@Composable
private fun SearchResultsView(query: String, filter: Int, onFilter: (Int) -> Unit) {
    val results = rememberLoad(query) { DeezerApi.search(query) }
    val fullSongs = rememberLoad("au$query") { runCatching { AudiusApi.search(query, 25) }.getOrDefault(emptyList()) }
    val labels = listOf(R.string.music_filter_all, R.string.music_filter_songs, R.string.music_filter_albums, R.string.music_filter_artists, R.string.music_filter_playlists, R.string.music_full_songs)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels.size) { i ->
            FilterChip(selected = filter == i, onClick = { onFilter(i) }, label = { Text(str(labels[i])) })
        }
    }
    if (filter == 5) {
        val list = fullSongs.value
        when {
            list == null -> LoadingBox()
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                Text(str(R.string.music_no_results, query), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)) {
                item { Text(str(R.string.music_full_note), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                trackItems(list, list.size)
            }
        }
        return
    }
    LoadedContent(results) { r ->
        if (r.isEmpty) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                Text(str(R.string.music_no_results, query), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            ResultsList(r, filter, query, fullSongs.value.orEmpty())
        }
    }
}

@Composable
private fun ResultsList(r: SearchResults, filter: Int, query: String, full: List<Track>) {
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        when (filter) {
            1 -> trackItems(r.tracks, r.tracks.size)
            2 -> items(r.albums, key = { "a${it.id}" }) { AlbumRow(it) }
            3 -> items(r.artists, key = { "r${it.id}" }) { ArtistRow(it) }
            4 -> items(r.playlists, key = { "p${it.id}" }) { PlaylistRow(it) }
            else -> {
                topArtist(r, query)?.let { top ->
                    item {
                        SectionTitle(str(R.string.music_top_result))
                        ArtistRow(top)
                    }
                }
                if (r.tracks.isNotEmpty()) {
                    item { SectionTitle(str(R.string.music_filter_songs)) }
                    trackItems(r.tracks, 5)
                }
                if (full.isNotEmpty()) {
                    item { SectionTitle(str(R.string.music_full_songs)) }
                    trackItems(full, 4)
                }
                val others = r.artists.filter { it.id != topArtist(r, query)?.id }.take(4)
                if (others.isNotEmpty()) {
                    item { SectionTitle(str(R.string.music_filter_artists)) }
                    items(others, key = { "r${it.id}" }) { ArtistRow(it) }
                }
                if (r.albums.isNotEmpty()) {
                    item { SectionTitle(str(R.string.music_filter_albums)) }
                    items(r.albums.take(4), key = { "a${it.id}" }) { AlbumRow(it) }
                }
                if (r.playlists.isNotEmpty()) {
                    item { SectionTitle(str(R.string.music_filter_playlists)) }
                    items(r.playlists.take(4), key = { "p${it.id}" }) { PlaylistRow(it) }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trackItems(tracks: List<Track>, limit: Int) {
    val shown = tracks.take(limit)
    items(shown.size, key = { "t${shown[it].id}" }) { i ->
        TrackRow(shown[i], onClick = { MusicPlayer.play(tracks, i) })
    }
}

@Composable
fun AlbumRow(a: Album, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable { MusicUi.open(Dest.Album(a.id)) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(a.artUrl, Modifier.size(56.dp), px = 150)
        Column(Modifier.padding(start = 12.dp)) {
            Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(str(R.string.music_album_by, a.artist), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ArtistRow(a: Artist, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable { MusicUi.open(Dest.Artist(a.id)) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(a.picUrl, Modifier.size(56.dp), px = 150, shape = CircleShape)
        Column(Modifier.padding(start = 12.dp)) {
            Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(str(R.string.music_artist_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PlaylistRow(p: PlaylistInfo, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable { MusicUi.open(Dest.DeezerPlaylist(p.id)) }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(p.artUrl, Modifier.size(56.dp), px = 150)
        Column(Modifier.padding(start = 12.dp)) {
            Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(str(R.string.music_playlist_by, p.owner.ifEmpty { songsLabel(p.trackCount) }), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The artist to show as "Top result": the best-known artist whose name is exactly the search, otherwise the artist of the
 * first song found, otherwise the first artist Deezer lists.
 */
internal fun topArtist(r: SearchResults, query: String): Artist? {
    val q = query.trim()
    r.artists.filter { it.name.equals(q, ignoreCase = true) }.maxByOrNull { it.fans }?.let { return it }
    r.tracks.firstOrNull()?.let { t -> r.artists.firstOrNull { it.id == t.artistId }?.let { return it } }
    return r.artists.maxByOrNull { it.fans }
}
