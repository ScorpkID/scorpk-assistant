package com.scorpk.assistant.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scorpk.assistant.ui.theme.AccentBlue

private val BAR_WEIGHTS = floatArrayOf(0.45f, 0.75f, 1f, 0.75f, 0.45f)

/** Onda de audio minimalista: cinco barras cuya altura sigue el nivel del micrófono (0..1). */
@Composable
fun VoiceWave(
    level: Float,
    modifier: Modifier = Modifier,
    color: Color = AccentBlue,
    width: Dp = 44.dp,
    height: Dp = 28.dp
) {
    val animated by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 90),
        label = "voiceLevel"
    )
    Canvas(modifier = modifier.size(width, height)) {
        val barCount = BAR_WEIGHTS.size
        val gap = size.width * 0.08f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val minHeight = barWidth
        BAR_WEIGHTS.forEachIndexed { index, weight ->
            val barHeight = minHeight + (size.height - minHeight) * animated * weight
            val x = index * (barWidth + gap)
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
