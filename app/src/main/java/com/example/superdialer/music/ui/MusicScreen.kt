package com.example.superdialer.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.superdialer.R
import com.example.superdialer.i18n.str

/**
 * The Music section of the Apps tab: Home / Search / Your Library as pills on top, with album, artist, playlist and
 * other pages pushed over them. Back pops a pushed page first.
 */
@Composable
fun MusicScreen(modifier: Modifier = Modifier) {
    BackHandler(enabled = MusicUi.stack.isNotEmpty()) { MusicUi.back() }
    val top = MusicUi.stack.lastOrNull()
    Column(modifier.fillMaxSize()) {
        if (top == null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tabs = listOf(
                    Triple(R.string.music_tab_home, Icons.Filled.Home, "music_tab_home"),
                    Triple(R.string.music_tab_search, Icons.Filled.Search, "music_tab_search"),
                    Triple(R.string.music_tab_library, Icons.Filled.LibraryMusic, "music_tab_library"),
                )
                tabs.forEachIndexed { i, (label, icon, tag) ->
                    FilterChip(
                        selected = MusicUi.tab == i,
                        onClick = { MusicUi.tab = i },
                        label = { Text(str(label), maxLines = 1) },
                        leadingIcon = { Icon(icon, null) },
                        modifier = Modifier.testTag(tag),
                    )
                }
            }
        }
        AnimatedContent(
            targetState = top ?: MusicUi.tab,
            transitionSpec = {
                if (targetState is Dest) {
                    (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(260))) togetherWith fadeOut(tween(120))
                } else if (initialState is Dest) {
                    fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(200)))
                } else {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                }
            },
            label = "musicPage",
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                is Dest -> DestScreen(page)
                1 -> SearchScreen()
                2 -> LibraryScreen()
                else -> HomeScreen()
            }
        }
    }
}
