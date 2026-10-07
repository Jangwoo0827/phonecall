package com.example.superdialer.hub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.superdialer.browser.BrowserScreen
import com.example.superdialer.games.GamesScreen

private val sections = listOf("브라우저", "게임")

/** One bottom tab that hosts the built-in browser and the built-in games behind a top switcher. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebGamesScreen(modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selected) {
            sections.forEachIndexed { index, title ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) })
            }
        }
        when (selected) {
            0 -> BrowserScreen()
            else -> GamesScreen()
        }
    }
}
