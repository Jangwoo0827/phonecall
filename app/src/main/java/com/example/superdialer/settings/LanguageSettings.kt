package com.example.superdialer.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.superdialer.R
import com.example.superdialer.i18n.AppLanguage
import com.example.superdialer.i18n.LanguageSwitcher
import com.example.superdialer.i18n.str

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun AppLanguage.label(): String = when (this) {
    AppLanguage.SYSTEM -> str(R.string.language_follow_phone)
    AppLanguage.KOREAN -> "한국어" // language names are written in their own language
    AppLanguage.ENGLISH -> "English"
}

/** 설정 > 화면 > 언어: follow the phone, Korean or English. Changing it restarts the app (after a confirmation). */
@Composable
fun LanguageRow() {
    val activity = LocalContext.current.activity()
    var pending by remember { mutableStateOf<AppLanguage?>(null) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(str(R.string.language_title), style = MaterialTheme.typography.bodyLarge)
        Text(
            str(R.string.language_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            AppLanguage.entries.forEachIndexed { index, language ->
                SegmentedButton(
                    selected = AppSettings.language == language,
                    onClick = { if (language != AppSettings.language) pending = language },
                    shape = SegmentedButtonDefaults.itemShape(index, AppLanguage.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.testTag("language_${language.key}"),
                ) { Text(language.label(), maxLines = 1, softWrap = false) }
            }
        }
    }
    pending?.let { language ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(str(R.string.language_restart_title)) },
            text = { Text(str(R.string.language_restart_text, language.label())) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    activity?.let { LanguageSwitcher.change(it, language) }
                }) { Text(str(R.string.language_restart_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(str(R.string.cancel)) } },
        )
    }
}
