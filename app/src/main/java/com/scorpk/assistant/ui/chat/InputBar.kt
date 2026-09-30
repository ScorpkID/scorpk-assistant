package com.scorpk.assistant.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scorpk.assistant.domain.model.Attachment
import com.scorpk.assistant.ui.components.VoiceWave
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.CardSurface
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.ElevatedSurface
import com.scorpk.assistant.ui.theme.InputSurface
import com.scorpk.assistant.ui.theme.OledBlack
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary

/** Estado de escucha mostrado en la barra: null cuando el micrófono está inactivo. */
data class ListeningUi(val transcript: String, val level: Float)

@Composable
fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onCancelListening: () -> Unit,
    listening: ListeningUi?,
    attachment: Attachment?,
    loadingAttachment: Boolean,
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
    onRemoveAttachment: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        shape = RoundedCornerShape(30.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, DividerColor),
        shadowElevation = 0.dp
    ) {
        Column {
            if (attachment != null && listening == null) {
                AttachmentPreview(
                    attachment = attachment,
                    onRemove = onRemoveAttachment,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
                )
            }
            if (listening != null) {
                ListeningRow(listening = listening, onStop = onMicClick, onCancel = onCancelListening)
            } else {
                EditingRow(
                    value = value,
                    onValueChange = onValueChange,
                    onSend = onSend,
                    onMicClick = onMicClick,
                    canSend = value.isNotBlank() || attachment != null,
                    loadingAttachment = loadingAttachment,
                    onPickImage = onPickImage,
                    onPickDocument = onPickDocument,
                    enabled = enabled
                )
            }
        }
    }
}

/** Botón "+" con menú para elegir imagen o documento del selector nativo. */
@Composable
private fun AttachButton(
    loading: Boolean,
    enabled: Boolean,
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            enabled = enabled && !loading,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(InputSurface)
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentBlue)
            } else {
                Icon(Icons.Filled.Add, contentDescription = "Adjuntar archivo", tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = ElevatedSurface
        ) {
            DropdownMenuItem(
                text = { Text("Imagen", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null, tint = AccentBlue) },
                onClick = {
                    expanded = false
                    onPickImage()
                }
            )
            DropdownMenuItem(
                text = { Text("Documento", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = AccentBlue) },
                onClick = {
                    expanded = false
                    onPickDocument()
                }
            )
        }
    }
}

@Composable
private fun EditingRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    canSend: Boolean,
    loadingAttachment: Boolean,
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.padding(start = 4.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AttachButton(
            loading = loadingAttachment,
            enabled = enabled,
            onPickImage = onPickImage,
            onPickDocument = onPickDocument
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
            placeholder = { Text("Pregúntale a Scorpk", color = TextSecondary) },
            textStyle = MaterialTheme.typography.bodyLarge,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentBlue
            )
        )
        if (canSend) {
            FilledIconButton(
                onClick = onSend,
                enabled = enabled,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = TextPrimary,
                    contentColor = OledBlack,
                    disabledContainerColor = InputSurface,
                    disabledContentColor = TextSecondary
                )
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Enviar", modifier = Modifier.size(20.dp))
            }
        } else {
            IconButton(
                onClick = onMicClick,
                enabled = enabled,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Hablar", tint = TextPrimary)
            }
        }
    }
}

@Composable
private fun ListeningRow(listening: ListeningUi, onStop: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .padding(start = 4.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Cancelar", tint = TextSecondary)
        }
        VoiceWave(level = listening.level, width = 32.dp, height = 22.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            listening.transcript.ifBlank { "Escuchando…" },
            style = MaterialTheme.typography.bodyLarge,
            color = if (listening.transcript.isBlank()) TextSecondary else TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        FilledIconButton(
            onClick = onStop,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = TextPrimary,
                contentColor = OledBlack
            )
        ) {
            Icon(Icons.Filled.Stop, contentDescription = "Terminar y enviar", modifier = Modifier.size(20.dp))
        }
    }
}
