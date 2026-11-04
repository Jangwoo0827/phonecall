package com.example.superdialer.incall

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Videocam
import com.example.superdialer.ui.hasPermission
import android.Manifest
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.EditNote
import com.example.superdialer.ui.theme.EndCallRed
import com.example.superdialer.ui.theme.CallButtonGreen
import androidx.compose.ui.graphics.Brush
import android.telecom.DisconnectCause
import android.telecom.CallAudioState
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superdialer.dialer.PhoneNumberFormatter
import com.example.superdialer.settings.RejectMessageStore
import com.example.superdialer.ui.InitialAvatar
import kotlinx.coroutines.delay


@Composable
fun InCallScreen(onClose: () -> Unit, onAddCall: () -> Unit) {
    val calls by CallManager.snapshots.collectAsState()
    val audio by CallManager.audio.collectAsState()

    // Keep showing the last call as "통화 종료" for a moment after Telecom removes it.
    var lastCall by remember { mutableStateOf<CallSnapshot?>(null) }
    val primary = calls.primary()
    if (primary != null) lastCall = primary
    val ended = primary == null

    LaunchedEffect(ended) {
        if (ended) {
            delay(1200)
            onClose()
        }
    }

    val call = primary ?: lastCall
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2D22), Color(0xFF0B1210)))),
    ) {
        if (call == null) return@Box
        val others = calls.filter { it.id != call.id }

        // A video call that is on (or being set up) fills the screen with pictures.
        if (call.isVideo && !call.isRinging && !call.isHolding) {
            VideoCallLayout(call, audio, ended)
            if (call.upgradeRequest != 0) VideoUpgradeDialog(call)
            return@Box
        }
        if (call.upgradeRequest != 0 && !ended) VideoUpgradeDialog(call)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallHeader(call = call, ended = ended)
            others.firstOrNull { it.isHolding }?.let { held ->
                Spacer(Modifier.height(16.dp))
                HeldBanner(held)
            }
            Spacer(Modifier.weight(1f))
            when {
                ended -> Unit
                call.isRinging -> RingingControls(call)
                else -> ActiveControls(call, audio, onAddCall)
            }
        }
    }
}

/** Two soft rings that grow and fade behind the caller's avatar while the phone rings. */
@Composable
private fun RingingPulse() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "ring")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "ringPhase",
    )
    androidx.compose.foundation.Canvas(Modifier.size(220.dp)) {
        listOf(0f, 0.5f).forEach { offset ->
            val p = (phase + offset) % 1f
            drawCircle(
                color = Color(0xFF3DDC97).copy(alpha = (1f - p) * 0.35f),
                radius = size.minDimension / 2f * (0.55f + 0.45f * p),
            )
        }
    }
}

