package com.example.superdialer.settings

import com.example.superdialer.R
import com.example.superdialer.i18n.str
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
        Text(str(R.string.t_search_engine), style = MaterialTheme.typography.bodyLarge)
        Text(
            str(R.string.t_text_in_the_address_bar_that),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            SearchEngine.entries.forEachIndexed { index, engine ->
                SegmentedButton(
                    selected = AppSettings.searchEngine == engine,
                    icon = {},
                    onClick = { AppSettings.updateSearchEngine(engine) },
                    shape = SegmentedButtonDefaults.itemShape(index, SearchEngine.entries.size),
                    colors = choiceColors(),
                    modifier = Modifier.testTag("search_engine_${engine.key}"),
                ) { Text(engine.label, maxLines = 1, softWrap = false) }
            }
        }

        Text(str(R.string.t_web_page_dark_mode), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 20.dp))
        Text(
            str(R.string.t_also_darkens_sites_that_have_no),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            WebDarkMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = AppSettings.webDarkMode == mode,
                    icon = {},
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
