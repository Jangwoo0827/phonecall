package com.example.superdialer.settings

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.background
import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.superdialer.ui.ScreenHeader
import com.example.superdialer.ui.hasPermission
import com.example.superdialer.ui.openAppSettings
import com.example.superdialer.ui.rememberDefaultDialerStatus

@Composable
fun SettingsScreen(
    onOpenBlocked: () -> Unit,
    onOpenRejectMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val defaultDialer = rememberDefaultDialerStatus { granted ->
        Toast.makeText(
            context,
            if (granted) "기본 전화 앱으로 설정되었습니다." else "기본 전화 앱으로 설정하지 않았습니다.",
            Toast.LENGTH_SHORT,
        ).show()
    }

    // Permission state can change in system settings while this screen stays open.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    @Suppress("UNUSED_EXPRESSION") refresh

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("설정", onBack = null)

        SettingsGroup("계정") {
            AccountRows()
        }

        SettingsGroup("전화") {
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text("기본 전화 앱") },
                supportingContent = {
                    Text(
                        if (defaultDialer.isDefault) {
                            "SuperDialer가 기본 전화 앱입니다 (되돌리기: 휴대폰 설정 > 앱 > 기본 앱 > 전화 앱)"
                        } else {
                            "수신·통화 화면과 번호 차단에 필요합니다"
                        }
                    )
                },
                trailingContent = {
                    if (defaultDialer.isDefault) {
                        TextButton(onClick = defaultDialer.request) { Text("다시 요청") }
                    } else {
                        Button(onClick = defaultDialer.request) { Text("설정") }
                    }
                },
            )
            Clickable("차단 관리", "차단한 번호 보기·추가·해제", onOpenBlocked)
            Clickable("거절 메시지", "수신 거절 시 보낼 문구 관리", onOpenRejectMessages)
            Clickable("착신전환·통화 부가서비스", "통신사 통화 설정 열기 (착신전환 등)") { openCallSettings(context) }
        }

        SettingsGroup("화면") {
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text("배경화면 색상 따라가기") },
                supportingContent = { Text("켜면 폰 배경화면에서 뽑은 색을 씁니다. 기본값은 앱 고유 색상입니다") },
                trailingContent = {
                    Switch(checked = AppSettings.dynamicColor, onCheckedChange = AppSettings::updateDynamicColor)
                },
            )
        }

        SettingsGroup("키패드") {
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text("키패드음") },
                supportingContent = { Text("번호를 누를 때 DTMF 소리를 재생합니다") },
                trailingContent = {
                    Switch(checked = AppSettings.dtmfEnabled, onCheckedChange = AppSettings::updateDtmfEnabled)
                },
            )
        }

        SettingsGroup("브라우저") {
            var externalLinks by remember { mutableStateOf(ExternalLinks.isEnabled(context)) }
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text("외부 링크를 이 브라우저로 열기") },
                supportingContent = {
                    Text("켜면 다른 앱의 웹 링크를 열 때 SuperDialer가 선택지에 나옵니다. 기본값은 꺼짐입니다.")
                },
                trailingContent = {
                    Switch(checked = externalLinks, onCheckedChange = {
                        ExternalLinks.setEnabled(context, it)
                        externalLinks = it
                    })
                },
            )
        }

        SettingsGroup("권한") {
            PermissionRow(context, "전화", Manifest.permission.CALL_PHONE)
            PermissionRow(context, "통화 기록", Manifest.permission.READ_CALL_LOG)
            PermissionRow(context, "통화 기록 삭제", Manifest.permission.WRITE_CALL_LOG)
            PermissionRow(context, "연락처", Manifest.permission.READ_CONTACTS)
            PermissionRow(context, "연락처 수정 (즐겨찾기)", Manifest.permission.WRITE_CONTACTS)
            PermissionRow(context, "문자 읽기 (연락처·기록의 메시지 보기)", Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(context, "알림", Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val allowed = context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
                ListItem(
                    colors = transparentListItem(),
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    headlineContent = { Text("전체 화면 알림") },
                    supportingContent = { Text("잠금 화면에서 수신 전화를 크게 표시합니다 · " + if (allowed) "허용됨" else "허용 안 됨") },
                )
            }
        }

        SettingsGroup("앱 정보") {
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text("버전") },
                supportingContent = { Text(appVersion(context)) },
            )
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text("통화 녹음") },
                supportingContent = { Text("안드로이드는 보안 정책상 일반 앱의 통화 녹음을 허용하지 않아 지원하지 않습니다") },
            )
        }
    }
}

/** A titled card holding one block of settings rows. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
private fun transparentListItem() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun Clickable(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        colors = transparentListItem(),
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
    )
}

@Composable
private fun PermissionRow(context: Context, label: String, permission: String) {
    val granted = context.hasPermission(permission)
    ListItem(
        colors = transparentListItem(),
        modifier = Modifier.clickable { context.openAppSettings() },
        headlineContent = { Text(label) },
        supportingContent = { Text(if (granted) "허용됨" else "허용 안 됨 · 눌러서 설정 열기") },
    )
}

private fun openCallSettings(context: Context) {
    try {
        context.startActivity(Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "통화 설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
} catch (e: Exception) {
    "-"
}
