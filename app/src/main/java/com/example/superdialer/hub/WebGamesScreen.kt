package com.example.superdialer.hub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.superdialer.browser.BrowserScreen
import com.example.superdialer.browser.BrowserViewModel
import com.example.superdialer.games.GamesScreen

private val sections = listOf("브라우저", "게임")

/**
 * One bottom tab that hosts the built-in browser and the built-in games behind a top switcher.
 * While a website is open the switcher is hidden (and the app's bottom bar too) so the page gets the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebGamesScreen(browserViewModel: BrowserViewModel, modifier: Modifier = Modifier) {
    val selected = browserViewModel.hubSection
    // A link opened from another app always lands in the browser section.
    LaunchedEffect(browserViewModel.externalUrl) {
        if (browserViewModel.externalUrl != null) browserViewModel.hubSection = 0
    }
    Column(modifier = modifier.fillMaxSize()) {
        if (!browserViewModel.immersive) {
            PrimaryTabRow(selectedTabIndex = selected) {
                sections.forEachIndexed { index, title ->
                    Tab(
                        selected = selected == index,
                        onClick = { browserViewModel.hubSection = index },
                        text = { Text(title) },
                    )
                }
            }
        }
        when (selected) {
            0 -> BrowserScreen(browserViewModel)
            else -> GamesScreen()
        }
    }
}
