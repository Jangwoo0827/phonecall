package com.example.superdialer.dialer

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import com.example.superdialer.ui.theme.CallButtonGreen
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.superdialer.settings.AppSettings
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.superdialer.ui.DefaultDialerBanner
import com.example.superdialer.ui.InitialAvatar
import com.example.superdialer.ui.addContact

private data class Key(val char: Char, val sub: String = "")

private val keyRows = listOf(
    listOf(Key('1'), Key('2', "ABC"), Key('3', "DEF")),
    listOf(Key('4', "GHI"), Key('5', "JKL"), Key('6', "MNO")),
    listOf(Key('7', "PQRS"), Key('8', "TUV"), Key('9', "WXYZ")),
    listOf(Key('*'), Key('0', "+"), Key('#')),
)

@Composable
fun DialerScreen(
    viewModel: DialerViewModel,
    modifier: Modifier = Modifier,
    suggestions: List<Suggestion> = emptyList(),
    onRefreshSources: () -> Unit = {},
    dtmfEnabled: Boolean = AppSettings.dtmfEnabled,
) {
    val context = LocalContext.current
    val number = viewModel.number

    // Contacts / recent calls feed the autocomplete; they may have become readable since last time.
    LifecycleResumeEffect(Unit) {
        onRefreshSources()
        onPauseOrDispose { }
    }

    val dtmf = remember { DtmfPlayer() }
    dtmf.enabled = dtmfEnabled
    DisposableEffect(dtmf) { onDispose { dtmf.release() } }

    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var callUnavailable by rememberSaveable { mutableStateOf(false) }

    fun placeCall() {
        when (CallPlacer.placeCall(context, number)) {
            PlaceCallResult.Placed -> { permissionDenied = false; callUnavailable = false }
            PlaceCallResult.NoPermission -> permissionDenied = true
            PlaceCallResult.Unavailable -> callUnavailable = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) placeCall() else permissionDenied = true
    }

    fun onCallClick() {
        if (number.isEmpty()) return
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) placeCall() else permissionLauncher.launch(Manifest.permission.CALL_PHONE)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DefaultDialerBanner()

        NumberDisplay(
            number = number,
            onBackspace = viewModel::backspace,
            onClear = viewModel::clear,
        )

        // Always the same height, so the keypad below keeps its size whether or not anything matches.
        SuggestionStrip(
            suggestions = suggestions,
            onPick = { viewModel.replaceNumber(it.number) },
            showAddContact = number.length >= 3 && suggestions.none { it.fromContact },
            onAddContact = { context.addContact(number) },
            modifier = Modifier.fillMaxWidth().height(SUGGESTION_SLOT_HEIGHT),
        )

        if (permissionDenied) {
            Text(
                text = "전화를 걸려면 '전화' 권한이 필요합니다.\n설정에서 권한을 허용해 주세요.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
            }) { Text("설정 열기") }
        }
        if (callUnavailable) {
            Text(
                text = "이 기기에서는 전화를 걸 수 없습니다.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            keyRows.forEach { row ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    row.forEach { key ->
                        DialKey(
                            key = key,
                            onPressStart = dtmf::start,
                            onPressEnd = dtmf::stop,
                            onTap = viewModel::append,
                            onLongPress = { if (it == '0') viewModel.appendPlus() },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .size(80.dp)
                .alpha(if (number.isEmpty()) 0.4f else 1f)
                .shadow(if (number.isEmpty()) 0.dp else 8.dp, CircleShape)
                .clip(CircleShape)
                .background(CallButtonGreen)
                .pointerInput(number) { detectTapGestures(onTap = { onCallClick() }) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = "통화",
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun NumberDisplay(
    number: String,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    val formatted = PhoneNumberFormatter.format(number)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp))
        // Starts big and steps down until the whole number fits on one line (never cut off).
        var fontSize by remember(formatted) { mutableStateOf(42.sp) }
        Text(
            text = formatted,
            modifier = Modifier.weight(1f),
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            onTextLayout = { result ->
                if (result.didOverflowWidth && fontSize.value > 16f) fontSize = (fontSize.value - 2f).sp
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onBackspace() }, onLongPress = { onClear() })
                },
            contentAlignment = Alignment.Center,
        ) {
            if (number.isNotEmpty()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "지우기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DialKey(
    key: Key,
    onPressStart: (Char) -> Unit,
    onPressEnd: () -> Unit,
    onTap: (Char) -> Unit,
    onLongPress: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    val container = MaterialTheme.colorScheme.surfaceContainer
    val pressedContainer = MaterialTheme.colorScheme.primaryContainer

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val diameter = min(maxWidth, maxHeight) - 8.dp
        Box(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(if (pressed) pressedContainer else container)
                .pointerInput(key) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            onPressStart(key.char)
                            try {
                                tryAwaitRelease()
                            } finally {
                                pressed = false
                                onPressEnd()
                            }
                        },
                        onTap = { onTap(key.char) },
                        onLongPress = { onLongPress(key.char) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val digitSize = (diameter.value * 0.42f).coerceIn(20f, 32f)
            val subSize = (diameter.value * 0.15f).coerceIn(8f, 11f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = key.char.toString(),
                    fontSize = digitSize.sp,
                    lineHeight = (digitSize * 1.1f).sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (key.sub.isNotEmpty()) {
                    Text(
                        text = key.sub,
                        fontSize = subSize.sp,
                        lineHeight = (subSize * 1.2f).sp,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val SUGGESTION_SLOT_HEIGHT = 68.dp

/** One horizontally scrolling line of matches (saved contacts, then recent numbers), or "add contact". */
@Composable
private fun SuggestionStrip(
    suggestions: List<Suggestion>,
    onPick: (Suggestion) -> Unit,
    showAddContact: Boolean,
    onAddContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(suggestions) { suggestion ->
            val formatted = PhoneNumberFormatter.formatLoose(suggestion.number)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { onPick(suggestion) }
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InitialAvatar(suggestion.name ?: formatted, size = 32.dp, fontSize = 14.sp)
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        suggestion.name ?: formatted,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (suggestion.name != null) {
                        Text(
                            formatted,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (showAddContact) {
            item {
                TextButton(onClick = onAddContact) { Text("연락처에 추가") }
            }
        }
    }
}
