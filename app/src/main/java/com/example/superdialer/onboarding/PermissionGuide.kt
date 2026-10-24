package com.example.superdialer.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.superdialer.ui.hasPermission

/** One thing the app asks the phone for at first start, with the plain-language reason shown on the guide screen. */
class PermissionInfo(val title: String, val reason: String, val icon: ImageVector, val permissions: List<String>)

/**
 * The permissions the main screens need. They used to be asked one by one the first time each screen was opened;
 * the guide asks for all of them up front so nothing pops up in the middle of a call or a search.
 */
object CorePermissions {
    val all: List<PermissionInfo> = listOf(
        PermissionInfo(
            "전화", "키패드에서 바로 전화를 걸어요.",
            Icons.Filled.Call, listOf(Manifest.permission.CALL_PHONE),
        ),
        PermissionInfo(
            "통화 기록", "최근 기록을 보여주고 지울 수 있어요.",
            Icons.Filled.History, listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG),
        ),
        PermissionInfo(
            "연락처", "번호 대신 이름을 보여주고, 키패드에서 자동 완성해요.",
            Icons.Filled.Contacts, listOf(Manifest.permission.READ_CONTACTS),
        ),
        PermissionInfo(
            "문자", "통화 기록 옆에서 그 번호와 주고받은 문자를 보여줘요. 읽기만 하고 서버로 보내지 않아요.",
            Icons.Filled.Sms, listOf(Manifest.permission.READ_SMS),
        ),
    )

    /** Every permission of [all] as one list, in the order they are listed. */
    val flat: List<String> get() = all.flatMap { it.permissions }

    /** The permissions that are still not granted, according to [isGranted]. */
    fun missing(isGranted: (String) -> Boolean): List<String> = flat.filterNot(isGranted)
}

/**
 * First-run screen explaining what each permission is for and asking for all of them at once. [onFinished] is called
 * after the system dialog closes (granted or not: the individual screens ask again when needed) or on "나중에".
 */
@Composable
fun PermissionGuide(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onFinished() }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.VerifiedUser, contentDescription = null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text("이 앱이 쓰는 권한", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "아래 권한을 한 번에 허용하면 중간에 허용 창이 뜨지 않아요. 허용하지 않아도 앱은 쓸 수 있고, 필요한 화면에서 다시 물어봐요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        CorePermissions.all.forEach { info ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(info.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(info.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(info.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                val missing = CorePermissions.missing { context.hasPermission(it) }
                if (missing.isEmpty()) onFinished() else launcher.launch(missing.toTypedArray())
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("권한 허용") }
        TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text("나중에") }
    }
}
