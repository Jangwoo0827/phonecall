package com.example.superdialer.dialer

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager

enum class PlaceCallResult { Placed, NoPermission, Unavailable }

object CallPlacer {
    fun placeCall(context: Context, number: String): PlaceCallResult {
        val telecom = context.getSystemService(TelecomManager::class.java)
            ?: return PlaceCallResult.Unavailable
        return try {
            telecom.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null), Bundle())
            PlaceCallResult.Placed
        } catch (e: SecurityException) {
            PlaceCallResult.NoPermission
        }
    }
}
