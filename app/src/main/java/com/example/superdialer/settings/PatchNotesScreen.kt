package com.example.superdialer.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.superdialer.ui.ScreenHeader
import com.example.superdialer.update.ReleaseNotes
import com.example.superdialer.update.UpdateChecker

/** 설정 > 앱 정보 > 패치노트: what changed in every released version, newest first. */
@Composable
fun PatchNotesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(ReleaseNotes.cached(context)) }
    var loading by remember { mutableStateOf(true) }
    var offline by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val fresh = ReleaseNotes.refresh(context)
        if (fresh != null) entries = fresh else offline = true
        loading = false
    }
    val current = remember { UpdateChecker.currentVersion(context) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader("패치노트", onBack)
        when {
            entries.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            entries.isEmpty() -> Text(
                "패치노트를 불러오지 못했어요. 인터넷 연결을 확인하고 다시 열어 주세요.",
                modifier = Modifier.padding(24.dp).testTag("patch_notes_error"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("patch_notes"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (offline) {
                    item {
                        Text(
                            "인터넷에 연결되지 않아 저장해 둔 내용을 보여줘요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                items(entries, key = { it.version }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("v${entry.version}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (entry.version == current) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.padding(start = 8.dp),
                                    ) {
                                        Text(
                                            "현재 버전",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            if (entry.date.isNotEmpty()) {
                                Text(entry.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (entry.lines.isEmpty()) {
                                Text(
                                    "기록된 변경 내용이 없어요.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            entry.lines.forEach { line ->
                                Text("• $line", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
