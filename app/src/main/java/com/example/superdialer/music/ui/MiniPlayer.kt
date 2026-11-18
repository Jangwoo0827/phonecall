package com.example.superdialer.music.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.music.MusicPlayer
import com.example.superdialer.music.MusicStore

/** The bar above the app's tab bar while a song is loaded: tap it to open the full player. */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val video = com.example.superdialer.music.YoutubePlayback.current
    val track = video ?: MusicPlayer.current ?: return
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).testTag("mini_player"),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 3.dp,
    ) {
        Column {
            Row(
                Modifier.clickable { MusicUi.nowPlayingOpen = true }.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track.artUrl, Modifier.size(42.dp), px = 150, shape = RoundedCornerShape(6.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val liked = MusicStore.isLiked(track)
                IconButton(onClick = { toggleLike(context, track) }) {
                    Icon(
                        if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        str(if (liked) R.string.music_unlike else R.string.music_like),
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // A video only plays while it is on screen, so its play button opens the full player.
                IconButton(onClick = { if (video != null) MusicUi.nowPlayingOpen = true else MusicPlayer.togglePlay() }, modifier = Modifier.testTag("mini_play")) {
                    Icon(
                        if (video == null && MusicPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        str(if (video == null && MusicPlayer.isPlaying) R.string.music_pause else R.string.music_play),
                    )
                }
            }
            val duration = (if (video != null) com.example.superdialer.music.YoutubePlayback.durationMs else MusicPlayer.durationMs).coerceAtLeast(1L)
            val position = if (video != null) com.example.superdialer.music.YoutubePlayback.positionMs else MusicPlayer.positionMs
            LinearProgressIndicator(
                progress = { (position.toFloat() / duration).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}
