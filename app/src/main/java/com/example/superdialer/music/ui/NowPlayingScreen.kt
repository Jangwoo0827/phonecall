package com.example.superdialer.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.music.MusicPlayer
import com.example.superdialer.music.MusicStore
import com.example.superdialer.music.Track
import com.example.superdialer.music.formatDuration

/** The full-screen player: big cover on a background tinted with the cover's color, seek bar, controls, queue and sleep timer. */
@Composable
fun NowPlayingScreen() {
    val context = LocalContext.current
    val track = MusicPlayer.current
    BackHandler { MusicUi.nowPlayingOpen = false }
    if (track == null) {
        // Playback ended while this was open.
        androidx.compose.runtime.LaunchedEffect(Unit) { MusicUi.nowPlayingOpen = false }
        return
    }
    val tint = rememberArtColor(track.artUrl) ?: MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    // The cover's color, darkened so white text stays readable, fading into the app's own surface color.
    val top = lerp(tint, Color.Black, 0.45f)
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, lerp(top, surface, 0.7f), surface)))
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged > 160f) MusicUi.nowPlayingOpen = false },
                ) { _, amount -> dragged += amount }
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { MusicUi.nowPlayingOpen = false }) { Icon(Icons.Filled.KeyboardArrowDown, str(R.string.music_close), tint = Color.White) }
            Text(
                str(R.string.music_now_playing),
                Modifier.weight(1f),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { MusicUi.menuTrack = track }) { Icon(Icons.Filled.MoreVert, str(R.string.music_more), tint = Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        Crossfade(track.id, label = "cover", modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            Artwork(track.artUrl, Modifier.fillMaxSize(), px = 1000, shape = RoundedCornerShape(12.dp))
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist,
                    Modifier.clickable(enabled = track.artistId > 0) { MusicUi.requestOpen(Dest.Artist(track.artistId)) },
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val liked = MusicStore.isLiked(track)
            IconButton(onClick = { toggleLike(context, track) }) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    str(if (liked) R.string.music_unlike else R.string.music_like),
                    tint = if (liked) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (!track.isLocal) {
            Text(
                str(R.string.music_preview_note),
                Modifier.padding(top = 4.dp).background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        SeekBar()
        Controls()
        Spacer(Modifier.weight(1f))
        BottomActions(track)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SeekBar() {
    val duration = MusicPlayer.durationMs.coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val value = if (dragging) dragValue else (MusicPlayer.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Slider(
        value = value,
        onValueChange = { dragging = true; dragValue = it },
        onValueChangeFinished = {
            MusicPlayer.seekTo((dragValue * duration).toLong())
            dragging = false
        },
        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        val shownMs = if (dragging) (dragValue * duration).toLong() else MusicPlayer.positionMs
        Text(formatDuration((shownMs / 1000).toInt()), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        Text(formatDuration((duration / 1000).toInt()), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Controls() {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { MusicPlayer.toggleShuffle() }) {
            Icon(
                Icons.Filled.Shuffle,
                str(if (MusicPlayer.shuffle) R.string.music_shuffle_on else R.string.music_shuffle_off),
                tint = if (MusicPlayer.shuffle) MaterialTheme.colorScheme.primary else Color.White,
            )
        }
        IconButton(onClick = { MusicPlayer.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.SkipPrevious, str(R.string.music_previous), tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Box(
            Modifier.size(68.dp).background(Color.White, CircleShape).clickable { MusicPlayer.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            if (MusicPlayer.buffering && !MusicPlayer.isPlaying) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(32.dp), color = Color.Black, strokeWidth = 3.dp)
            } else {
                Icon(
                    if (MusicPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    str(if (MusicPlayer.isPlaying) R.string.music_pause else R.string.music_play),
                    tint = Color.Black,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        IconButton(onClick = { MusicPlayer.next() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.SkipNext, str(R.string.music_next), tint = Color.White, modifier = Modifier.size(40.dp))
        }
        IconButton(onClick = { MusicPlayer.cycleRepeat() }) {
            val mode = MusicPlayer.repeat
            Icon(
                if (mode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                str(when (mode) { Player.REPEAT_MODE_ONE -> R.string.music_repeat_one; Player.REPEAT_MODE_ALL -> R.string.music_repeat_all; else -> R.string.music_repeat_off }),
                tint = if (mode == Player.REPEAT_MODE_OFF) Color.White else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun BottomActions(track: Track) {
    val context = LocalContext.current
    val sleeping = MusicPlayer.sleepEndsAt > 0L || MusicPlayer.sleepAfterTrack
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { MusicUi.sleepOpen = true }) {
            Icon(Icons.Filled.Bedtime, str(R.string.music_sleep_timer), tint = if (sleeping) MaterialTheme.colorScheme.primary else Color.White)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { shareTrack(context, track) }) { Icon(Icons.Filled.Share, str(R.string.music_share), tint = Color.White) }
            IconButton(onClick = { MusicUi.queueOpen = true }) { Icon(Icons.Filled.QueueMusic, str(R.string.music_queue), tint = Color.White) }
        }
    }
}
