package com.example.superdialer.settings

import com.example.superdialer.R
import com.example.superdialer.i18n.str
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.superdialer.account.AccountManager
import com.example.superdialer.calllog.formatDateTime

/** Rows of the "계정" settings card: sign in / up, or the signed-in state with sync and sign-out. */
@Composable
internal fun AccountRows() {
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    if (!AccountManager.signedIn) {
        val context = androidx.compose.ui.platform.LocalContext.current
        ListItem(
            colors = transparent,
            modifier = Modifier.clickable(enabled = !AccountManager.busy) { AccountManager.startGoogleSignIn(context) },
            headlineContent = { Text(str(R.string.t_continue_with_google)) },
            supportingContent = { Text(str(R.string.t_sign_in_with_your_google_account)) },
            trailingContent = { if (AccountManager.busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp)) },
        )
        ListItem(
            colors = transparent,
            modifier = Modifier.clickable { dialogOpen = true },
            headlineContent = { Text(str(R.string.t_sign_in_with_email_sign_up)) },
            supportingContent = {
                Text(str(R.string.t_signing_in_syncs_speed_dials_bookmarks))
            },
        )
    } else {
        ListItem(
            colors = transparent,
            headlineContent = { Text(AccountManager.email.orEmpty()) },
            supportingContent = {
                Text(
                    if (AccountManager.lastSyncMs > 0) str(R.string.t_last_synced, formatDateTime(AccountManager.lastSyncMs))
                    else str(R.string.t_not_synced_yet)
                )
            },
            trailingContent = { if (AccountManager.busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp)) },
        )
        var editingNickname by rememberSaveable { mutableStateOf(false) }
        ListItem(
            modifier = Modifier.clickable { editingNickname = true },
            colors = transparent,
            headlineContent = { Text(str(R.string.t_nickname_shown_on_the_leaderboard)) },
            supportingContent = { Text(AccountManager.nickname.also { AccountManager.nicknameVersion }) },
        )
        if (editingNickname) NicknameDialog(AccountManager.nickname, onDismiss = { editingNickname = false })
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = AccountManager::syncNow, enabled = !AccountManager.busy) { Text(str(R.string.t_sync_now)) }
            OutlinedButton(onClick = AccountManager::signOut) { Text(str(R.string.t_sign_out)) }
        }
        var confirmDelete by rememberSaveable { mutableStateOf(false) }
        TextButton(
            onClick = { confirmDelete = true },
            enabled = !AccountManager.busy,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text(str(R.string.t_delete_account), color = MaterialTheme.colorScheme.error) }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(str(R.string.t_delete_your_account)) },
                text = {
                    Text(
                        str(R.string.t_all_of_this_account_s_data)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; AccountManager.deleteAccount { } }) {
                        Text(str(R.string.t_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(str(R.string.cancel)) } },
            )
        }
    }
    AccountManager.message?.let {
        Text(
            it,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ListItem(
        colors = transparent,
        supportingContent = { Text(str(R.string.t_what_is_synced_start_page_links)) },
        headlineContent = {},
    )

    if (dialogOpen && !AccountManager.signedIn) {
        LoginDialog(onDismiss = { dialogOpen = false }, onSignedIn = { dialogOpen = false })
    }
}

@Composable
private fun LoginDialog(onDismiss: () -> Unit, onSignedIn: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val busy = AccountManager.busy

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(str(R.string.t_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(str(R.string.t_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(str(R.string.t_password_6_characters)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                AccountManager.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { AccountManager.sendPasswordReset(email) }, enabled = !busy) { Text(str(R.string.t_i_forgot_my_password)) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = !busy,
                    onClick = { AccountManager.signUp(email, password) { if (it) onSignedIn() } },
                ) { Text(str(R.string.t_sign_up)) }
                Button(
                    enabled = !busy,
                    onClick = { AccountManager.signIn(email, password) { if (it) onSignedIn() } },
                ) { Text(if (busy) str(R.string.t_checking) else str(R.string.t_sign_in)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(str(R.string.cancel)) } },
    )
}

@Composable
private fun NicknameDialog(current: String, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.t_nickname)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(20) },
                singleLine = true,
                supportingText = { Text(str(R.string.t_other_people_see_this_on_the)) },
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { AccountManager.updateNickname(text); onDismiss() }) { Text(str(R.string.t_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
}
