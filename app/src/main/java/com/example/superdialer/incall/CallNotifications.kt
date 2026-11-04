package com.example.superdialer.incall

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.superdialer.MainActivity
import com.example.superdialer.dialer.PhoneNumberFormatter

object CallNotifications {
    private const val CHANNEL_INCOMING = "incoming_call"
    private const val CHANNEL_ONGOING = "ongoing_call"
    private const val CHANNEL_MISSED = "missed_call"

    private const val ID_INCOMING = 1001
    private const val ID_ONGOING = 1002
    private const val ID_MISSED_BASE = 2000

    const val ACTION_DECLINE = "com.example.superdialer.action.DECLINE"
    const val ACTION_HANGUP = "com.example.superdialer.action.HANGUP"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Telecom plays the ringtone itself, so the notification stays silent.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, str(R.string.t_incoming_call), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, str(R.string.t_in_call), NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MISSED, str(R.string.t_missed_call), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** Shows the incoming-call or ongoing-call notification matching the current calls. */
    fun update(context: Context, calls: List<CallSnapshot>) {
        val manager = NotificationManagerCompat.from(context)
        val ringing = calls.firstOrNull { it.isRinging }
        val ongoing = calls.firstOrNull { !it.isRinging && !it.isDisconnected }
        try {
            when {
                ringing != null -> {
                    manager.cancel(ID_ONGOING)
                    manager.notify(ID_INCOMING, incoming(context, ringing))
                }
                ongoing != null -> {
                    manager.cancel(ID_INCOMING)
                    manager.notify(ID_ONGOING, ongoing(context, ongoing))
                }
                else -> {
                    manager.cancel(ID_INCOMING)
                    manager.cancel(ID_ONGOING)
                }
            }
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied: the call still works, just without notifications.
        }
    }

    fun showMissed(context: Context, number: String, name: String?) {
        val label = name ?: PhoneNumberFormatter.formatLoose(number).ifEmpty { str(R.string.t_no_caller_info) }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_CALL_LOG, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle(str(R.string.t_missed_call))
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_MISSED_BASE + number.hashCode().and(0xFFF), notification)
        } catch (e: SecurityException) {
            // Notifications not permitted.
        }
    }

    private fun label(call: CallSnapshot) =
        call.name ?: if (call.isConference) str(R.string.t_conference_call) else PhoneNumberFormatter.formatLoose(call.number).ifEmpty { str(R.string.t_no_caller_info) }

    private fun incoming(context: Context, call: CallSnapshot) =
        NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(label(call))
            .setContentText(if (call.isVideo) str(R.string.t_incoming_video_call) else str(R.string.t_incoming_call))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openCall(context, 0))
            .setFullScreenIntent(openCall(context, 1), true)
            .addAction(0, str(R.string.t_declined), broadcast(context, ACTION_DECLINE, 2))
            .apply {
                if (call.isVideo) {
                    addAction(0, str(R.string.t_voice), openCall(context, 3, answer = true))
                    addAction(0, str(R.string.t_video), openCall(context, 6, answer = true, video = true))
                } else {
                    addAction(0, str(R.string.t_answer), openCall(context, 3, answer = true))
                }
            }
            .build()

    private fun ongoing(context: Context, call: CallSnapshot) =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(label(call))
            .setContentText(if (call.isHolding) str(R.string.t_on_hold) else str(R.string.t_in_call))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openCall(context, 4))
            .addAction(0, str(R.string.t_end), broadcast(context, ACTION_HANGUP, 5))
            .build()

    private fun openCall(context: Context, requestCode: Int, answer: Boolean = false, video: Boolean = false) =
        PendingIntent.getActivity(
            context, requestCode, InCallActivity.intent(context, answer, video),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcast(context: Context, action: String, requestCode: Int) =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
