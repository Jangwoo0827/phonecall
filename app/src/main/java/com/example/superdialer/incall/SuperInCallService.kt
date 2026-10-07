package com.example.superdialer.incall

import android.content.Intent
import android.os.IBinder
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Receives calls from Telecom once the app holds the default Phone role.
 * Incoming calls surface through a full-screen notification; outgoing calls open the UI directly.
 */
class SuperInCallService : InCallService() {

    override fun onBind(intent: Intent?): IBinder? {
        CallManager.service = this
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        CallManager.service = null
        return super.onUnbind(intent)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(this, call)
        val incoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
        if (!incoming) {
            startActivity(InCallActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(call)
    }

    @Deprecated("Deprecated in Android 14; still delivered on all supported versions")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.onAudioStateChanged(audioState)
    }
}
