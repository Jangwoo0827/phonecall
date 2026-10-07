package com.example.superdialer.incall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the Decline / Hang up buttons of the call notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            CallNotifications.ACTION_DECLINE -> CallManager.rejectRinging()
            CallNotifications.ACTION_HANGUP ->
                CallManager.snapshots.value.firstOrNull { !it.isRinging }?.let { CallManager.disconnect(it.id) }
        }
    }
}
