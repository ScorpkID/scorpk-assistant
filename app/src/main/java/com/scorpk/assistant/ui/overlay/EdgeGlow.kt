package com.scorpk.assistant.ui.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.floor

/** Paleta cíclica de la luz de borde: azul, violeta, rosa y cian de la marca Scorpk. */
private val GlowPalette = listOf(
    Color(0xFF4C8DFF),
    Color(0xFF9B6BFF),
    Color(0xFFFF6BC1),
    Color(0xFF3FE0E6)
)

private const val GRADIENT_STEPS = 16

/**
 * Iluminación que recorre todo el borde de la pantalla, como el asistente de Gemini.
 * [level] (0..1) intensifica el brillo con la voz; [active] enciende/apaga el efecto.
 */
@Composable
fun EdgeGlow(level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "edgeGlow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 5200, easing = LinearEasing)),
        label = "phase"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1500), RepeatMode.Reverse),
        label = "breathe"
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        val voice = level.coerceIn(0f, 1f)
        val strength = (if (active) 1f else 0.5f) * breathe * (0.75f + 0.25f * voice)
        drawGlow(phase, strength, voice)
    }
}

private fun cycleColor(t: Float): Color {
    val scaled = t * GlowPalette.size
    val index = floor(scaled).toInt().mod(GlowPalette.size)
    return lerp(GlowPalette[index], GlowPalette[(index + 1) % GlowPalette.size], scaled - floor(scaled))
}

/** Degradado angular periódico desplazado por [phase]: parece girar alrededor de la pantalla. */
private fun DrawScope.rotatingBrush(phase: Float): Brush {
    val stops = Array(GRADIENT_STEPS + 1) { i ->
        val position = i.toFloat() / GRADIENT_STEPS
        position to cycleColor((position - phase + 1f) % 1f)
    }
    return Brush.sweepGradient(colorStops = stops, center = Offset(size.width / 2f, size.height / 2f))
}

private fun DrawScope.drawGlow(phase: Float, strength: Float, level: Float) {
    val brush = rotatingBrush(phase)
    // Capas del borde hacia adentro: de un halo ancho y suave a una línea fina e intensa.
    val layers = listOf(
        (64f + 36f * level) to 0.09f,
        (40f + 24f * level) to 0.15f,
        (22f + 12f * level) to 0.24f,
        10f to 0.50f,
        4f to 0.95f
    )
    layers.forEach { (width, alpha) ->
        val inset = width / 2f
        drawRoundRect(
            brush = brush,
            topLeft = Offset(inset, inset),
            size = Size(size.width - width, size.height - width),
            cornerRadius = CornerRadius(72f, 72f),
            alpha = (alpha * strength).coerceIn(0f, 1f),
            style = Stroke(width = width)
        )
    }
}
