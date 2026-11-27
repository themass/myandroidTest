package com.openapi.ks.myapp.dh

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Local camera preview; offsets are relative to the bottom-left starting position. */
@Composable
internal fun DraggableCameraPreview(content: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { 108.dp.toPx() }
        val height = with(density) { 144.dp.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val bottom = with(density) { 120.dp.toPx() }
        val availableX = (constraints.maxWidth - width - 2 * margin).coerceAtLeast(0f)
        val availableY = (constraints.maxHeight - height - 2 * margin).coerceAtLeast(0f)
        val initialY = (constraints.maxHeight - height - bottom).coerceIn(margin, margin + availableY)
        var dx by remember { mutableFloatStateOf(0f) }
        var dy by remember { mutableFloatStateOf(0f) }
        val x = (margin + dx).coerceIn(margin, margin + availableX)
        val y = (initialY + dy).coerceIn(margin, margin + availableY)
        Box(
            Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(108.dp, 144.dp)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(availableX, availableY, initialY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        dx = (dx + drag.x).coerceIn(0f, availableX)
                        dy = (dy + drag.y).coerceIn(margin - initialY, margin + availableY - initialY)
                    }
                },
        ) { content(Modifier.fillMaxSize()) }
    }
}
