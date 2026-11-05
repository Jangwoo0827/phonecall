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
            "crossyroads" -> drawCrossy()
            "flap" -> drawFlap()
            "tetris" -> drawBlocks()
            "moles" -> drawMoles()
            "stack" -> drawStack()
            "dodge" -> drawDodge()
            "simon" -> drawSimon()
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

/** A boxy chick standing on a lane with a car block coming along the road. */
private fun DrawScope.drawCrossy() {
    val w = size.width
    val radius = CornerRadius(w * 0.04f)
    // road band with dashed lane marking
    drawRoundRect(Color(0x55000000), Offset(w * 0.08f, w * 0.52f), Size(w * 0.84f, w * 0.34f), radius)
    for (i in 0 until 4) {
        drawRoundRect(soft(0.7f), Offset(w * (0.14f + i * 0.2f), w * 0.685f), Size(w * 0.11f, w * 0.03f), CornerRadius(w * 0.015f))
    }
    // car
    drawRoundRect(Color(0xFFFFEB3B), Offset(w * 0.5f, w * 0.58f), Size(w * 0.34f, w * 0.15f), radius)
    drawRoundRect(soft(0.9f), Offset(w * 0.58f, w * 0.545f), Size(w * 0.17f, w * 0.07f), CornerRadius(w * 0.02f))
    // chick: body, beak, eye
    drawRoundRect(white, Offset(w * 0.2f, w * 0.14f), Size(w * 0.3f, w * 0.34f), radius)
    drawRoundRect(Color(0xFFFFB300), Offset(w * 0.47f, w * 0.27f), Size(w * 0.1f, w * 0.07f), CornerRadius(w * 0.015f))
    drawRoundRect(Color(0xFFE53935), Offset(w * 0.3f, w * 0.1f), Size(w * 0.1f, w * 0.06f), CornerRadius(w * 0.015f))
    drawCircle(Color(0xCC000000), w * 0.024f, Offset(w * 0.42f, w * 0.25f))
}

/** A round bird between two pipe ends. */
private fun DrawScope.drawFlap() {
    val w = size.width
    val radius = CornerRadius(w * 0.03f)
    drawRoundRect(soft(0.55f), Offset(w * 0.1f, 0f), Size(w * 0.2f, w * 0.38f), radius)
    drawRoundRect(soft(0.55f), Offset(w * 0.1f, w * 0.7f), Size(w * 0.2f, w * 0.3f), radius)
    drawRoundRect(soft(0.8f), Offset(w * 0.07f, w * 0.34f), Size(w * 0.26f, w * 0.07f), radius)
    drawRoundRect(soft(0.8f), Offset(w * 0.07f, w * 0.7f), Size(w * 0.26f, w * 0.07f), radius)
    drawCircle(Color(0xFFFFEB3B), w * 0.17f, Offset(w * 0.62f, w * 0.52f))
    drawCircle(white, w * 0.05f, Offset(w * 0.68f, w * 0.47f))
    drawCircle(Color(0xCC000000), w * 0.022f, Offset(w * 0.70f, w * 0.47f))
    val beak = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.77f, w * 0.52f); lineTo(w * 0.9f, w * 0.56f); lineTo(w * 0.77f, w * 0.6f); close()
    }
    drawPath(beak, Color(0xFFFF7043))
}

/** Falling tetromino pieces. */
private fun DrawScope.drawBlocks() {
    val w = size.width
    val c = w * 0.17f
    val radius = CornerRadius(c * 0.2f)
    fun cell(col: Int, row: Int, alpha: Float) =
        drawRoundRect(soft(alpha), Offset(w * 0.2f + col * (c + 2f), w * 0.14f + row * (c + 2f)), Size(c, c), radius)
    cell(1, 0, 0.95f); cell(0, 1, 0.95f); cell(1, 1, 0.95f); cell(2, 1, 0.95f)
    cell(0, 3, 0.6f); cell(1, 3, 0.6f); cell(2, 3, 0.6f); cell(3, 3, 0.6f)
    cell(2, 2, 0.4f)
}

/** A mole peeking out of its hole. */
private fun DrawScope.drawMoles() {
    val w = size.width
    drawOval(Color(0x66000000), Offset(w * 0.14f, w * 0.64f), Size(w * 0.72f, w * 0.24f))
    drawCircle(Color(0xFFD7B9A4), w * 0.27f, Offset(w * 0.5f, w * 0.52f))
    drawCircle(white, w * 0.05f, Offset(w * 0.4f, w * 0.44f))
    drawCircle(white, w * 0.05f, Offset(w * 0.6f, w * 0.44f))
    drawCircle(Color(0xCC000000), w * 0.024f, Offset(w * 0.4f, w * 0.45f))
    drawCircle(Color(0xCC000000), w * 0.024f, Offset(w * 0.6f, w * 0.45f))
    drawCircle(Color(0xFFE57373), w * 0.045f, Offset(w * 0.5f, w * 0.54f))
    drawOval(Color(0x99000000), Offset(w * 0.1f, w * 0.7f), Size(w * 0.8f, w * 0.2f))
}

/** A leaning tower of colored slabs. */
private fun DrawScope.drawStack() {
    val w = size.width
    val h = w * 0.13f
    val radius = CornerRadius(h * 0.2f)
    val widths = listOf(0.62f, 0.56f, 0.46f, 0.4f, 0.32f)
    val shifts = listOf(0.19f, 0.22f, 0.27f, 0.3f, 0.36f)
    for (i in widths.indices) {
        drawRoundRect(soft(0.95f - i * 0.12f), Offset(w * shifts[i], w * 0.82f - (i + 1) * (h + 3f)), Size(w * widths[i], h), radius)
    }
}

/** A ship-like ball with blocks raining down. */
private fun DrawScope.drawDodge() {
    val w = size.width
    val radius = CornerRadius(w * 0.02f)
    drawRoundRect(soft(0.85f), Offset(w * 0.14f, w * 0.12f), Size(w * 0.26f, w * 0.1f), radius)
    drawRoundRect(soft(0.6f), Offset(w * 0.56f, w * 0.26f), Size(w * 0.3f, w * 0.1f), radius)
    drawRoundRect(soft(0.45f), Offset(w * 0.3f, w * 0.44f), Size(w * 0.18f, w * 0.1f), radius)
    drawCircle(Color(0xFF80DEEA), w * 0.12f, Offset(w * 0.55f, w * 0.78f))
    drawCircle(white, w * 0.028f, Offset(w * 0.51f, w * 0.75f))
    drawCircle(white, w * 0.028f, Offset(w * 0.6f, w * 0.75f))
}

/** Four lit pads. */
private fun DrawScope.drawSimon() {
    val w = size.width
    val pad = w * 0.36f
    val gap = w * 0.06f
    val radius = CornerRadius(w * 0.1f)
    val colors = listOf(Color(0xFF66BB6A), Color(0xFFEF5350), Color(0xFFFFEE58), Color(0xFF42A5F5))
    for (i in 0 until 4) {
        val x = w / 2 - pad - gap / 2 + (i % 2) * (pad + gap)
        val y = w / 2 - pad - gap / 2 + (i / 2) * (pad + gap)
        drawRoundRect(colors[i].copy(alpha = if (i == 1) 1f else 0.55f), Offset(x, y), Size(pad, pad), radius)
    }
}

private fun DrawScope.drawDots() {
    val w = size.width
    for (r in 0 until 3) for (c in 0 until 3) {
        drawCircle(soft(0.85f), w * 0.07f, Offset(w * (0.25f + c * 0.25f), w * (0.25f + r * 0.25f)))
    }
}
