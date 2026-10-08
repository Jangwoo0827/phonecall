package com.example.superdialer.incall

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
        ?: if (call.isConference) "다자간 통화" else PhoneNumberFormatter.formatLoose(call.number).ifEmpty { "번호정보 없음" }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = statusText(call, ended),
            color = Color(0xFFB0BEC5),
            fontSize = 16.sp,
        )
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
    if (ended || call.isDisconnected) return "통화 종료"
    if (call.isRinging) return "수신 전화"
    if (call.isHolding) return "통화 대기 중"
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
    return "연결 중..."
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
    val label = held.name ?: PhoneNumberFormatter.formatLoose(held.number).ifEmpty { "번호정보 없음" }
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
            Text("보류 중", color = Color(0xFFB0BEC5), fontSize = 12.sp)
            Text(label, color = Color.White, fontSize = 16.sp)
        }
        Text("전환", color = Color(0xFF81C784), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RingingControls(call: CallSnapshot) {
    var showMessages by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { showMessages = true }) {
            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, tint = Color.White)
            Text("  메시지로 거절", color = Color.White)
        }
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundButton(Icons.Filled.CallEnd, "거절", EndCallRed) { CallManager.reject(call.id) }
            RoundButton(Icons.Filled.Call, "받기", CallButtonGreen) { CallManager.answer(call.id) }
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
    CallAudioState.ROUTE_SPEAKER -> "스피커"
    CallAudioState.ROUTE_BLUETOOTH -> "블루투스"
    CallAudioState.ROUTE_WIRED_HEADSET -> "헤드셋"
    else -> "수화기"
}

/** Pick where the call audio goes: earpiece, speaker, bluetooth or a wired headset (only the ones available). */
@Composable
private fun AudioRouteDialog(audio: AudioInfo, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        CallAudioState.ROUTE_EARPIECE to "수화기",
        CallAudioState.ROUTE_SPEAKER to "스피커",
        CallAudioState.ROUTE_BLUETOOTH to "블루투스",
        CallAudioState.ROUTE_WIRED_HEADSET to "유선 헤드셋",
    ).filter { audio.supportedRoutes and it.first != 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("소리 출력") },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun RejectMessageDialog(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("메시지로 거절") },
        text = {
            Column {
                if (RejectMessageStore.messages.isEmpty()) {
                    Text("등록된 거절 메시지가 없습니다. 설정에서 추가해 주세요.")
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
                        placeholder = { Text("직접 입력") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = custom.isNotBlank(), onClick = { onSelect(custom.trim()) }) { Text("보내기") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("취소") } },
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
            Toast.makeText(context, "마이크로 녹음을 시작했습니다. 상대 목소리는 스피커를 켰을 때만 담깁니다.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "녹음을 시작하지 못했습니다.", Toast.LENGTH_SHORT).show()
        }
    }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording() else Toast.makeText(context, "녹음하려면 마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
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
            title = "통화 메모",
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
            ToggleAction(if (audio.muted) Icons.Filled.MicOff else Icons.Filled.Mic, "음소거", audio.muted) {
                CallManager.setMuted(!audio.muted)
            }
            ToggleAction(Icons.Filled.Dialpad, "키패드", false) { showKeypad = true }
            val extraRoutes = audio.supportedRoutes and (CallAudioState.ROUTE_BLUETOOTH or CallAudioState.ROUTE_WIRED_HEADSET) != 0
            ToggleAction(
                icon = when (audio.route) {
                    CallAudioState.ROUTE_BLUETOOTH -> Icons.Filled.Bluetooth
                    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Filled.Headset
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                },
                label = if (extraRoutes) routeLabel(audio.route) else "스피커",
                active = speakerOn || (extraRoutes && audio.route != CallAudioState.ROUTE_EARPIECE),
            ) { if (extraRoutes) showRoutes = true else CallManager.setSpeaker(!speakerOn) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToggleAction(Icons.Filled.Add, "통화 추가", false, onClick = onAddCall)
            ToggleAction(
                icon = if (call.isHolding) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                label = if (call.isHolding) "보류 해제" else "보류",
                active = call.isHolding,
                enabled = call.canHold,
            ) { if (call.isHolding) CallManager.unhold(call.id) else CallManager.hold(call.id) }
            if (call.canMerge) {
                ToggleAction(Icons.AutoMirrored.Filled.CallMerge, "병합", false) { CallManager.merge(call.id) }
            } else {
                ToggleAction(Icons.Filled.EditNote, "메모", false, enabled = call.number.isNotEmpty()) { showMemo = true }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (call.canMerge) {
                ToggleAction(Icons.Filled.EditNote, "메모", false, enabled = call.number.isNotEmpty()) { showMemo = true }
            }
            ToggleAction(Icons.Filled.FiberManualRecord, if (CallRecorder.recording) "녹음 중" else "녹음", CallRecorder.recording) {
                if (CallRecorder.recording) {
                    CallRecorder.stop(context)
                    Toast.makeText(context, "녹음을 저장했습니다 (내 파일 > 음악 > SuperDialer)", Toast.LENGTH_LONG).show()
                } else if (context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    startRecording()
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        RoundButton(Icons.Filled.CallEnd, "종료", EndCallRed) { CallManager.disconnect(call.id) }
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
            TextButton(onClick = onHide) { Text("숨기기", color = Color.White) }
            RoundButton(Icons.Filled.CallEnd, "종료", EndCallRed) { CallManager.disconnect(callId) }
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
