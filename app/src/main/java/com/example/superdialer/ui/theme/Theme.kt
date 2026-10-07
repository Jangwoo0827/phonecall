package com.example.superdialer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superdialer.settings.AppSettings

// Own palette instead of the wallpaper-derived one: a clear green brand color, a slate-blue secondary and an amber
// accent (favorites). Cards sit one step lighter/darker than the page so lists read as grouped containers.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0A8F5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDF4E1),
    onPrimaryContainer = Color(0xFF00391F),
    secondary = Color(0xFF3F6A8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E7F4),
    onSecondaryContainer = Color(0xFF0B2A3F),
    tertiary = Color(0xFFB26A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE7B5),
    onTertiaryContainer = Color(0xFF3A2400),
    background = Color(0xFFF1F5F3),
    onBackground = Color(0xFF16201B),
    surface = Color(0xFFF1F5F3),
    onSurface = Color(0xFF16201B),
    surfaceVariant = Color(0xFFDCE5E0),
    onSurfaceVariant = Color(0xFF52625A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAF8),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE6EDE9),
    surfaceContainerHighest = Color(0xFFDCE5E0),
    outline = Color(0xFF86968E),
    outlineVariant = Color(0xFFCAD6CF),
    error = Color(0xFFD33B2C),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3DDC97),
    onPrimary = Color(0xFF00371F),
    primaryContainer = Color(0xFF0F4D33),
    onPrimaryContainer = Color(0xFFBDF5D8),
    secondary = Color(0xFF9CC4E0),
    onSecondary = Color(0xFF0B2A3F),
    secondaryContainer = Color(0xFF254B66),
    onSecondaryContainer = Color(0xFFD6E7F4),
    tertiary = Color(0xFFFFC857),
    onTertiary = Color(0xFF3A2400),
    tertiaryContainer = Color(0xFF5A3D00),
    onTertiaryContainer = Color(0xFFFFE7B5),
    background = Color(0xFF0C1110),
    onBackground = Color(0xFFE3EBE6),
    surface = Color(0xFF0C1110),
    onSurface = Color(0xFFE3EBE6),
    surfaceVariant = Color(0xFF2A352F),
    onSurfaceVariant = Color(0xFFA6B6AE),
    surfaceContainerLowest = Color(0xFF080C0B),
    surfaceContainerLow = Color(0xFF121816),
    surfaceContainer = Color(0xFF18201D),
    surfaceContainerHigh = Color(0xFF212A26),
    surfaceContainerHighest = Color(0xFF2B3631),
    outline = Color(0xFF6E7F76),
    outlineVariant = Color(0xFF34413A),
    error = Color(0xFFFF8A7A),
    onError = Color(0xFF410002),
    errorContainer = Color(0xFF7A1C13),
    onErrorContainer = Color(0xFFFFDAD4),
)

// Heavier weights for names and titles; the rest keeps the Material scale.
private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SuperDialerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = AppSettings.dynamicColor,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

private fun isDark(background: Color) = background.luminance() < 0.5f

/** Strong green for filled call buttons (white icon on top). Same in both themes. */
val CallButtonGreen = Color(0xFF16A34A)

/** Red for hang up / reject buttons. */
val EndCallRed = Color(0xFFD92D20)

/** Green for call icons drawn on a tonal surface; lighter in dark mode so it stays readable. */
@Composable
fun callGreen(): Color = if (isDark(MaterialTheme.colorScheme.background)) Color(0xFF4ADE80) else Color(0xFF0F8A3F)

/** Color of a call-type icon: incoming green, outgoing blue, missed red. */
@Composable
fun incomingColor(): Color = callGreen()

@Composable
fun outgoingColor(): Color = if (isDark(MaterialTheme.colorScheme.background)) Color(0xFF7CB7F0) else Color(0xFF1F6FBF)

/** Warm accent for favorites. */
@Composable
fun starColor(): Color = MaterialTheme.colorScheme.tertiary.let { if (isDark(MaterialTheme.colorScheme.background)) Color(0xFFFFC857) else Color(0xFFE59A00) }
