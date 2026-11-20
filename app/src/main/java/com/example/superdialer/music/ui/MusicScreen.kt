package com.example.superdialer.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.superdialer.R
import com.example.superdialer.browser.WebAudioKeepAlive
import com.example.superdialer.i18n.str
import com.example.superdialer.music.SpotifyWeb

/**
 * The Music section of the Apps tab is Spotify's web player (see [SpotifyWeb]). Back walks the page's own history first.
 * While the notification player is on, a thin bar offers to remove it (swiping the notification only brings it back).
 */
@Composable
fun MusicScreen(modifier: Modifier = Modifier) {
    BackHandler(enabled = SpotifyWeb.canGoBackNow) { SpotifyWeb.goBack() }
    Column(modifier.fillMaxSize()) {
        if (WebAudioKeepAlive.active) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(str(R.string.music_notif_on), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { SpotifyWeb.dismissNotification() }, modifier = Modifier.testTag("music_notif_dismiss")) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Text(str(R.string.music_notif_dismiss), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        SpotifyScreen(Modifier.weight(1f))
    }
}
