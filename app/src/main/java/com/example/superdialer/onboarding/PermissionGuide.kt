package com.example.superdialer.onboarding

import com.example.superdialer.R
import com.example.superdialer.i18n.str
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
            str(R.string.t_call), str(R.string.t_places_calls_right_from_the_keypad),
            Icons.Filled.Call, listOf(Manifest.permission.CALL_PHONE),
        ),
        PermissionInfo(
            str(R.string.t_calls_3), str(R.string.t_shows_and_deletes_your_recent_calls),
            Icons.Filled.History, listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG),
        ),
        PermissionInfo(
            str(R.string.shortcut_contacts), str(R.string.t_shows_names_instead_of_numbers_and),
            Icons.Filled.Contacts, listOf(Manifest.permission.READ_CONTACTS),
        ),
        PermissionInfo(
            str(R.string.t_texts), str(R.string.t_shows_the_texts_exchanged_with_a),
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
        Text(str(R.string.t_permissions_this_app_uses), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            str(R.string.t_if_you_allow_the_permissions_below),
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
        ) { Text(str(R.string.t_allow_permission)) }
        TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text(str(R.string.t_later)) }
    }
}
