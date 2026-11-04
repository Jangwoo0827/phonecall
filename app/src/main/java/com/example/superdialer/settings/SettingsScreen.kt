package com.example.superdialer.settings

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import com.example.superdialer.kakao.openNotificationAccessSettings
import com.example.superdialer.kakao.notificationAccessGranted
import com.example.superdialer.kakao.KakaoLinks
import com.example.superdialer.kakao.KakaoInbox
import androidx.compose.ui.platform.testTag
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
    onOpenQuickDial: () -> Unit,
    onOpenSitePermissions: () -> Unit,
    onOpenPatchNotes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val defaultDialer = rememberDefaultDialerStatus { granted ->
        Toast.makeText(
            context,
            if (granted) str(R.string.t_set_as_the_default_phone_app) else str(R.string.t_not_set_as_the_default_phone),
            Toast.LENGTH_SHORT,
        ).show()
    }

    var showLockShortcutHelp by remember { mutableStateOf(false) }
    if (showLockShortcutHelp) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLockShortcutHelp = false },
            title = { Text(str(R.string.t_lock_screen_phone_shortcut)) },
            text = {
                Text(
                    str(R.string.t_the_lock_screen_corner_icon_is)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLockShortcutHelp = false
                    try {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: android.content.ActivityNotFoundException) {
                        Toast.makeText(context, str(R.string.t_can_t_open_the_settings), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(str(R.string.t_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { showLockShortcutHelp = false }) { Text(str(R.string.t_close)) } },
        )
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
        ScreenHeader(str(R.string.t_settings), onBack = null)
        UpdateBanner()

        SettingsGroup(str(R.string.t_account)) {
            AccountRows()
        }

        SettingsGroup(str(R.string.t_call)) {
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text(str(R.string.t_default_phone_app)) },
                supportingContent = {
                    Text(
                        if (defaultDialer.isDefault) {
                            str(R.string.t_superdialer_is_the_default_phone_app)
                        } else {
                            str(R.string.t_needed_for_the_incoming_and_in)
                        }
                    )
                },
                trailingContent = {
                    if (defaultDialer.isDefault) {
                        TextButton(onClick = defaultDialer.request) { Text(str(R.string.t_ask_again)) }
                    } else {
                        Button(onClick = defaultDialer.request) { Text(str(R.string.t_settings)) }
                    }
                },
            )
            Clickable(str(R.string.t_lock_screen_phone_shortcut), str(R.string.t_how_to_change_the_lock_screen)) { showLockShortcutHelp = true }
            Clickable(str(R.string.t_blocked_numbers), str(R.string.t_view_add_and_remove_blocked_numbers), onOpenBlocked)
            Clickable(str(R.string.t_decline_messages), str(R.string.t_manage_the_messages_sent_when_declinin), onOpenRejectMessages)
            Clickable(str(R.string.t_call_forwarding_call_services), str(R.string.t_open_carrier_call_settings_call_forwar)) { openCallSettings(context) }
        }

        SettingsGroup(str(R.string.t_kakaotalk_2)) {
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text(str(R.string.t_collect_kakaotalk_notifications)) },
                supportingContent = {
                    Text(str(R.string.t_when_on_and_notification_access_is))
                },
                trailingContent = {
                    Switch(
                        checked = KakaoInbox.enabled,
                        onCheckedChange = {
                            KakaoInbox.switchCollecting(it)
                            if (it && !notificationAccessGranted(context)) openNotificationAccessSettings(context)
                        },
                        modifier = Modifier.testTag("switch_kakao_inbox"),
                    )
                },
            )
            Clickable(str(R.string.t_notification_access_settings), str(R.string.t_turn_superdialer_s_notification_access)) { openNotificationAccessSettings(context) }
            Clickable(str(R.string.t_open_kakaotalk), str(R.string.t_opens_the_kakaotalk_app)) { KakaoLinks.openApp(context) }
        }

        SettingsGroup(str(R.string.t_display)) {
            LanguageRow()
            ThemeRows()
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text(str(R.string.t_follow_wallpaper_colors)) },
                supportingContent = { Text(str(R.string.t_when_on_the_colors_are_taken)) },
                trailingContent = {
                    Switch(checked = AppSettings.dynamicColor, onCheckedChange = AppSettings::updateDynamicColor)
                },
            )
        }

        SettingsGroup(str(R.string.t_keypad)) {
            Clickable(str(R.string.t_speed_dial), str(R.string.t_long_press_a_digit_to_dial), onOpenQuickDial)
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text(str(R.string.t_keypad_sound)) },
                supportingContent = { Text(str(R.string.t_plays_a_dtmf_tone_when_you)) },
                trailingContent = {
                    Switch(
                        checked = AppSettings.dtmfEnabled,
                        onCheckedChange = AppSettings::updateDtmfEnabled,
                        modifier = Modifier.testTag("switch_dtmf"),
                    )
                },
            )
        }

        SettingsGroup(str(R.string.shortcut_browser)) {
            BrowserPreferences()
            HorizontalDivider()
            Clickable(str(R.string.t_site_permissions), str(R.string.t_view_sites_allowed_or_blocked_from), onOpenSitePermissions)
            Clickable(str(R.string.t_passwords_autofill), str(R.string.t_pick_an_autofill_service_such_as)) {
                try {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, str(R.string.t_can_t_open_the_autofill_settings), Toast.LENGTH_LONG).show()
                }
            }
            var externalLinks by remember { mutableStateOf(ExternalLinks.isEnabled(context)) }
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text(str(R.string.t_open_external_links_in_this_browser)) },
                supportingContent = {
                    Text(str(R.string.t_when_on_superdialer_appears_as_an))
                },
                trailingContent = {
                    Switch(checked = externalLinks, onCheckedChange = {
                        ExternalLinks.setEnabled(context, it)
                        externalLinks = it
                    })
                },
            )
        }

        SettingsGroup(str(R.string.t_permissions)) {
            PermissionRow(context, str(R.string.t_call), Manifest.permission.CALL_PHONE)
            PermissionRow(context, str(R.string.t_calls_3), Manifest.permission.READ_CALL_LOG)
            PermissionRow(context, str(R.string.t_delete_call_history), Manifest.permission.WRITE_CALL_LOG)
            PermissionRow(context, str(R.string.shortcut_contacts), Manifest.permission.READ_CONTACTS)
            PermissionRow(context, str(R.string.t_edit_contacts_favorites), Manifest.permission.WRITE_CONTACTS)
            PermissionRow(context, str(R.string.t_read_texts_messages_in_contacts_and), Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(context, str(R.string.t_notifications), Manifest.permission.POST_NOTIFICATIONS)
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
                    headlineContent = { Text(str(R.string.t_full_screen_notifications)) },
                    supportingContent = { Text(str(R.string.t_shows_incoming_calls_full_screen_on) + if (allowed) str(R.string.t_allowed) else str(R.string.t_not_allowed)) },
                )
            }
        }

        SettingsGroup(str(R.string.t_about)) {
            AppInfoRows()
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text(str(R.string.t_version)) },
                supportingContent = { Text(appVersion(context)) },
            )
            Clickable(str(R.string.t_patch_notes), str(R.string.t_what_changed_in_every_version_so), onOpenPatchNotes)
            Clickable(str(R.string.t_privacy_policy), str(R.string.t_what_is_stored_where_and_how)) {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, str(R.string.t_can_t_open_a_web_browser_2), Toast.LENGTH_SHORT).show()
                }
            }
            ListItem(
                colors = transparentListItem(),
                headlineContent = { Text(str(R.string.t_call_recording)) },
                supportingContent = {
                    Text(str(R.string.t_the_recording_button_on_the_call))
                },
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
        supportingContent = { Text(if (granted) str(R.string.t_allowed) else str(R.string.t_not_allowed_tap_to_open_settings)) },
    )
}

private fun openCallSettings(context: Context) {
    try {
        context.startActivity(Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, str(R.string.t_can_t_open_the_call_settings), Toast.LENGTH_SHORT).show()
    }
}

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
} catch (e: Exception) {
    "-"
}

/** The privacy policy page (docs/privacy.html of the repository, served by GitHub Pages). */
const val PRIVACY_POLICY_URL = "https://jangwoo0827.github.io/phonecall/privacy.html"
const val PRIVACY_POLICY_URL_EN = "https://jangwoo0827.github.io/phonecall/privacy-en.html"

/** The privacy policy page in the app's language. */
fun privacyPolicyUrl(): String = if (com.example.superdialer.i18n.Strings.isKorean) PRIVACY_POLICY_URL else PRIVACY_POLICY_URL_EN
