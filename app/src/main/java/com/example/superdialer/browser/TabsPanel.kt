package com.example.superdialer.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The tab list as a grid of page cards with a "일반 / 비공개" switch on top (the pattern of current mobile browsers).
 * Private tabs are shown in their own violet palette. [initialPrivate] is the kind of the tab that is open now.
 */
@Composable
internal fun TabsPanel(
    tabs: List<BrowserTab>,
    selectedId: Int,
    initialPrivate: Boolean,
    onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit,
    onCloseAll: (private: Boolean) -> Unit,
    onNewTab: (private: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var showPrivate by rememberSaveable { mutableStateOf(initialPrivate) }
    val normalCount = tabs.count { !it.isPrivate }
    val privateCount = tabs.count { it.isPrivate }
    val shown = tabs.filter { it.isPrivate == showPrivate }

    PrivateTheme(showPrivate) {
        Surface(modifier = Modifier.fillMaxSize().testTag("tabs_panel"), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로") }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                        SegmentedButton(
                            selected = !showPrivate,
                            onClick = { showPrivate = false },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                            colors = switchColors(),
                        ) { Text("일반 $normalCount", maxLines = 1, softWrap = false) }
                        SegmentedButton(
                            selected = showPrivate,
                            onClick = { showPrivate = true },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                            colors = switchColors(),
                            icon = {},
                            modifier = Modifier.testTag("private_switch"),
                        ) { Text("비공개 $privateCount", maxLines = 1, softWrap = false) }
                    }
                    FilledTonalIconButton(
                        onClick = { onNewTab(showPrivate) },
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp).testTag("new_tab_button"),
                        shape = RoundedCornerShape(14.dp),
                    ) { Icon(Icons.Filled.Add, contentDescription = if (showPrivate) "새 비공개 탭" else "새 탭") }
                }
                if (shown.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onCloseAll(showPrivate) }) { Text("모두 닫기") }
                    }
                }
                if (shown.isEmpty()) {
                    PrivateEmpty(onNewTab = { onNewTab(true) })
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shown, key = { it.id }) { tab ->
                            TabCard(tab, current = tab.id == selectedId, onSelect = { onSelect(tab.id) }, onClose = { onClose(tab.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun switchColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@Composable
private fun TabCard(tab: BrowserTab, current: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val title = tab.title.ifBlank { tab.url.ifBlank { "새 탭" } }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).testTag("tab_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (current) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tab.url.isNotBlank()) SiteIcon(tab.url, tab.title, 20.dp)
                Text(
                    title,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "탭 닫기", modifier = Modifier.size(18.dp))
                }
            }
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                val picture = tab.thumbnail
                if (picture != null) {
                    Image(
                        bitmap = picture,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                    )
                } else if (tab.url.isNotBlank()) {
                    SiteIcon(tab.url, tab.title, 48.dp)
                } else {
                    Text("새 탭", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Shown in the private view while no private tab is open: what private tabs do and do not keep. */
@Composable
private fun PrivateEmpty(onNewTab: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.height(20.dp))
        Text("비공개 탭이 없어요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "비공개 탭은 방문 기록, 쿠키, 사이트 데이터를 남기지 않아요. 탭을 모두 닫으면 그 데이터도 함께 지워져요. 다운로드한 파일과 북마크는 남아요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onNewTab) { Text("새 비공개 탭") }
    }
}
