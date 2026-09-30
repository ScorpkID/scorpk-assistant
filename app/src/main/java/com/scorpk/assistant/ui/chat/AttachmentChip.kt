package com.scorpk.assistant.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scorpk.assistant.domain.model.Attachment
import com.scorpk.assistant.domain.model.AttachmentKind
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.AccentBlueContainer
import com.scorpk.assistant.ui.theme.CardSurface
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary
import com.scorpk.assistant.util.formatFileSize

/** Previsualización del archivo pendiente de enviar (miniatura o ícono + nombre + tamaño). */
@Composable
fun AttachmentPreview(attachment: Attachment, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumbnail = attachment.thumbnail
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
        } else {
            FileIcon(iconFor(attachment.kind, attachment.mimeType))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.widthIn(max = 200.dp)) {
            Text(
                attachment.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = listOf(
                formatFileSize(attachment.sizeBytes),
                if (attachment.kind == AttachmentKind.TEXT && attachment.truncated) "se enviará un extracto" else ""
            ).filter { it.isNotBlank() }.joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Quitar adjunto", tint = TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** Chip compacto del adjunto dentro de una burbuja ya enviada. */
@Composable
fun SentAttachmentChip(name: String, mimeType: String?, modifier: Modifier = Modifier) {
    val kind = when {
        mimeType?.startsWith("image/") == true -> AttachmentKind.IMAGE
        mimeType?.startsWith("text/") == true -> AttachmentKind.TEXT
        else -> AttachmentKind.DOCUMENT
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(iconFor(kind, mimeType), contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp)
        )
    }
}

@Composable
private fun FileIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AccentBlueContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(22.dp))
    }
}

private fun iconFor(kind: AttachmentKind, mimeType: String?): ImageVector = when (kind) {
    AttachmentKind.IMAGE -> Icons.Outlined.Image
    AttachmentKind.TEXT -> Icons.Outlined.Description
    AttachmentKind.DOCUMENT -> if (mimeType == "application/pdf") Icons.Outlined.Description else Icons.AutoMirrored.Outlined.InsertDriveFile
}
