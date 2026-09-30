package com.scorpk.assistant.ui.connectors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scorpk.assistant.R
import com.scorpk.assistant.connectors.ConnectorType
import com.scorpk.assistant.ui.theme.InputSurface

/** Mosaico con la marca del servicio: logo oficial cuando existe, o color de marca + ícono. */
@Composable
fun ConnectorLogo(type: ConnectorType, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.28f)
    val glyph = size * 0.55f
    val (container, icon) = when (type) {
        ConnectorType.YOUTUBE -> Color(0xFFFF0033) to Icons.Filled.PlayArrow
        ConnectorType.GOOGLE_CALENDAR -> Color(0xFF4285F4) to Icons.Outlined.CalendarMonth
        ConnectorType.GMAIL -> Color(0xFFEA4335) to Icons.Outlined.Mail
        ConnectorType.EMAIL -> Color(0xFF5F6368) to Icons.Outlined.Mail
        ConnectorType.CONTACTS -> Color(0xFF1A73E8) to Icons.Filled.Call
        ConnectorType.WHATSAPP -> Color(0xFF25D366) to Icons.AutoMirrored.Filled.Chat
        ConnectorType.MAPS -> Color(0xFF34A853) to Icons.Filled.Place
        else -> InputSurface to null
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        when {
            icon != null -> Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(glyph))
            type == ConnectorType.SPOTIFY -> BrandVector(R.drawable.ic_spotify_logo, glyph, Color.Unspecified)
            type == ConnectorType.GOOGLE_DRIVE -> BrandVector(R.drawable.ic_drive_logo, glyph, Color.Unspecified)
            type == ConnectorType.GITHUB -> BrandVector(R.drawable.ic_github_logo, glyph, Color.White)
        }
    }
}

@Composable
private fun BrandVector(resource: Int, size: Dp, tint: Color) {
    Icon(painterResource(resource), contentDescription = null, tint = tint, modifier = Modifier.size(size))
}
