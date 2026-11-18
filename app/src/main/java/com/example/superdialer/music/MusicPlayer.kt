package com.example.superdialer.music

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import java.io.IOException

/**
 * The one music player of the app (a media3 ExoPlayer) plus the state the screens show. Playback runs in [MusicService]
 * so it keeps going in the background with a media notification and lock-screen controls.
 *
 * The queue is the player's own playlist. Shuffle is done here (the upcoming songs are rearranged) instead of with
 * ExoPlayer's shuffle mode, so "Play next" and "Add to queue" always land where the queue screen shows them.
 */
object MusicPlayer {
    private lateinit var app: Context
    private var built: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Songs the player has been handed, by id, so a MediaItem can be turned back into a [Track]. */
    private val known = HashMap<String, Track>()

    /** Ids in the order they had before shuffling (to restore it when shuffle goes off). */
    private val originalOrder = ArrayList<String>()

    var queue by mutableStateOf<List<Track>>(emptyList()); private set
    var current by mutableStateOf<Track?>(null); private set
    var currentIndex by mutableIntStateOf(-1); private set
    var isPlaying by mutableStateOf(false); private set
    var buffering by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set
    var durationMs by mutableLongStateOf(0L); private set
    var shuffle by mutableStateOf(false); private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF); private set

    /** Wall-clock time the sleep timer stops playback (0 = off) and "stop after this song". */
    var sleepEndsAt by mutableLongStateOf(0L); private set
    var sleepAfterTrack by mutableStateOf(false); private set

    fun init(context: Context) {
        app = context.applicationContext
    }

    val player: ExoPlayer
        get() = built ?: build().also { built = it }

    private fun build(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("SuperDialer")
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(12000)
            .setAllowCrossProtocolRedirects(true)
        val upstream = DefaultDataSource.Factory(app, http)
        // "deezer://track/<id>" -> the track's current preview link (links expire, so they are never stored).
        val resolving = ResolvingDataSource.Factory(upstream) { spec ->
            if (spec.uri.scheme == "deezer") {
                val id = spec.uri.lastPathSegment?.toLongOrNull() ?: throw IOException("bad track")
                val url = DeezerApi.freshPreviewBlocking(id) ?: throw IOException("no preview")
                spec.withUri(Uri.parse(url))
            } else {
                spec
            }
        }
        return ExoPlayer.Builder(app)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { it.addListener(listener) }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncCurrent()
            if (player.playWhenReady) current?.let { MusicStore.addRecent(it) }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = refreshQueue()

        override fun onIsPlayingChanged(isPlayingNow: Boolean) {
            isPlaying = isPlayingNow
            tick()
        }

        override fun onPlaybackStateChanged(state: Int) {
            buffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY || state == Player.STATE_ENDED) tick()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && sleepAfterTrack) {
                player.pauseAtEndOfMediaItems = false
                sleepAfterTrack = false
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            repeat = repeatMode
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player
            Toast.makeText(app, str(R.string.music_cant_play), Toast.LENGTH_SHORT).show()
            // Skip the broken song and keep going.
            if (p.hasNextMediaItem()) {
                p.seekToNextMediaItem()
                p.prepare()
                p.play()
            } else {
                p.prepare()
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val p = built ?: return
            positionMs = p.currentPosition.coerceAtLeast(0L)
            durationMs = p.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: current?.durationSec?.times(1000L) ?: 0L
            if (sleepEndsAt > 0L && System.currentTimeMillis() >= sleepEndsAt) {
                sleepEndsAt = 0L
                p.pause()
            }
            if (p.isPlaying || p.playbackState == Player.STATE_BUFFERING) handler.postDelayed(this, 400)
        }
    }

    private fun tick() {
        handler.removeCallbacks(ticker)
        ticker.run()
    }

    private fun syncCurrent() {
        val p = built ?: return
        currentIndex = p.currentMediaItemIndex
        current = p.currentMediaItem?.mediaId?.let { known[it] }
        durationMs = current?.durationSec?.times(1000L) ?: 0L
        positionMs = 0L
    }

    private fun refreshQueue() {
        val p = built ?: return
        queue = (0 until p.mediaItemCount).mapNotNull { known[p.getMediaItemAt(it).mediaId] }
        currentIndex = p.currentMediaItemIndex
        current = p.currentMediaItem?.mediaId?.let { known[it] }
    }

    private fun item(track: Track): MediaItem {
        known[track.id] = track
        val art = artAtSize(track.artUrl, 500) ?: track.artUrl
        val b = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(art?.let { Uri.parse(it) })
                    .build(),
            )
        if (!track.isLocal) b.setMimeType(MimeTypes.AUDIO_MPEG)
        return b.build()
    }

    private fun ensureService() {
        try {
            app.startService(Intent(app, MusicService::class.java))
        } catch (_: Exception) {
            // Started from the background: playback still works, only the notification may be missing.
        }
    }

    // --- starting playback -----------------------------------------------------------------------------------------

    /** Plays [tracks] from [startIndex]. With [shuffled] the rest is played in random order (the chosen song first). */
    fun play(tracks: List<Track>, startIndex: Int = 0, shuffled: Boolean = false) {
        if (tracks.isEmpty()) return
        val p = player
        val start = startIndex.coerceIn(0, tracks.lastIndex)
        originalOrder.clear()
        originalOrder.addAll(tracks.map { it.id })
        p.setMediaItems(tracks.map { item(it) }, start, 0L)
        shuffle = false
        if (shuffled) applyShuffle(true)
        p.prepare()
        p.play()
        ensureService()
        refreshQueue()
        syncCurrent()
    }

    /** "Shuffle play": a random song first, the rest shuffled. */
    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        play(tracks, (tracks.indices).random(), shuffled = true)
    }

    fun playNext(track: Track) {
        val p = player
        if (p.mediaItemCount == 0) return play(listOf(track))
        p.addMediaItem(p.currentMediaItemIndex + 1, item(track))
        originalOrder.add(track.id)
        Toast.makeText(app, str(R.string.music_will_play_next), Toast.LENGTH_SHORT).show()
    }

    fun addToQueue(track: Track) {
        val p = player
        if (p.mediaItemCount == 0) return play(listOf(track))
        p.addMediaItem(item(track))
        originalOrder.add(track.id)
        Toast.makeText(app, str(R.string.music_added_to_queue), Toast.LENGTH_SHORT).show()
    }

    fun addAllToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val p = player
        if (p.mediaItemCount == 0) return play(tracks)
        p.addMediaItems(tracks.map { item(it) })
        originalOrder.addAll(tracks.map { it.id })
        Toast.makeText(app, str(R.string.music_added_to_queue), Toast.LENGTH_SHORT).show()
    }

    // --- controls ---------------------------------------------------------------------------------------------------

    fun togglePlay() {
        val p = player
        if (p.mediaItemCount == 0) return
        if (p.isPlaying) {
            p.pause()
        } else {
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0, 0L)
            p.play()
        }
    }

    fun next() {
        val p = player
        if (p.hasNextMediaItem()) p.seekToNextMediaItem() else if (repeat != Player.REPEAT_MODE_OFF) p.seekTo(0, 0L)
        p.play()
    }

    fun previous() {
        val p = player
        // As in every player: more than 3 seconds in restarts the song, otherwise go back one.
        if (p.currentPosition > 3000 || !p.hasPreviousMediaItem()) p.seekTo(0L) else p.seekToPreviousMediaItem()
        p.play()
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms.coerceAtLeast(0L))
        positionMs = ms
    }

    fun skipTo(index: Int) {
        val p = player
        if (index !in 0 until p.mediaItemCount) return
        p.seekTo(index, 0L)
        p.play()
    }

    fun removeFromQueue(index: Int) {
        val p = player
        if (index !in 0 until p.mediaItemCount) return
        p.removeMediaItem(index)
        if (p.mediaItemCount == 0) stop()
    }

    fun moveInQueue(from: Int, to: Int) {
        val p = player
        if (from !in 0 until p.mediaItemCount || to !in 0 until p.mediaItemCount) return
        p.moveMediaItem(from, to)
    }

    fun clearUpcoming() {
        val p = player
        val from = p.currentMediaItemIndex + 1
        if (from < p.mediaItemCount) p.removeMediaItems(from, p.mediaItemCount)
        originalOrder.retainAll(queue.map { it.id }.toSet())
    }

    fun stop() {
        built?.run {
            stop()
            clearMediaItems()
        }
        originalOrder.clear()
        current = null
        currentIndex = -1
        queue = emptyList()
        isPlaying = false
        positionMs = 0L
        durationMs = 0L
        cancelSleepTimer()
        handler.removeCallbacks(ticker)
    }

    fun cycleRepeat() {
        val p = player
        p.repeatMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        repeat = p.repeatMode
    }

    fun toggleShuffle() = applyShuffle(!shuffle)

    /** Rearranges the songs after the current one: random order, or back to the order they were added in. */
    private fun applyShuffle(on: Boolean) {
        val p = player
        shuffle = on
        val first = p.currentMediaItemIndex + 1
        val count = p.mediaItemCount
        if (first >= count - 1) return
        if (on) {
            // A random order for the upcoming songs, applied with moves so the current song keeps playing.
            val wanted = (first until count).map { p.getMediaItemAt(it).mediaId }.shuffled()
            wanted.forEachIndexed { n, id ->
                val pos = first + n
                var k = pos
                while (k < count && p.getMediaItemAt(k).mediaId != id) k++
                if (k < count && k != pos) p.moveMediaItem(k, pos)
            }
        } else {
            for (pos in first until count) {
                var best = pos
                var bestRank = Int.MAX_VALUE
                for (k in pos until count) {
                    val rank = originalOrder.indexOf(p.getMediaItemAt(k).mediaId).let { if (it < 0) Int.MAX_VALUE - 1 else it }
                    if (rank < bestRank) { bestRank = rank; best = k }
                }
                if (best != pos) p.moveMediaItem(best, pos)
            }
        }
        refreshQueue()
    }

    // --- sleep timer ------------------------------------------------------------------------------------------------

    fun sleepInMinutes(minutes: Int) {
        cancelSleepTimer()
        sleepEndsAt = System.currentTimeMillis() + minutes * 60_000L
        tick()
    }

    fun sleepAfterCurrentTrack() {
        cancelSleepTimer()
        sleepAfterTrack = true
        player.pauseAtEndOfMediaItems = true
    }

    fun cancelSleepTimer() {
        sleepEndsAt = 0L
        if (sleepAfterTrack) {
            sleepAfterTrack = false
            built?.pauseAtEndOfMediaItems = false
        }
    }
}
