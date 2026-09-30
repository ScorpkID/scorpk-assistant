package com.scorpk.assistant.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Negro puro: fondo OLED y contenido sobre acentos (texto/íconos negros sobre azul).
val OledBlack = Color(0xFF000000)
/** Borde sutil de tarjetas, barras y separadores. */
val DividerColor = Color(0xFF1C1C24)

// Texto
val TextPrimary = Color(0xFFF5F5F7)
val TextSecondary = Color(0xFF8E8E93)
val TextDisabled = Color(0xFF48484E)

// Acentos discretos
val AccentBlue = Color(0xFF8AB4F8)
val AccentBlueContainer = Color(0xFF1B2A41)
val SuccessGreen = Color(0xFF81C995)
val ErrorRed = Color(0xFFF28B82)
val ErrorContainer = Color(0xFF3B1D1B)

/** Superficies que cambian según el tema elegido por el usuario. */
data class ScorpkPalette(
    val background: Color,
    val card: Color,
    val input: Color,
    val elevated: Color
)

/** Negro puro OLED: fondo #000000, tarjetas #0D0D11, burbujas/inputs #1E1E26. */
val OledPalette = ScorpkPalette(
    background = Color(0xFF000000),
    card = Color(0xFF0D0D11),
    input = Color(0xFF1E1E26),
    elevated = Color(0xFF15151B)
)

/** Oscuro atenuado: fondo #121212 con superficies un punto más claras. */
val DimPalette = ScorpkPalette(
    background = Color(0xFF121212),
    card = Color(0xFF1A1A20),
    input = Color(0xFF26262F),
    elevated = Color(0xFF202027)
)

val LocalScorpkPalette = staticCompositionLocalOf { OledPalette }

val AppBackground: Color
    @Composable @ReadOnlyComposable get() = LocalScorpkPalette.current.background

val CardSurface: Color
    @Composable @ReadOnlyComposable get() = LocalScorpkPalette.current.card

val InputSurface: Color
    @Composable @ReadOnlyComposable get() = LocalScorpkPalette.current.input

val ElevatedSurface: Color
    @Composable @ReadOnlyComposable get() = LocalScorpkPalette.current.elevated
