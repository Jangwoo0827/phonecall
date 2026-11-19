package com.example.superdialer.browser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.superdialer.MainActivity
import com.example.superdialer.R
import com.example.superdialer.i18n.str

/**
 * Keeps a music web player (Spotify's) playing when the app leaves the screen, as a phone browser does: while a tab of
 * open.spotify.com is open the app does not pause that page, does not release it after the 3-minute timer, and shows a
 * notification (needed for Android to keep the app running) with a Stop button that pauses the page.
 *
 * Only for Spotify's own web player, whose audio pages are meant to keep playing; video sites such as YouTube are not
 * handled here and still pause when they leave the screen.
 */
object WebAudioKeepAlive {
    private const val HOST = "https://open.spotify.com"

    /** True while a normal (non-private) tab shows the web player; the browser then skips pausing and releasing it. */
    @Volatile
    var active = false
        private set

    /** Pauses the audio of the web player tabs (set by the browser view model). */
    var pauseAll: () -> Unit = {}

    fun isMusicUrl(url: String?) = url != null && url.startsWith(HOST)

    fun update(context: Context, shouldRun: Boolean) {
        if (shouldRun == active) return
        active = shouldRun
        val app = context.applicationContext
        if (shouldRun) {
            runCatching { ContextCompat.startForegroundService(app, Intent(app, WebAudioService::class.java)) }
        } else {
            app.stopService(Intent(app, WebAudioService::class.java))
        }
    }
}

class WebAudioService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            WebAudioKeepAlive.pauseAll()
            WebAudioKeepAlive.update(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, str(R.string.music_web_player_running), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, WebAudioService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(str(R.string.music_web_player_running))
            .setContentText(str(R.string.music_web_player_running_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, str(R.string.music_web_player_stop), stop)
            .build()
        startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        return START_NOT_STICKY
    }

    private companion object {
        const val CHANNEL = "web_audio"
        const val NOTIFICATION_ID = 3001
        const val ACTION_STOP = "com.example.superdialer.WEB_AUDIO_STOP"
    }
}
