package com.scorpk.assistant.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scorpk.assistant.ui.components.MarkdownText
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.AccentBlueContainer
import com.scorpk.assistant.ui.theme.CardSurface
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.ErrorContainer
import com.scorpk.assistant.ui.theme.ErrorRed
import com.scorpk.assistant.ui.theme.SuccessGreen
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary

/** Respuestas con datos (agenda, correo, archivos…): se muestran como una tarjeta con título y filas. */
private val LIST_ACTIONS = setOf("calendar_query", "gmail_inbox", "drive_search", "github_query", "read_screen")

fun isListAction(action: String?): Boolean = action in LIST_ACTIONS

/** Respuesta conversacional: texto libre, sin tarjeta. */
fun isConversationAction(action: String?): Boolean = action == null || action == "respond_chat"

fun actionIcon(action: String?): ImageVector = when (action) {
    "open_app" -> Icons.Outlined.Apps
    "toggle_flashlight" -> Icons.Outlined.FlashlightOn
    "set_alarm" -> Icons.Outlined.Alarm
    "set_timer" -> Icons.Outlined.Timer
    "media_control", "spotify_control" -> Icons.Outlined.MusicNote
    "volume_control" -> Icons.AutoMirrored.Outlined.VolumeUp
    "battery_status" -> Icons.Outlined.BatteryFull
    "global_action", "read_screen" -> Icons.Outlined.Smartphone
    "click_node", "gesture" -> Icons.Outlined.TouchApp
    "send_message", "whatsapp_message" -> Icons.AutoMirrored.Outlined.Send
    "calendar_query", "calendar_create" -> Icons.Outlined.Event
    "drive_search" -> Icons.Outlined.Folder
    "gmail_inbox", "compose_email" -> Icons.Outlined.Mail
    "github_query" -> Icons.Outlined.Code
    "call_contact" -> Icons.Outlined.Call
    "navigate" -> Icons.Outlined.Navigation
    "youtube_search" -> Icons.Outlined.PlayCircle
    else -> Icons.Outlined.AutoAwesome
}

/** Confirmación de una acción: ícono de la acción, la frase y una marca de éxito o error. */
@Composable
fun ActionCard(text: String, action: String?, success: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, if (success) DividerColor else ErrorRed.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(if (success) actionIcon(action) else Icons.Outlined.ErrorOutline, success)
            Spacer(Modifier.width(12.dp))
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (success) TextPrimary else ErrorRed
                )
            }
            if (success) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.CheckCircle, contentDescription = "Hecho", tint = SuccessGreen, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Resultado con datos: la primera línea es el título; el resto, las filas (con enlaces pulsables). */
@Composable
fun ListCard(text: String, action: String?, modifier: Modifier = Modifier) {
    val lines = text.lines()
    val title = lines.firstOrNull().orEmpty().trimEnd(':').ifBlank { "Resultado" }
    val rows = lines.drop(1).joinToString("\n").trim()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, DividerColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(actionIcon(action), success = true)
                Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            if (rows.isNotEmpty()) {
                SelectionContainer {
                    MarkdownText(rows, color = TextPrimary, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, success: Boolean) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (success) AccentBlueContainer else ErrorContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (success) AccentBlue else ErrorRed, modifier = Modifier.size(20.dp))
    }
}

/** Nota discreta del sistema bajo una respuesta (p. ej. «usé el modo local»). */
@Composable
fun SystemNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 4.dp)
    )
}
