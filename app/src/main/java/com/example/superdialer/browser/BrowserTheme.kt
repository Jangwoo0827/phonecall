package com.example.superdialer.browser

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Private browsing look: a dark violet palette (as in other browsers' incognito mode) so it is clear at a glance. */
private val privateScheme = darkColorScheme(
    primary = Color(0xFFCDBDFF),
    onPrimary = Color(0xFF2D1B6B),
    primaryContainer = Color(0xFF453487),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondaryContainer = Color(0xFF3B3555),
    onSecondaryContainer = Color(0xFFE6DFFF),
    background = Color(0xFF14111F),
    onBackground = Color(0xFFE7E1F2),
    surface = Color(0xFF1B1727),
    onSurface = Color(0xFFE7E1F2),
    onSurfaceVariant = Color(0xFFC9C3D6),
    surfaceContainer = Color(0xFF262236),
    surfaceContainerHigh = Color(0xFF302B42),
    surfaceContainerHighest = Color(0xFF3A354D),
    outlineVariant = Color(0xFF494458),
    error = Color(0xFFFFB4AB),
)

/** Shows [content] in the private palette when [private] is true, unchanged otherwise. */
@Composable
internal fun PrivateTheme(private: Boolean, content: @Composable () -> Unit) {
    if (private) {
        MaterialTheme(colorScheme = privateScheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes, content = content)
    } else {
        content()
    }
}
