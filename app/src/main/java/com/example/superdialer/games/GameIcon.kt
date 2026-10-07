package com.example.superdialer.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** Hand-drawn emblem per game (no image assets); unknown ids get a plain play-style dot grid. */
@Composable
internal fun GameIcon(gameId: String, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        when (gameId) {
            "2048" -> draw2048()
            "snake" -> drawSnake()
            "breakout" -> drawBreakout()
            else -> drawDots()
        }
    }
}

private val white = Color.White
private fun soft(alpha: Float) = Color.White.copy(alpha = alpha)

/** A 2x2 block of merging number tiles. */
private fun DrawScope.draw2048() {
    val gap = size.width * 0.06f
    val tile = (size.width - gap * 3) / 2
    val radius = CornerRadius(tile * 0.18f)
    val shades = listOf(soft(0.95f), soft(0.7f), soft(0.55f), soft(0.35f))
    for (i in 0 until 4) {
        val x = gap + (i % 2) * (tile + gap)
        val y = gap + (i / 2) * (tile + gap)
        drawRoundRect(shades[i], Offset(x, y), Size(tile, tile), radius)
    }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB3000000.toInt()
        textSize = tile * 0.7f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = android.graphics.Paint.Align.CENTER
    }
    drawContext.canvas.nativeCanvas.drawText("2", gap + tile / 2, gap + tile * 0.72f, paint)
}

/** A snake body curling toward a food dot. */
private fun DrawScope.drawSnake() {
    val w = size.width
    val body = Stroke(width = w * 0.15f, cap = StrokeCap.Round)
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.16f, w * 0.72f)
        lineTo(w * 0.16f, w * 0.3f)
        lineTo(w * 0.5f, w * 0.3f)
        lineTo(w * 0.5f, w * 0.62f)
        lineTo(w * 0.76f, w * 0.62f)
    }
    drawPath(path, soft(0.92f), style = body)
    // head
    drawCircle(white, w * 0.12f, Offset(w * 0.76f, w * 0.62f))
    drawCircle(Color(0xCC000000), w * 0.028f, Offset(w * 0.79f, w * 0.59f))
    // food
    drawCircle(Color(0xFFFFEB3B), w * 0.07f, Offset(w * 0.84f, w * 0.24f))
}

/** Rows of bricks above a paddle and ball. */
private fun DrawScope.drawBreakout() {
    val w = size.width
    val brickW = w * 0.25f
    val brickH = w * 0.1f
    val gap = w * 0.04f
    val left = (w - (3 * brickW + 2 * gap)) / 2
    val radius = CornerRadius(brickH * 0.3f)
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            val alpha = if (row == 0) 0.95f else if (row == 1) 0.7f else 0.45f
            drawRoundRect(
                soft(alpha),
                Offset(left + col * (brickW + gap), w * 0.16f + row * (brickH + gap)),
                Size(brickW, brickH),
                radius,
            )
        }
    }
    drawCircle(Color(0xFFFFEB3B), w * 0.055f, Offset(w * 0.6f, w * 0.64f))
    drawRoundRect(white, Offset(w * 0.3f, w * 0.8f), Size(w * 0.4f, brickH * 0.9f), radius)
}

private fun DrawScope.drawDots() {
    val w = size.width
    for (r in 0 until 3) for (c in 0 until 3) {
        drawCircle(soft(0.85f), w * 0.07f, Offset(w * (0.25f + c * 0.25f), w * (0.25f + r * 0.25f)))
    }
}
