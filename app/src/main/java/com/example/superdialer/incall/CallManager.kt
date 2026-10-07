package com.example.superdialer.incall

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CallSnapshot(
    val id: Int,
    val number: String,
    val name: String?,
    val state: Int,
    val incoming: Boolean,
    val connectTimeMillis: Long,
    val canHold: Boolean,
    val disconnectCode: Int,
) {
    val isRinging get() = state == Call.STATE_RINGING
    val isActive get() = state == Call.STATE_ACTIVE
    val isHolding get() = state == Call.STATE_HOLDING
    val isDisconnected get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING
    val isDialing get() = !isRinging && !isActive && !isHolding && !isDisconnected
}

data class AudioInfo(val muted: Boolean, val route: Int, val bluetoothAvailable: Boolean)

/** Bridges the system's [InCallService] callbacks to the Compose in-call UI. Main-thread only. */
object CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val calls = LinkedHashMap<Int, Call>()
    private val names = HashMap<String, String?>()
    private var appContext: Context? = null

    private val _snapshots = MutableStateFlow<List<CallSnapshot>>(emptyList())
    val snapshots: StateFlow<List<CallSnapshot>> = _snapshots.asStateFlow()

    private val _audio = MutableStateFlow(AudioInfo(muted = false, route = CallAudioState.ROUTE_EARPIECE, bluetoothAvailable = false))
    val audio: StateFlow<AudioInfo> = _audio.asStateFlow()

    var service: InCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
    }

    fun onCallAdded(context: Context, call: Call) {
        appContext = context.applicationContext
        calls[call.key()] = call
        call.registerCallback(callback)
        resolveName(call.number())
        publish()
    }

    fun onCallRemoved(call: Call) {
        val context = appContext
        val number = call.number()
        if (context != null && call.details.disconnectCause?.code == DisconnectCause.MISSED) {
            CallNotifications.showMissed(context, number, names[number])
        }
        call.unregisterCallback(callback)
        calls.remove(call.key())
        publish()
    }

    @Suppress("DEPRECATION")
    fun onAudioStateChanged(state: CallAudioState) {
        _audio.value = AudioInfo(
            muted = state.isMuted,
            route = state.route,
            bluetoothAvailable = state.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0,
        )
    }

    // --- Actions -----------------------------------------------------------------------------

    fun answer(id: Int) = calls[id]?.answer(VideoProfile.STATE_AUDIO_ONLY)

    fun answerRinging() {
        _snapshots.value.firstOrNull { it.isRinging }?.let { answer(it.id) }
    }

    fun rejectRinging(message: String? = null) {
        _snapshots.value.firstOrNull { it.isRinging }?.let { reject(it.id, message) }
    }

    fun reject(id: Int, message: String? = null) = calls[id]?.reject(message != null, message)

    fun disconnect(id: Int) = calls[id]?.disconnect()

    fun hold(id: Int) = calls[id]?.hold()

    fun unhold(id: Int) = calls[id]?.unhold()

    fun playDtmf(id: Int, digit: Char) {
        calls[id]?.playDtmfTone(digit)
    }

    fun stopDtmf(id: Int) {
        calls[id]?.stopDtmfTone()
    }

    fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean) {
        val route = if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        service?.setAudioRoute(route)
    }

    // --- Internals ---------------------------------------------------------------------------

    private fun Call.key() = System.identityHashCode(this)

    private fun Call.number(): String = details.handle?.schemeSpecificPart.orEmpty()

    @Suppress("DEPRECATION")
    private fun Call.toSnapshot(): CallSnapshot {
        val number = number()
        return CallSnapshot(
            id = key(),
            number = number,
            name = names[number]?.takeIf { it.isNotBlank() }
                ?: details.callerDisplayName?.takeIf { it.isNotBlank() },
            state = state,
            incoming = details.callDirection == Call.Details.DIRECTION_INCOMING,
            connectTimeMillis = details.connectTimeMillis,
            canHold = details.can(Call.Details.CAPABILITY_HOLD),
            disconnectCode = details.disconnectCause?.code ?: DisconnectCause.UNKNOWN,
        )
    }

    private fun publish() {
        val list = calls.values.map { it.toSnapshot() }
        _snapshots.value = list
        appContext?.let { CallNotifications.update(it, list) }
    }

    private fun resolveName(number: String) {
        val context = appContext ?: return
        if (number.isEmpty() || names.containsKey(number)) return
        names[number] = null
        scope.launch {
            val name = withContext(Dispatchers.IO) { lookup(context, number) }
            if (name != null) {
                names[number] = name
                publish()
            }
        }
    }

    private fun lookup(context: Context, number: String): String? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver
            .query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: SecurityException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

/** Ringing call first, then the one in progress, then held calls. */
fun List<CallSnapshot>.primary(): CallSnapshot? =
    firstOrNull { it.isRinging } ?: firstOrNull { it.isActive || it.isDialing } ?: firstOrNull()
