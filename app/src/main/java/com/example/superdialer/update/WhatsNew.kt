package com.example.superdialer.update

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** What changed in the version that was just installed, shown once after an update. */
data class WhatsNewInfo(val version: String, val lines: List<String>)

/**
 * After an update the first start shows the release notes of the new version once. A fresh install shows nothing.
 * The notes come from the GitHub release of that tag; without a network or a release the dialog just says it was updated.
 */
object WhatsNew {
    private const val PREFS = "whats_new"
    private const val KEY_SEEN = "seen_version"
    private const val TAG_URL = "https://api.github.com/repos/Jangwoo0827/phonecall/releases/tags/v"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Observable by Compose; null when there is nothing to show. */
    var pending by mutableStateOf<WhatsNewInfo?>(null)
        private set

    fun check(context: Context) {
        val app = context.applicationContext
        val current = UpdateChecker.currentVersion(app)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getString(KEY_SEEN, null)
        // remembered right away: a failed fetch must not make the dialog come back on every start
        prefs.edit().putString(KEY_SEEN, current).apply()
        if (!shouldShow(seen, current)) return
        scope.launch {
            val lines = fetchNotes(current)
            withContext(Dispatchers.Main) { pending = WhatsNewInfo(current, lines) }
        }
    }

    fun dismiss() {
        pending = null
    }

    private fun fetchNotes(version: String): List<String> {
        val connection = runCatching { URL(TAG_URL + version).openConnection() as HttpURLConnection }.getOrNull() ?: return emptyList()
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "SuperDialer")
            if (connection.responseCode != 200) return emptyList()
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            cleanNotes(UpdateChecker.parseRelease(body)?.notes.orEmpty())
        } catch (e: java.io.IOException) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    // --- Pure (unit tested) -----------------------------------------------------------------------

    /** Shown when a version was seen before and it differs from the one running now (never on a fresh install). */
    fun shouldShow(seenVersion: String?, current: String): Boolean = seenVersion != null && current.isNotEmpty() && seenVersion != current

    /** The change list of a release body: only the "- ..." lines of the "변경 내용" part, without links and boilerplate. */
    fun cleanNotes(body: String): List<String> {
        val lines = body.lines().map { it.trim() }
        val start = lines.indexOfFirst { it.startsWith("## 변경") }
        val section = if (start >= 0) lines.drop(start + 1).takeWhile { !it.startsWith("## ") } else lines
        return section.filter { it.startsWith("- ") || it.startsWith("* ") }
            .map { it.drop(2).trim() }
            .filter { it.isNotEmpty() && !it.startsWith("**Full Changelog**") && !it.contains("github.com/") }
            .take(15)
    }
}

/** The "what's new" dialog; place it once near the root of the app. */
@Composable
fun WhatsNewHost() {
    val info = WhatsNew.pending ?: return
    AlertDialog(
        onDismissRequest = WhatsNew::dismiss,
        title = { Text("${info.version} 업데이트 완료") },
        text = {
            if (info.lines.isEmpty()) {
                Text("앱이 새 버전으로 업데이트되었습니다.")
            } else {
                androidx.compose.foundation.layout.Column {
                    Text("바뀐 내용", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                    info.lines.forEach { Text("• $it", modifier = androidx.compose.ui.Modifier.padding(top = 4.dp)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = WhatsNew::dismiss) { Text("확인") } },
    )
}
