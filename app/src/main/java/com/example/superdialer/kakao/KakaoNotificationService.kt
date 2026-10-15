package com.example.superdialer.kakao

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Receives the phone's notifications (only while the user has allowed notification access for this app AND switched
 * collecting on) and hands KakaoTalk's to [KakaoInbox]. Everything else is ignored without being looked at.
 */
class KakaoNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!KakaoInbox.enabled) return
        if (sbn.packageName !in KakaoInbox.targetPackages) return
        val notification = sbn.notification
        // the "3 new messages" roll-up of several chats says nothing a single chat's notification does not
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras
        val messages = KakaoNotificationParser.parse(
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            lines = readLines(extras),
            postTime = sbn.postTime,
        )
        KakaoInbox.add(messages)

        // the reply box of the notification, usable until it goes away
        val room = messages.lastOrNull()?.room ?: return
        val action = notification.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
        if (action?.actionIntent != null) {
            KakaoInbox.setReplyHandle(
                room,
                KakaoInbox.ReplyHandle(sbn.key, action.actionIntent, action.remoteInputs, notification.contentIntent),
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in KakaoInbox.targetPackages) KakaoInbox.dropReplyHandle(sbn.key)
    }

    @Suppress("DEPRECATION")
    private fun readLines(extras: Bundle): List<KakaoNotificationParser.Line> {
        val raw = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        val bundles = raw.filterIsInstance<Bundle>().toTypedArray()
        return Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles).map {
            KakaoNotificationParser.Line(it.senderPerson?.name?.toString(), it.text?.toString().orEmpty(), it.timestamp)
        }
    }
}
