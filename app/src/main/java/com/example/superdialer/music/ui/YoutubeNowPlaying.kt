package com.example.superdialer.music.ui

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.music.MusicStore
import com.example.superdialer.music.YoutubePlayback
import com.example.superdialer.music.formatDuration

/**
 * The full player for YouTube link songs: YouTube's own player sits in the place of the cover, so the video is always in
 * view while it plays. Closing this screen, turning the screen off or leaving the app pauses it.
 */
@Composable
fun YoutubeNowPlaying() {
    val context = LocalContext.current
    val track = YoutubePlayback.current
    BackHandler { MusicUi.nowPlayingOpen = false }
    if (track == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { MusicUi.nowPlayingOpen = false }
        return
    }
    // The video only plays while it is on screen.
    LifecycleResumeEffect(Unit) {
        YoutubePlayback.webView().onResume()
        onPauseOrDispose {
            YoutubePlayback.pause()
            YoutubePlayback.webView().onPause()
        }
    }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF111111)).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { MusicUi.nowPlayingOpen = false }) { Icon(Icons.Filled.KeyboardArrowDown, str(R.string.music_close), tint = Color.White) }
            Text(str(R.string.music_now_playing), Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            IconButton(onClick = { MusicUi.menuTrack = track }) { Icon(Icons.Filled.MoreVert, str(R.string.music_more), tint = Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        // The cover's place: a square, with the 16:9 video in the middle of it.
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                factory = { ctx ->
                    YoutubePlayback.attach(ctx)
                    FrameLayout(ctx).apply {
                        val web = YoutubePlayback.webView()
                        (web.parent as? ViewGroup)?.removeView(web)
                        addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },
                onRelease = { frame ->
                    frame.removeAllViews()
                    YoutubePlayback.detach()
                },
            )
        }
        if (YoutubePlayback.errorCode != 0 || YoutubePlayback.stalled) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(str(if (YoutubePlayback.errorCode != 0) R.string.music_yt_cant_embed else R.string.music_yt_stalled), color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(track.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(str(R.string.music_yt_open_browser)) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val liked = MusicStore.isLiked(track)
            IconButton(onClick = { toggleLike(context, track) }) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    str(if (liked) R.string.music_unlike else R.string.music_like),
                    tint = if (liked) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        YoutubeSeekBar()
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { YoutubePlayback.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, str(R.string.music_previous), tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Box(Modifier.size(68.dp).background(Color.White, CircleShape).clickable { YoutubePlayback.togglePlay() }, contentAlignment = Alignment.Center) {
                Icon(
                    if (YoutubePlayback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    str(if (YoutubePlayback.isPlaying) R.string.music_pause else R.string.music_play),
                    tint = Color.Black,
                    modifier = Modifier.size(40.dp),
                )
            }
            IconButton(onClick = { YoutubePlayback.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, str(R.string.music_next), tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            str(R.string.music_yt_foreground_note),
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun YoutubeSeekBar() {
    val duration = YoutubePlayback.durationMs.coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val value = if (dragging) dragValue else (YoutubePlayback.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Slider(
        value = value,
        onValueChange = { dragging = true; dragValue = it },
        onValueChangeFinished = {
            YoutubePlayback.seekTo((dragValue * duration).toLong())
            dragging = false
        },
        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        val shown = if (dragging) (dragValue * duration).toLong() else YoutubePlayback.positionMs
        Text(formatDuration((shown / 1000).toInt()), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        Text(formatDuration((duration / 1000).toInt()), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
    }
}
