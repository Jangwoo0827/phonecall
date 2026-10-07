package com.example.superdialer.incall

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
            NotificationChannel(CHANNEL_INCOMING, "수신 전화", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "통화 중", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MISSED, "부재중 전화", NotificationManager.IMPORTANCE_DEFAULT)
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
        val label = name ?: PhoneNumberFormatter.formatLoose(number).ifEmpty { "번호정보 없음" }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_CALL_LOG, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle("부재중 전화")
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
        call.name ?: PhoneNumberFormatter.formatLoose(call.number).ifEmpty { "번호정보 없음" }

    private fun incoming(context: Context, call: CallSnapshot) =
        NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(label(call))
            .setContentText("수신 전화")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openCall(context, 0))
            .setFullScreenIntent(openCall(context, 1), true)
            .addAction(0, "거절", broadcast(context, ACTION_DECLINE, 2))
            .addAction(0, "받기", openCall(context, 3, answer = true))
            .build()

    private fun ongoing(context: Context, call: CallSnapshot) =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(label(call))
            .setContentText(if (call.isHolding) "통화 대기 중" else "통화 중")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openCall(context, 4))
            .addAction(0, "종료", broadcast(context, ACTION_HANGUP, 5))
            .build()

    private fun openCall(context: Context, requestCode: Int, answer: Boolean = false) =
        PendingIntent.getActivity(
            context, requestCode, InCallActivity.intent(context, answer),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcast(context: Context, action: String, requestCode: Int) =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
