package com.scorpk.assistant.ui.account

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scorpk.assistant.R
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.CardSurface
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.ErrorRed
import com.scorpk.assistant.ui.theme.InputSurface
import com.scorpk.assistant.ui.theme.OledBlack
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary
import com.scorpk.assistant.util.ScorpkLinks
import com.scorpk.assistant.util.openUrl

/** Degradado de marca (azul → violeta) usado en el avatar y el logo del login. */
val BrandGradient = Brush.linearGradient(listOf(AccentBlue, Color(0xFFC58AF9)))

enum class AuthProvider(@param:DrawableRes val logo: Int, val label: String) {
    GOOGLE(R.drawable.ic_google_logo, "Google"),
    GITHUB(R.drawable.ic_github_logo, "GitHub");

    companion object {
        fun from(value: String?): AuthProvider? = when (value?.lowercase()) {
            "google" -> GOOGLE
            "github" -> GITHUB
            else -> null
        }
    }
}

/** Tarjeta con título opcional y acción a la derecha del encabezado. */
@Composable
fun AccountCard(
    title: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, DividerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            if (title != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    trailing?.invoke()
                }
            }
            content()
        }
    }
}

/** Avatar circular con la inicial del usuario sobre el degradado de marca. */
@Composable
fun InitialAvatar(name: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(BrandGradient),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "S",
            style = if (size >= 64.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = OledBlack
        )
    }
}

/** Botón de proveedor OAuth con su logo oficial a color. */
@Composable
fun SocialButton(provider: AuthProvider, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        color = InputSurface,
        border = BorderStroke(1.dp, DividerColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(provider.logo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Continuar con ${provider.label}",
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) TextPrimary else TextSecondary
            )
        }
    }
}

/** Chip pequeño con el logo del proveedor con el que inició sesión el usuario. */
@Composable
fun ProviderBadge(provider: AuthProvider) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(InputSurface)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(provider.logo),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(provider.label, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
    }
}

/** Pie con los enlaces oficiales; con [showConsent] añade el aviso de aceptación (pantalla de acceso). */
@Composable
fun LegalLinks(showConsent: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showConsent) {
            Text(
                "Al continuar aceptas los Términos y la Política de privacidad.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegalLink("Términos") { openUrl(context, ScorpkLinks.TERMS) }
            LegalDot()
            LegalLink("Privacidad") { openUrl(context, ScorpkLinks.PRIVACY) }
            LegalDot()
            LegalLink("scorpk.tech") { openUrl(context, ScorpkLinks.WEBSITE) }
        }
    }
}

@Composable
private fun LegalLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
    )
}

@Composable
private fun LegalDot() {
    Text("·", style = MaterialTheme.typography.labelMedium, color = DividerColor)
}

/** Insignia del plan: degradado de marca para Pro, discreta para Free. */
@Composable
fun PlanBadge(isPro: Boolean) {
    Text(
        if (isPro) "PRO" else "FREE",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (isPro) OledBlack else TextSecondary,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (isPro) BrandGradient else Brush.linearGradient(listOf(InputSurface, InputSurface)))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun ButtonSpinner() {
    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = OledBlack)
}

@Composable
fun accountFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentBlue,
    unfocusedBorderColor = DividerColor,
    errorBorderColor = ErrorRed,
    focusedLabelColor = AccentBlue,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLeadingIconColor = AccentBlue,
    unfocusedLeadingIconColor = TextSecondary,
    focusedContainerColor = InputSurface,
    unfocusedContainerColor = InputSurface,
    cursorColor = AccentBlue
)
