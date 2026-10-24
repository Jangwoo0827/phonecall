package com.example.superdialer.calllog

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.superdialer.dialer.PhoneNumberFormatter
import com.example.superdialer.ui.InitialAvatar
import com.example.superdialer.ui.ScreenHeader
import com.example.superdialer.ui.groupShape
import com.example.superdialer.ui.hasPermission
import com.example.superdialer.ui.theme.incomingColor
import com.example.superdialer.ui.theme.outgoingColor

/** "통화 통계": how many calls, how long, and who you talk to most, for the last week / month / everything. */
@Composable
fun CallStatsScreen(viewModel: CallLogViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (context.hasPermission(Manifest.permission.READ_CALL_LOG)) viewModel.refresh()
    }
    var period by rememberSaveable { mutableStateOf(StatsPeriod.Month) }
    val stats = remember(viewModel.entries, period) {
        CallStatsCalculator.calculate(viewModel.entries, period, System.currentTimeMillis())
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader("통화 통계", onBack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatsPeriod.entries.forEach { p ->
                FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) })
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().testTag("call_stats")) {
            item { SummaryCard(stats) }
            item {
                Text(
                    "자주 통화한 사람",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                )
            }
            if (stats.top.isEmpty()) {
                item {
                    Text(
                        "이 기간에는 통화 기록이 없습니다.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            itemsIndexed(stats.top, key = { _, c -> c.number }) { index, caller ->
                val title = caller.name ?: PhoneNumberFormatter.formatLoose(caller.number)
                val talk = formatDuration(caller.talkSeconds)
                ListItem(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 12.dp, bottom = 2.dp)
                        .clip(groupShape(index, stats.top.size)),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    leadingContent = { InitialAvatar(caller.name ?: caller.number) },
                    headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(if (talk.isEmpty()) "통화 ${caller.calls}번" else "통화 ${caller.calls}번 · 총 $talk")
                    },
                    trailingContent = {
                        Text("${index + 1}위", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    },
                )
            }
            item { Text("", modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun SummaryCard(stats: CallStats) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("통화", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${stats.total}번", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            val talk = formatDuration(stats.talkSeconds)
            Text(
                if (talk.isEmpty()) "통화한 시간 없음" else "통화한 시간 $talk",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tile("수신", stats.incoming, incomingColor())
                Tile("발신", stats.outgoing, outgoingColor())
                Tile("부재중", stats.missed, MaterialTheme.colorScheme.error)
                Tile("거절·차단", stats.declined, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Tile(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