@Composable
private fun CallHeader(call: CallSnapshot, ended: Boolean) {
    val title = call.name
        ?: if (call.isConference) str(R.string.t_conference_call) else PhoneNumberFormatter.formatLoose(call.number).ifEmpty { str(R.string.t_no_caller_info) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = statusText(call, ended),
            color = Color(0xFFB0BEC5),
            fontSize = 16.sp,
        )
        if (call.isVideo && !ended) Text(VideoStates.label(call.videoState), color = Color(0xFF81C784), fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        Box(contentAlignment = Alignment.Center) {
            if (call.isRinging) RingingPulse()
            InitialAvatar(title, size = 120.dp, fontSize = 48.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (call.name != null) {
            Text(PhoneNumberFormatter.formatLoose(call.number), color = Color(0xFFB0BEC5), fontSize = 16.sp)
        }
    }
}

@Composable
private fun statusText(call: CallSnapshot, ended: Boolean): String {
    if (ended || call.isDisconnected) return str(R.string.t_call_ended)
    if (call.isRinging) return if (call.isVideo) str(R.string.t_incoming_video_call) else str(R.string.t_incoming_call)
    if (call.isHolding) return str(R.string.t_on_hold)
    if (call.isActive) {
        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(call.connectTimeMillis) {
            while (true) {
                now = System.currentTimeMillis()
                delay(500)
            }
        }
        return formatElapsed(now - call.connectTimeMillis)
    }
    return str(R.string.t_connecting)
}

private fun formatElapsed(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun HeldBanner(held: CallSnapshot) {
    val label = held.name ?: PhoneNumberFormatter.formatLoose(held.number).ifEmpty { str(R.string.t_no_caller_info) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E272E))
            .clickable { CallManager.swapTo(held.id) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(str(R.string.t_on_hold_2), color = Color(0xFFB0BEC5), fontSize = 12.sp)
            Text(label, color = Color.White, fontSize = 16.sp)
        }
        Text(str(R.string.t_flip), color = Color(0xFF81C784), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RingingControls(call: CallSnapshot) {
    var showMessages by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { showMessages = true }) {
            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, tint = Color.White)
            Text(str(R.string.t_decline_with_message), color = Color.White)
        }
        Spacer(Modifier.height(24.dp))
        val context = LocalContext.current
        // answering with video needs the camera: ask first, and fall back to a voice answer if it is refused
        val cameraPermission = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                CallManager.answerVideo(call.id)
            } else {
                Toast.makeText(context, str(R.string.t_no_camera_permission_so_the_call), Toast.LENGTH_SHORT).show()
                CallManager.answer(call.id)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundButton(Icons.Filled.CallEnd, str(R.string.t_declined), EndCallRed) { CallManager.reject(call.id) }
            if (call.isVideo) {
                RoundButton(Icons.Filled.Call, str(R.string.t_voice), Color(0xFF455A64)) { CallManager.answer(call.id) }
                RoundButton(Icons.Filled.Videocam, str(R.string.t_video), CallButtonGreen) {
                    if (context.hasPermission(Manifest.permission.CAMERA)) CallManager.answerVideo(call.id)
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                }
            } else {
                RoundButton(Icons.Filled.Call, str(R.string.t_answer), CallButtonGreen) { CallManager.answer(call.id) }
            }
        }
    }
    if (showMessages) {
        RejectMessageDialog(
            onSelect = { message ->
                showMessages = false
                CallManager.reject(call.id, message)
            },
            onDismiss = { showMessages = false },
        )
    }
}

private fun routeLabel(route: Int) = when (route) {
    CallAudioState.ROUTE_SPEAKER -> str(R.string.t_speaker)
    CallAudioState.ROUTE_BLUETOOTH -> str(R.string.t_bluetooth)
    CallAudioState.ROUTE_WIRED_HEADSET -> str(R.string.t_headset)
    else -> str(R.string.t_earpiece)
}

/** Pick where the call audio goes: earpiece, speaker, bluetooth or a wired headset (only the ones available). */
@Composable
private fun AudioRouteDialog(audio: AudioInfo, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        CallAudioState.ROUTE_EARPIECE to str(R.string.t_earpiece),
        CallAudioState.ROUTE_SPEAKER to str(R.string.t_speaker),
        CallAudioState.ROUTE_BLUETOOTH to str(R.string.t_bluetooth),
        CallAudioState.ROUTE_WIRED_HEADSET to str(R.string.t_wired_headset),
    ).filter { audio.supportedRoutes and it.first != 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.t_sound_output)) },
        text = {
            Column {
                options.forEach { (route, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onPick(route) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = audio.route == route, onClick = { onPick(route) })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str(R.string.t_close)) } },
    )
}

