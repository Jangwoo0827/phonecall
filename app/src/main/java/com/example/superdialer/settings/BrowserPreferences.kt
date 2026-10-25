package com.example.superdialer.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.superdialer.browser.SearchEngine

/** 설정 > 브라우저: which search engine the address bar uses, and whether web pages are shown dark. */
@Composable
internal fun BrowserPreferences() {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("검색 엔진", style = MaterialTheme.typography.bodyLarge)
        Text(
            "주소창에 주소가 아닌 글을 입력하면 이 검색엔진으로 찾아요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SearchEngine.entries.forEachIndexed { index, engine ->
                SegmentedButton(
                    selected = AppSettings.searchEngine == engine,
                    onClick = { AppSettings.updateSearchEngine(engine) },
                    shape = SegmentedButtonDefaults.itemShape(index, SearchEngine.entries.size),
                    colors = choiceColors(),
                    modifier = Modifier.testTag("search_engine_${engine.key}"),
                ) { Text(engine.label, maxLines = 1, softWrap = false) }
            }
        }

        Text("웹 페이지 다크 모드", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 20.dp))
        Text(
            "어두운 버전이 없는 사이트도 어둡게 바꿔서 보여줘요. 일부 사이트는 색이 이상하게 보일 수 있어요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            WebDarkMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = AppSettings.webDarkMode == mode,
                    onClick = { AppSettings.updateWebDarkMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, WebDarkMode.entries.size),
                    colors = choiceColors(),
                    modifier = Modifier.testTag("web_dark_${mode.key}"),
                ) { Text(mode.label, maxLines = 1, softWrap = false) }
            }
        }
    }
}

@Composable
private fun choiceColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
