package com.example.superdialer.settings

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
        ListItem(
            colors = transparent,
            modifier = Modifier.clickable { dialogOpen = true },
            headlineContent = { Text("로그인 / 회원가입") },
            supportingContent = {
                Text("로그인하면 스피드 다이얼·북마크·게임 점수·설정이 다른 폰과 동기화되고, 체크리스트에 자동 로그인됩니다. 로그인 없이도 모든 기능을 쓸 수 있습니다")
            },
        )
    } else {
        ListItem(
            colors = transparent,
            headlineContent = { Text(AccountManager.email.orEmpty()) },
            supportingContent = {
                Text(
                    if (AccountManager.lastSyncMs > 0) "마지막 동기화 ${formatDateTime(AccountManager.lastSyncMs)}"
                    else "아직 동기화하지 않았습니다"
                )
            },
            trailingContent = { if (AccountManager.busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(4.dp)) },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = AccountManager::syncNow, enabled = !AccountManager.busy) { Text("지금 동기화") }
            OutlinedButton(onClick = AccountManager::signOut) { Text("로그아웃") }
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
        supportingContent = { Text("동기화하는 것: 시작 페이지 링크, 북마크, 게임 최고 점수, 키패드음·화면 색 설정, 거절 메시지. 통화·연락처·문자는 올리지 않습니다") },
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
        title = { Text("계정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("이메일") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("비밀번호 (6자 이상)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                AccountManager.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { AccountManager.sendPasswordReset(email) }, enabled = !busy) { Text("비밀번호를 잊었어요") }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = !busy,
                    onClick = { AccountManager.signUp(email, password) { if (it) onSignedIn() } },
                ) { Text("회원가입") }
                Button(
                    enabled = !busy,
                    onClick = { AccountManager.signIn(email, password) { if (it) onSignedIn() } },
                ) { Text(if (busy) "확인 중…" else "로그인") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("취소") } },
    )
}
