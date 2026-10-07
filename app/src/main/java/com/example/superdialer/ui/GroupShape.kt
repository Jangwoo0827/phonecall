package com.example.superdialer.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Corner shape of row [index] in a group of [count] rows: big radius on the group's outer corners, small between rows. */
fun groupShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 6.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}