@Composable
private fun RejectMessageDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.t_decline_with_message_2)) },
        text = {
            Column {
                if (RejectMessageStore.messages.isEmpty()) {
                    Text(str(R.string.t_no_decline_messages_yet_add_some))
                }
                RejectMessageStore.messages.forEach { message ->
                    Text(
                        text = message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(message) }
                            .padding(vertical = 12.dp),
                    )
                }
                // Write a one-off message instead of a preset.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        placeholder = { Text(str(R.string.t_write_your_own)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = custom.isNotBlank(), onClick = { onSelect(custom.trim()) }) { Text(str(R.string.t_send)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}

@Composable
private fun ActiveControls(call: CallSnapshot, audio: AudioInfo, onAddCall: () -> Unit) {
    val context = LocalContext.current
    var showKeypad by remember { mutableStateOf(false) }
    var showMemo by remember { mutableStateOf(false) }
    // Microphone recording of the call: Android blocks real call recording, so this hears your side, and the other
    // side only on speaker (said in the toast and in Settings).
    fun startRecording() {
        if (CallRecorder.start(context, call.name ?: call.number)) {
            Toast.makeText(context, str(R.string.t_recording_with_the_microphone_the_othe), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, str(R.string.t_couldn_t_start_recording), Toast.LENGTH_SHORT).show()
        }
    }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording() else Toast.makeText(context, str(R.string.t_recording_needs_the_microphone_permiss), Toast.LENGTH_SHORT).show()
    }
    val videoPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) CallManager.requestVideo(call.id) else Toast.makeText(context, str(R.string.t_video_calls_need_the_camera_permission_2), Toast.LENGTH_SHORT).show()
    }
    // A recording never outlives its call.
    androidx.compose.runtime.DisposableEffect(call.id) { onDispose { CallRecorder.stop(context) } }
    var showRoutes by remember { mutableStateOf(false) }
    val speakerOn = audio.route == CallAudioState.ROUTE_SPEAKER

    if (showRoutes) {
        AudioRouteDialog(audio, onPick = { CallManager.setRoute(it); showRoutes = false }, onDismiss = { showRoutes = false })
    }
    if (showMemo) {
        com.example.superdialer.calllog.NoteDialog(
            title = str(R.string.t_call_note),
            initial = com.example.superdialer.calllog.CallNotes.duringCall(call.number, call.connectTimeMillis)?.text.orEmpty(),
            onSave = { com.example.superdialer.calllog.CallNotes.saveDuringCall(call.number, call.connectTimeMillis, it) },
            onDismiss = { showMemo = false },
        )
    }

    if (showKeypad) {
        InCallKeypad(call.id, onHide = { showKeypad = false })
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToggleAction(if (audio.muted) Icons.Filled.MicOff else Icons.Filled.Mic, str(R.string.t_mute), audio.muted) {
                CallManager.setMuted(!audio.muted)
            }
            ToggleAction(Icons.Filled.Dialpad, str(R.string.t_keypad), false) { showKeypad = true }
            val extraRoutes = audio.supportedRoutes and (CallAudioState.ROUTE_BLUETOOTH or CallAudioState.ROUTE_WIRED_HEADSET) != 0
            ToggleAction(
                icon = when (audio.route) {
                    CallAudioState.ROUTE_BLUETOOTH -> Icons.Filled.Bluetooth
                    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Filled.Headset
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                },
                label = if (extraRoutes) routeLabel(audio.route) else str(R.string.t_speaker),
                active = speakerOn || (extraRoutes && audio.route != CallAudioState.ROUTE_EARPIECE),
            ) { if (extraRoutes) showRoutes = true else CallManager.setSpeaker(!speakerOn) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToggleAction(Icons.Filled.Add, str(R.string.t_add_call), false, onClick = onAddCall)
            ToggleAction(
                icon = if (call.isHolding) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                label = if (call.isHolding) str(R.string.t_resume) else str(R.string.t_hold),
                active = call.isHolding,
                enabled = call.canHold,
            ) { if (call.isHolding) CallManager.unhold(call.id) else CallManager.hold(call.id) }
            if (call.canMerge) {
                ToggleAction(Icons.AutoMirrored.Filled.CallMerge, str(R.string.t_merge_2), false) { CallManager.merge(call.id) }
            } else {
                ToggleAction(Icons.Filled.EditNote, str(R.string.t_note_2), false, enabled = call.number.isNotEmpty()) { showMemo = true }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (call.canMerge) {
                ToggleAction(Icons.Filled.EditNote, str(R.string.t_note_2), false, enabled = call.number.isNotEmpty()) { showMemo = true }
            }
            if (call.canVideo && !call.isVideo) {
                ToggleAction(Icons.Filled.Videocam, str(R.string.t_switch_to_video), false) {
                    if (context.hasPermission(Manifest.permission.CAMERA)) CallManager.requestVideo(call.id)
                    else videoPermission.launch(Manifest.permission.CAMERA)
                }
            }
            ToggleAction(Icons.Filled.FiberManualRecord, if (CallRecorder.recording) str(R.string.t_recording) else str(R.string.t_record), CallRecorder.recording) {
                if (CallRecorder.recording) {
                    CallRecorder.stop(context)
                    Toast.makeText(context, str(R.string.t_recording_saved_my_files_music_superdi), Toast.LENGTH_LONG).show()
                } else if (context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    startRecording()
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        RoundButton(Icons.Filled.CallEnd, str(R.string.t_end), EndCallRed) { CallManager.disconnect(call.id) }
    }
}

@Composable
private fun InCallKeypad(callId: Int, onHide: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val rows = listOf("123", "456", "789", "*0#")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(typed, color = Color.White, fontSize = 28.sp, maxLines = 1, modifier = Modifier.height(40.dp))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF263238))
                            .pointerInput(key) {
                                detectTapGestures(
                                    onPress = {
                                        CallManager.playDtmf(callId, key)
                                        try {
                                            tryAwaitRelease()
                                        } finally {
                                            CallManager.stopDtmf(callId)
                                        }
                                    },
                                    onTap = { typed += key },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(key.toString(), color = Color.White, fontSize = 28.sp) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onHide) { Text(str(R.string.t_hide), color = Color.White) }
            RoundButton(Icons.Filled.CallEnd, str(R.string.t_end), EndCallRed) { CallManager.disconnect(callId) }
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(color)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(32.dp)) }
        Text(label, color = Color.White, modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp)
    }
}

