package com.example.superdialer.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.superdialer.music.SpotifyWeb

/**
 * The Music section of the Apps tab is Spotify's web player (see [SpotifyWeb]). Back walks the page's own history first.
 * The earlier Deezer/Audius home, search and library screens are no longer reachable from here.
 */
@Composable
fun MusicScreen(modifier: Modifier = Modifier) {
    BackHandler(enabled = SpotifyWeb.canGoBack()) { SpotifyWeb.goBack() }
    SpotifyScreen(modifier)
}
