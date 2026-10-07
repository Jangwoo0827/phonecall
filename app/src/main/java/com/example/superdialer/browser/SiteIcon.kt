package com.example.superdialer.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superdialer.browser.data.FaviconStore

private val fallbackColors = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935), Color(0xFF8E24AA),
    Color(0xFFF4511E), Color(0xFF00897B), Color(0xFF3949AB), Color(0xFF7CB342),
)

/**
 * A site's own icon on a light rounded tile (so dark logos stay readable in dark mode). Shows a colored
 * first-letter tile until the icon is available, or when the site has none.
 */
@Composable
internal fun SiteIcon(
    url: String,
    title: String,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = size * 0.28f,
) {
    val store = FaviconStore.get(LocalContext.current)
    var icon by remember(url) { mutableStateOf(store.peek(url)) }
    LaunchedEffect(url) {
        if (icon == null) icon = store.load(url)
    }

    val shape = RoundedCornerShape(corner)
    val bitmap = icon
    if (bitmap != null) {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size).padding(size * 0.18f),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
            )
        }
    } else {
        val color = fallbackColors[(title.hashCode() and Int.MAX_VALUE) % fallbackColors.size]
        Box(
            modifier = modifier.size(size).clip(shape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title.trim().take(1).uppercase().ifEmpty { "?" },
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