@Composable
private fun ToggleAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (active) Color.White else Color(0xFF263238))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = when {
                    !enabled -> Color(0xFF546E7A)
                    active -> Color.Black
                    else -> Color.White
                },
            )
        }
        Text(
            label,
            color = if (enabled) Color.White else Color(0xFF546E7A),
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 13.sp,
        )
    }
}

// --- Video calls ---------------------------------------------------------------------------------

/** The other side's picture full screen, ours small in a corner, and the controls of a video call. */
@Composable
private fun VideoCallLayout(call: CallSnapshot, audio: AudioInfo, ended: Boolean) {
    val context = LocalContext.current
    val front by CallManager.frontCamera.collectAsState()
    val peerSize by CallManager.peerSize.collectAsState()
    val sending = VideoStates.sendsVideo(call.videoState)
    val title = call.name ?: PhoneNumberFormatter.formatLoose(call.number).ifEmpty { str(R.string.t_no_caller_info) }

    // The camera needs its permission; ask once when a video call is up, then pick the front camera.
    val cameraPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) CallManager.useCamera(context, call.id, front = true)
        else Toast.makeText(context, str(R.string.t_sending_your_video_needs_the_camera), Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(call.id) {
        if (context.hasPermission(Manifest.permission.CAMERA)) CallManager.useCamera(context, call.id, front = true)
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).testTag("video_call_screen")) {
        // The other side's picture keeps its shape (letterboxed) when Telecom told us its size.
        val remoteModifier = peerSize?.let { (w, h) -> if (w > 0 && h > 0) Modifier.fillMaxSize().aspectRatio(w.toFloat() / h, matchHeightConstraintsFirst = false) else null }
            ?: Modifier.fillMaxSize()
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            VideoSurface(remoteModifier) { CallManager.setDisplaySurface(call.id, it) }
        }
        if (!VideoStates.receivesVideo(call.videoState)) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                InitialAvatar(title, size = 120.dp, fontSize = 48.sp)
                Text(str(R.string.t_waiting_for_the_other_side_s), color = Color(0xFFB0BEC5), modifier = Modifier.padding(top = 12.dp))
            }
        }

        // Our own picture
        if (sending && !ended) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 96.dp, end = 16.dp)
                    .size(width = 108.dp, height = 156.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF263238)),
            ) { VideoSurface(Modifier.fillMaxSize()) { CallManager.setPreviewSurface(call.id, it) } }
        }

        // Name and time on top
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))
                .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 24.dp),
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(statusText(call, ended), color = Color(0xFFCFD8DC), fontSize = 15.sp)
        }

        // Controls at the bottom
        if (!ended) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(horizontal = 16.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ToggleAction(if (audio.muted) Icons.Filled.MicOff else Icons.Filled.Mic, str(R.string.t_mute), audio.muted) {
                        CallManager.setMuted(!audio.muted)
                    }
                    ToggleAction(if (sending) Icons.Filled.Videocam else Icons.Filled.VideocamOff, if (sending) str(R.string.t_camera_off) else str(R.string.t_camera_on), !sending) {
                        CallManager.setCameraOn(call.id, !sending)
                    }
                    ToggleAction(Icons.Filled.Cameraswitch, str(R.string.t_flip), false, enabled = sending) { CallManager.switchCamera(context, call.id) }
                    val speakerOn = audio.route == CallAudioState.ROUTE_SPEAKER
                    ToggleAction(Icons.AutoMirrored.Filled.VolumeUp, str(R.string.t_speaker), speakerOn) { CallManager.setSpeaker(!speakerOn) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToggleAction(Icons.Filled.Call, str(R.string.t_voice), false) { CallManager.switchToVoice(call.id) }
                    RoundButton(Icons.Filled.CallEnd, str(R.string.t_end), EndCallRed) { CallManager.disconnect(call.id) }
                }
            }
        }
    }
}

