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
    /** Telecom can merge this call with the held one into a conference. */
    val canMerge: Boolean = false,
    /** This is a conference (several parties on one call). */
    val isConference: Boolean = false,
    /** Telecom's video state of the call (see [VideoStates]); for a ringing call it is what the caller offers. */
    val videoState: Int = VideoStates.AUDIO_ONLY,
    /** The line and network can carry video, so a voice call may be switched to video. */
    val canVideo: Boolean = false,
    /** The other side asked to switch this call to this video state and waits for our answer (0 = no request). */
    val upgradeRequest: Int = 0,
) {
    val isVideo get() = VideoStates.hasVideo(videoState)
    val isRinging get() = state == Call.STATE_RINGING
    val isActive get() = state == Call.STATE_ACTIVE
    val isHolding get() = state == Call.STATE_HOLDING
    val isDisconnected get() = state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING
    val isDialing get() = !isRinging && !isActive && !isHolding && !isDisconnected
}

data class AudioInfo(val muted: Boolean, val route: Int, val bluetoothAvailable: Boolean, val supportedRoutes: Int = 0)

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

    // --- Video -------------------------------------------------------------------------------
    private val videoCallbacks = HashMap<Int, InCallService.VideoCall.Callback>()
    private val upgradeRequests = HashMap<Int, Int>()

    private val _frontCamera = MutableStateFlow(true)
    /** Which camera is sent to the other side. */
    val frontCamera: StateFlow<Boolean> = _frontCamera.asStateFlow()

    private val _peerSize = MutableStateFlow<Pair<Int, Int>?>(null)
    /** Width and height of the other side's picture, when Telecom told us. */
    val peerSize: StateFlow<Pair<Int, Int>?> = _peerSize.asStateFlow()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        override fun onVideoCallChanged(call: Call, videoCall: InCallService.VideoCall?) = watchVideo(call, videoCall)
    }

    fun onCallAdded(context: Context, call: Call) {
        appContext = context.applicationContext
        calls[call.key()] = call
        call.registerCallback(callback)
        watchVideo(call, call.videoCall)
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
        videoCallbacks.remove(call.key())?.let { call.videoCall?.unregisterCallback(it) }
        upgradeRequests.remove(call.key())
        calls.remove(call.key())
        publish()
    }

    @Suppress("DEPRECATION")
    fun onAudioStateChanged(state: CallAudioState) {
        _audio.value = AudioInfo(
            muted = state.isMuted,
            route = state.route,
            bluetoothAvailable = state.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0,
            supportedRoutes = state.supportedRouteMask,
        )
    }

    // --- Actions -----------------------------------------------------------------------------

    fun answer(id: Int) = calls[id]?.answer(VideoProfile.STATE_AUDIO_ONLY)

    /** Answers a call that offers video with video (what the caller offered); an audio-only call is just answered. */
    fun answerVideo(id: Int) {
        val call = calls[id] ?: return
        val offered = call.details.videoState
        call.answer(if (VideoStates.hasVideo(offered)) offered else VideoProfile.STATE_AUDIO_ONLY)
    }

    fun answerRinging() {
        _snapshots.value.firstOrNull { it.isRinging }?.let { answer(it.id) }
    }

    fun answerRingingWithVideo() {
        _snapshots.value.firstOrNull { it.isRinging }?.let { answerVideo(it.id) }
    }

    /** Asks to turn a voice call into a video call; the other side must accept. */
    fun requestVideo(id: Int) {
        calls[id]?.videoCall?.sendSessionModifyRequest(VideoProfile(VideoStates.BIDIRECTIONAL))
    }

    /** Back to voice only. */
    fun switchToVoice(id: Int) {
        calls[id]?.videoCall?.sendSessionModifyRequest(VideoProfile(VideoStates.AUDIO_ONLY))
    }

    /** Camera on (send our picture) or off (keep seeing the other side). */
    fun setCameraOn(id: Int, on: Boolean) {
        calls[id]?.videoCall?.sendSessionModifyRequest(VideoProfile(VideoStates.cameraTarget(on)))
    }

    /** Answers the other side's request to switch this call to video. Refusing keeps the call as it is. */
    fun respondToUpgrade(id: Int, accept: Boolean) {
        val call = calls[id] ?: return
        val requested = upgradeRequests.remove(id) ?: return
        call.videoCall?.sendSessionModifyResponse(VideoProfile(if (accept) requested else call.details.videoState))
        publish()
    }

    fun setDisplaySurface(id: Int, surface: android.view.Surface?) {
        calls[id]?.videoCall?.setDisplaySurface(surface)
    }

    fun setPreviewSurface(id: Int, surface: android.view.Surface?) {
        calls[id]?.videoCall?.setPreviewSurface(surface)
    }

    /** Selects the front or back camera for the call (needs the camera permission). */
    fun useCamera(context: Context, id: Int, front: Boolean) {
        val videoCall = calls[id]?.videoCall ?: return
        val cameraId = cameraIdFor(context, front) ?: return
        try {
            videoCall.setCamera(cameraId)
            videoCall.setDeviceOrientation(0)
            _frontCamera.value = front
        } catch (e: SecurityException) {
            // no camera permission yet: the screen asks and calls this again
        }
    }

    fun switchCamera(context: Context, id: Int) = useCamera(context, id, !_frontCamera.value)

    private fun cameraIdFor(context: Context, front: Boolean): String? = try {
        val manager = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
        val want = if (front) android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT else android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
        manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == want }
    } catch (e: Exception) {
        null
    }

    private fun watchVideo(call: Call, videoCall: InCallService.VideoCall?) {
        val key = call.key()
        videoCallbacks.remove(key)?.let { old -> videoCall?.unregisterCallback(old) }
        if (videoCall == null) return
        val watcher = object : InCallService.VideoCall.Callback() {
            override fun onSessionModifyRequestReceived(videoProfile: VideoProfile) {
                // the other side wants video (or to change it): ask the user, unless it is a step back to voice
                if (VideoStates.hasVideo(videoProfile.videoState)) upgradeRequests[key] = videoProfile.videoState
                publish()
            }

            override fun onSessionModifyResponseReceived(status: Int, requestedProfile: VideoProfile?, responseProfile: VideoProfile?) = publish()
            override fun onCallSessionEvent(event: Int) {}
            override fun onPeerDimensionsChanged(width: Int, height: Int) {
                _peerSize.value = width to height
            }

            override fun onCallDataUsageChanged(dataUsage: Long) {}
            override fun onCameraCapabilitiesChanged(cameraCapabilities: VideoProfile.CameraCapabilities?) {}
            override fun onVideoQualityChanged(videoQuality: Int) {}
        }
        videoCallbacks[key] = watcher
        videoCall.registerCallback(watcher)
    }

    fun rejectRinging(message: String? = null) {
        _snapshots.value.firstOrNull { it.isRinging }?.let { reject(it.id, message) }
    }

    fun reject(id: Int, message: String? = null) = calls[id]?.reject(message != null, message)

    fun disconnect(id: Int) = calls[id]?.disconnect()

    fun hold(id: Int) = calls[id]?.hold()

    fun unhold(id: Int) = calls[id]?.unhold()

    /** Swaps to a held call; Telecom puts the active one on hold automatically. */
    fun swapTo(id: Int) = calls[id]?.unhold()

    /** Merges the call with another conferenceable call (or folds a held call into its conference). */
    fun merge(id: Int) {
        val call = calls[id] ?: return
        val other = call.conferenceableCalls.firstOrNull()
        if (other != null) call.conference(other) else call.mergeConference()
    }

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

    /** Moves the call audio to one of the CallAudioState.ROUTE_* values (earpiece, bluetooth, wired headset, speaker). */
    @Suppress("DEPRECATION")
    fun setRoute(route: Int) {
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
            canMerge = details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) || conferenceableCalls.isNotEmpty(),
            isConference = details.hasProperty(Call.Details.PROPERTY_CONFERENCE),
            videoState = details.videoState,
            canVideo = videoCall != null && details.can(Call.Details.CAPABILITY_SUPPORTS_VT_LOCAL_BIDIRECTIONAL),
            upgradeRequest = upgradeRequests[key()] ?: 0,
        )
    }

    private fun publish() {
        // Members of a conference are shown through their parent call only.
        val list = calls.values.filter { it.parent == null }.map { it.toSnapshot() }
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
