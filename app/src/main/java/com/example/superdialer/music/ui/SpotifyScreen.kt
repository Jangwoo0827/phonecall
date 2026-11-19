package com.example.superdialer.music.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.superdialer.music.SpotifyWeb

/**
 * Spotify's web player inside the Music tab. The page itself is kept by [SpotifyWeb] (so the music goes on when another chip
 * is opened or the app is left); this only puts it on screen.
 */
@Composable
fun SpotifyScreen(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            SpotifyWeb.attach(ctx)
            FrameLayout(ctx).apply {
                val web = SpotifyWeb.webView()
                (web.parent as? ViewGroup)?.removeView(web)
                addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        },
        onRelease = { frame ->
            frame.removeAllViews()
            SpotifyWeb.detach()
        },
    )
}