/** A surface the video call draws into. The surface is handed to Telecom while it exists and taken back when it goes. */
@Composable
private fun VideoSurface(modifier: Modifier, onSurface: (android.view.Surface?) -> Unit) {
    val latest by androidx.compose.runtime.rememberUpdatedState(onSurface)
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { context ->
            android.view.TextureView(context).apply {
                keepScreenOn = true
                surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                    private var surface: android.view.Surface? = null
                    override fun onSurfaceTextureAvailable(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        surface = android.view.Surface(texture).also { latest(it) }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: android.graphics.SurfaceTexture, width: Int, height: Int) {}

                    override fun onSurfaceTextureDestroyed(texture: android.graphics.SurfaceTexture): Boolean {
                        latest(null)
                        surface?.release()
                        surface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(texture: android.graphics.SurfaceTexture) {}
                }
            }
        },
    )
}

/** The other side asks to switch to video: accept (the camera permission is asked first) or keep the call as it is. */
@Composable
private fun VideoUpgradeDialog(call: CallSnapshot) {
    val context = LocalContext.current
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            CallManager.respondToUpgrade(call.id, accept = true)
        } else {
            Toast.makeText(context, str(R.string.t_video_calls_need_the_camera_permission_2), Toast.LENGTH_SHORT).show()
            CallManager.respondToUpgrade(call.id, accept = false)
        }
    }
    AlertDialog(
        onDismissRequest = { CallManager.respondToUpgrade(call.id, accept = false) },
        title = { Text(str(R.string.t_video_call_request)) },
        text = { Text(str(R.string.t_the_other_person_wants_to_switch)) },
        confirmButton = {
            TextButton(onClick = {
                if (context.hasPermission(Manifest.permission.CAMERA)) CallManager.respondToUpgrade(call.id, accept = true)
                else permission.launch(Manifest.permission.CAMERA)
            }) { Text(str(R.string.t_accept)) }
        },
        dismissButton = { TextButton(onClick = { CallManager.respondToUpgrade(call.id, accept = false) }) { Text(str(R.string.t_declined)) } },
    )
}
