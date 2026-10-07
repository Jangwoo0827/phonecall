package com.example.superdialer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// (light background, light text, dark background, dark text) per hue, picked by the name so a person keeps their color.
private val avatarPalette = listOf(
    listOf(0xFFD3F2E3, 0xFF0B5C39, 0xFF14543A, 0xFFBFF3D9),
    listOf(0xFFD5E6F8, 0xFF174B7A, 0xFF1F4D74, 0xFFCBE3FA),
    listOf(0xFFFFE3C2, 0xFF7A4300, 0xFF6B4306, 0xFFFFE0B8),
    listOf(0xFFF7D6E4, 0xFF8A1F4F, 0xFF6E2B4A, 0xFFFAD3E5),
    listOf(0xFFE3DAF7, 0xFF4A2C8A, 0xFF433071, 0xFFE0D6FA),
    listOf(0xFFFBD9D3, 0xFF8C2A1B, 0xFF70312A, 0xFFFAD6CF),
    listOf(0xFFD1EFF1, 0xFF0B5A62, 0xFF16525A, 0xFFC4EEF2),
    listOf(0xFFEBEFC7, 0xFF4F5A0D, 0xFF4B5222, 0xFFEAEFC2),
)

@Composable
fun InitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    fontSize: TextUnit = 18.sp,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val colors = avatarPalette[(name.trim().hashCode() and Int.MAX_VALUE) % avatarPalette.size]
    val container = Color(if (dark) colors[2] else colors[0])
    val content = Color(if (dark) colors[3] else colors[1])
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifEmpty { "#" },
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
    }
}
