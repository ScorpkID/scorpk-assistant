package com.scorpk.assistant.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.scorpk.assistant.data.local.ThemeMode

private fun colorSchemeFor(palette: ScorpkPalette): ColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = OledBlack,
    primaryContainer = AccentBlueContainer,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = OledBlack,
    secondaryContainer = palette.elevated,
    onSecondaryContainer = TextPrimary,
    tertiary = SuccessGreen,
    onTertiary = OledBlack,
    background = palette.background,
    onBackground = TextPrimary,
    surface = palette.background,
    onSurface = TextPrimary,
    surfaceVariant = palette.input,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = palette.background,
    surfaceContainerLow = palette.card,
    surfaceContainer = palette.card,
    surfaceContainerHigh = palette.elevated,
    surfaceContainerHighest = palette.input,
    inverseSurface = TextPrimary,
    inverseOnSurface = OledBlack,
    outline = DividerColor,
    outlineVariant = DividerColor,
    error = ErrorRed,
    onError = OledBlack,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorRed,
    scrim = OledBlack
)

@Composable
fun ScorpkTheme(themeMode: ThemeMode = ThemeMode.OLED, content: @Composable () -> Unit) {
    val palette = when (themeMode) {
        ThemeMode.OLED -> OledPalette
        ThemeMode.DIM -> DimPalette
    }
    CompositionLocalProvider(LocalScorpkPalette provides palette) {
        MaterialTheme(
            colorScheme = colorSchemeFor(palette),
            typography = Typography,
            content = content
        )
    }
}
