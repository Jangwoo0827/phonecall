package com.example.superdialer.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.superdialer.MainActivity
import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.music.SpotifyWeb
import java.net.URL
import kotlin.concurrent.thread

/**
 * Keeps a music web player (Spotify's) playing when the app leaves the screen, as a phone browser does: while a tab of
 * open.spotify.com (or the Music tab's page) is open the app does not pause that page, does not release it after the
 * 3-minute timer, and shows a media notification (needed for Android to keep the app running, and what appears at the
 * bottom of the lock screen) with the current song, previous / play-pause / next buttons and a Stop button.
 *
 * Only for Spotify's own web player, whose audio pages are meant to keep playing; video sites such as YouTube are not
 * handled here and still pause when they leave the screen.
 */
object WebAudioKeepAlive {
    private const val HOST = "https://open.spotify.com"

    private val sources = HashSet<String>()

    /** True while some page (a browser tab or the Music tab's player) is a web player that must keep playing. */
    @Volatile
    var active = false
        private set

    /** Pauses the audio of every web player (set by the browser view model and the Music tab). */
    var pauseAll: () -> Unit = {}

    fun isMusicUrl(url: String?) = url != null && url.startsWith(HOST)

    /** [source] says whether it needs the keep-alive; the service runs while any source does. */
    fun update(context: Context, source: String, needed: Boolean) {
        val changed = synchronized(sources) { if (needed) sources.add(source) else sources.remove(source) }
        if (!changed) return
        val want = synchronized(sources) { sources.isNotEmpty() }
        if (want == active) return
        active = want
        val app = context.applicationContext
        if (want) {
            runCatching { ContextCompat.startForegroundService(app, Intent(app, WebAudioService::class.java)) }
        } else {
            app.stopService(Intent(app, WebAudioService::class.java))
        }
    }

    /** The Stop button: forget every source. */
    fun clear(context: Context) {
        synchronized(sources) { sources.clear() }
        if (active) {
            active = false
            context.applicationContext.stopService(Intent(context.applicationContext, WebAudioService::class.java))
        }
    }
}

class WebAudioService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var title = ""
    private var artist = ""
    private var playing = false
    private var positionMs = 0L
    private var durationMs = 0L
    private var artUrl = ""
    private var art: Bitmap? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    private val poll = object : Runnable {
        override fun run() {
            SpotifyWeb.readState { s ->
                if (s != null) {
                    title = s.title; artist = s.artist; playing = s.playing
                    positionMs = (s.positionSec * 1000).toLong(); durationMs = (s.durationSec * 1000).toLong()
                    if (s.artUrl.isNotEmpty() && s.artUrl != artUrl) {
                        artUrl = s.artUrl
                        val url = s.artUrl
                        thread {
                            val bmp = runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()
                            handler.post { if (url == artUrl) { art = bmp; publish() } }
                        }
                    }
                    publish()
                }
                if (started) handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                WebAudioKeepAlive.pauseAll()
                WebAudioKeepAlive.clear(this)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY_PAUSE -> { SpotifyWeb.control(if (playing) "pause" else "play"); return START_NOT_STICKY }
            ACTION_NEXT -> { SpotifyWeb.control("next"); return START_NOT_STICKY }
            ACTION_PREV -> { SpotifyWeb.control("prev"); return START_NOT_STICKY }
        }
        if (!started) {
            started = true
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CHANNEL, str(R.string.music_web_player_running), NotificationManager.IMPORTANCE_LOW))
            session = MediaSession(this, "SuperDialerWeb").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() = SpotifyWeb.control("play")
                    override fun onPause() = SpotifyWeb.control("pause")
                    override fun onSkipToNext() = SpotifyWeb.control("next")
                    override fun onSkipToPrevious() = SpotifyWeb.control("prev")
                    override fun onStop() { startService(Intent(this@WebAudioService, WebAudioService::class.java).setAction(ACTION_STOP)) }
                })
                isActive = true
            }
            startForeground(NOTIFICATION_ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            handler.post(poll)
        }
        return START_NOT_STICKY
    }

    private fun action(code: Int, label: String, icon: Int, action: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, code, Intent(this, WebAudioService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, icon), label, pi).build()
    }

    private fun build(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(title.ifEmpty { str(R.string.music_web_player_running) })
            .setContentText(artist.ifEmpty { str(R.string.music_web_player_running_text) })
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(action(1, str(R.string.music_web_player_prev), android.R.drawable.ic_media_previous, ACTION_PREV))
            .addAction(
                if (playing) action(2, str(R.string.music_web_player_pause), android.R.drawable.ic_media_pause, ACTION_PLAY_PAUSE)
                else action(2, str(R.string.music_web_player_play), android.R.drawable.ic_media_play, ACTION_PLAY_PAUSE),
            )
            .addAction(action(3, str(R.string.music_web_player_next), android.R.drawable.ic_media_next, ACTION_NEXT))
            .addAction(action(4, str(R.string.music_web_player_stop), android.R.drawable.ic_menu_close_clear_cancel, ACTION_STOP))
            .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1, 2))
        art?.let { b.setLargeIcon(it) }
        return b.build()
    }

    private fun publish() {
        val s = session ?: return
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build(),
        )
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP,
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs, if (playing) 1f else 0f,
                )
                .build(),
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build())
    }

    override fun onDestroy() {
        started = false
        handler.removeCallbacksAndMessages(null)
        session?.release()
        session = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "web_audio"
        const val NOTIFICATION_ID = 3001
        const val ACTION_STOP = "com.example.superdialer.WEB_AUDIO_STOP"
        const val ACTION_PLAY_PAUSE = "com.example.superdialer.WEB_AUDIO_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.superdialer.WEB_AUDIO_NEXT"
        const val ACTION_PREV = "com.example.superdialer.WEB_AUDIO_PREV"
    }
}
