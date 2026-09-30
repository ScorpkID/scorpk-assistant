package com.scorpk.assistant.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scorpk.assistant.R

/** Icono oficial de Scorpk (scorpk_logo, recortado de branding/scorpk-icon.png). */
@Composable
fun ScorpkLogo(size: Dp = 28.dp, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Image(
        painter = painterResource(R.drawable.scorpk_logo),
        contentDescription = "Scorpk",
        alpha = alpha,
        modifier = modifier.size(size)
    )
}
