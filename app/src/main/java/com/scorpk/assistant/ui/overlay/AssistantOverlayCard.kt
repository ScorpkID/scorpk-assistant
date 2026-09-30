package com.scorpk.assistant.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scorpk.assistant.ui.components.MarkdownText
import com.scorpk.assistant.ui.components.ScorpkLogo
import com.scorpk.assistant.ui.components.VoiceWave
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.CardSurface
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.ErrorRed
import com.scorpk.assistant.ui.theme.InputSurface
import com.scorpk.assistant.ui.theme.OledBlack
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Tarjeta inferior flotante del asistente (estilo Gemini): estado de escucha, respuesta,
 * imágenes adjuntas, accesos a pantalla/cámara y un campo para escribir o dictar.
 */
@Composable
fun AssistantOverlayCard(
    state: OverlayState,
    actions: OverlayActions,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
    ) {
        AnimatedVisibility(
            visible = state.visible,
            enter = slideInVertically(tween(340)) { it } + fadeIn(tween(240)),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(tween(200))
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, DividerColor),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)) {
                    Header(state, actions)
                    Body(state)
                    if (state.images.isNotEmpty()) {
                        Spacer(Modifier.size(10.dp))
                        ImagesRow(state, actions)
                    }
                    if (state.screenEnabled || state.cameraEnabled) {
                        Spacer(Modifier.size(10.dp))
                        ActionChips(state, actions)
                    }
                    Spacer(Modifier.size(12.dp))
                    InputPill(state, actions)
                }
            }
        }
    }
}

@Composable
private fun Header(state: OverlayState, actions: OverlayActions) {
    val title = when (state.phase) {
        OverlayPhase.LISTENING -> "Escuchando…"
        OverlayPhase.IDLE -> "¿En qué te ayudo?"
        OverlayPhase.PROCESSING -> "Pensando…"
        OverlayPhase.RESULT -> if (state.success) "Listo" else "No pude hacerlo"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        ScorpkLogo(size = 30.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (state.phase == OverlayPhase.RESULT && !state.success) ErrorRed else TextPrimary,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = actions.onClose) {
            Icon(Icons.Outlined.Close, contentDescription = "Cerrar", tint = TextSecondary)
        }
    }
}

@Composable
private fun Body(state: OverlayState) {
    when (state.phase) {
        OverlayPhase.PROCESSING -> Row(
            modifier = Modifier.padding(top = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentBlue)
            Spacer(Modifier.width(10.dp))
            Text(
                state.submitted,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        OverlayPhase.RESULT -> Column(
            modifier = Modifier
                .padding(top = 6.dp, end = 6.dp)
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (state.submitted.isNotBlank()) {
                Text(
                    state.submitted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(8.dp))
            }
            if (state.success) {
                MarkdownText(state.response, color = TextPrimary)
            } else {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(state.response, style = MaterialTheme.typography.bodyLarge, color = ErrorRed)
                }
            }
        }
        OverlayPhase.LISTENING, OverlayPhase.IDLE -> {
            val hint = state.notice ?: if (state.phase == OverlayPhase.LISTENING && state.input.isBlank()) {
                "Habla ahora o escribe abajo"
            } else {
                null
            }
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.notice != null) ErrorRed else TextSecondary,
                    modifier = Modifier.padding(top = 6.dp, end = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ImagesRow(state: OverlayState, actions: OverlayActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        state.images.forEach { image ->
            Box {
                val thumbnail = image.thumbnail
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = image.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(OledBlack.copy(alpha = 0.75f))
                        .clickable { actions.onRemoveImage(image.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Quitar", tint = TextPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionChips(state: OverlayState, actions: OverlayActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.screenEnabled) {
            ActionChip("Pantalla", Icons.Outlined.Smartphone, loading = state.capturing, onClick = actions.onScreen)
        }
        if (state.cameraEnabled) {
            ActionChip("Cámara", Icons.Outlined.PhotoCamera, loading = false, onClick = actions.onCamera)
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, loading: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(50),
        color = InputSurface,
        border = BorderStroke(1.dp, DividerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentBlue)
            } else {
                Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
        }
    }
}

@Composable
private fun InputPill(state: OverlayState, actions: OverlayActions) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.focusTick) {
        if (state.focusTick > 0) {
            delay(80)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val busy = state.phase == OverlayPhase.PROCESSING
    val hasContent = state.input.isNotBlank() || state.images.isNotEmpty()

    Surface(
        shape = RoundedCornerShape(30.dp),
        color = InputSurface,
        border = BorderStroke(1.dp, DividerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = state.input,
                onValueChange = actions.onInputChange,
                enabled = !busy,
                placeholder = { Text("Pregúntale a Scorpk", color = TextSecondary) },
                textStyle = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { actions.onSend() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    disabledTextColor = TextSecondary,
                    cursorColor = AccentBlue
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 54.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) actions.onInputFocused() }
            )
            when {
                state.phase == OverlayPhase.LISTENING -> RoundButton(
                    onClick = actions.onMic,
                    container = TextPrimary
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = "Terminar y enviar", tint = OledBlack, modifier = Modifier.size(20.dp))
                }
                hasContent && !busy -> RoundButton(
                    onClick = actions.onSend,
                    container = TextPrimary
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Enviar", tint = OledBlack, modifier = Modifier.size(20.dp))
                }
                else -> RoundButton(
                    onClick = actions.onMic,
                    enabled = !busy,
                    container = CardSurface
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Hablar", tint = TextPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
    if (state.phase == OverlayPhase.LISTENING) {
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            VoiceWave(level = state.level, width = 52.dp, height = 24.dp)
        }
    }
}

@Composable
private fun RoundButton(
    onClick: () -> Unit,
    container: Color,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = container,
        modifier = Modifier.size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
