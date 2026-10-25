package com.example.superdialer.browser

import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.superdialer.settings.ThemeMode
import com.example.superdialer.settings.WebDarkMode

/** "웹 페이지 다크 모드": the WebView's own darkening of pages that have no dark version of their own. */
object WebDark {
    /** Whether pages should be dark, given the web setting, the app theme choice and whether the phone is in dark mode. */
    fun wanted(mode: WebDarkMode, theme: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        WebDarkMode.OFF -> false
        WebDarkMode.ON -> true
        WebDarkMode.FOLLOW_APP -> when (theme) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    }

    fun apply(view: WebView, on: Boolean) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, on)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setForceDark(
                    view.settings,
                    if (on) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF,
                )
            }
        } catch (e: Exception) {
            // an old or odd WebView: pages simply stay as they are
        }
    }
}
