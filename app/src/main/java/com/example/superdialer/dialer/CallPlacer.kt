package com.example.superdialer.dialer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.telecom.VideoProfile

enum class PlaceCallResult { Placed, NoPermission, Unavailable }

object CallPlacer {
    /** Places a call; with [video] it asks Telecom for a two-way video call (the carrier may still connect it as voice). */
    fun placeCall(context: Context, number: String, video: Boolean = false): PlaceCallResult {
        val telecom = context.getSystemService(TelecomManager::class.java)
            ?: return PlaceCallResult.Unavailable
        val extras = Bundle()
        if (video) extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
        return try {
            telecom.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null), extras)
            PlaceCallResult.Placed
        } catch (e: SecurityException) {
            PlaceCallResult.NoPermission
        }
    }
}
