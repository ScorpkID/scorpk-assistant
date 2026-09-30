package com.scorpk.assistant.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scorpk.assistant.domain.AssistantText
import com.scorpk.assistant.domain.model.ChatMessage
import com.scorpk.assistant.domain.model.CommandSource
import com.scorpk.assistant.domain.model.MessageRole
import com.scorpk.assistant.ui.components.MarkdownText
import com.scorpk.assistant.ui.components.ScorpkLogo
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.CardSurface
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.InputSurface
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary

private val SUGGESTIONS = listOf(
    "Prende la linterna",
    "¿Qué tengo mañana?",
    "Pon lo-fi en Spotify",
    "¿Cuánta batería tengo?",
    "Temporizador de 5 minutos",
    "¿Qué puedes hacer?"
)

@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    isProcessing: Boolean,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty() && !isProcessing) {
        EmptyState(onSuggestionClick = onSuggestionClick, modifier = modifier)
        return
    }

    val listState = rememberLazyListState()
    val itemCount = messages.size + if (isProcessing) 1 else 0
    LaunchedEffect(itemCount) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            Box(modifier = Modifier.appear(isRecent(message))) {
                when (message.role) {
                    MessageRole.USER -> UserBubble(message)
                    MessageRole.ASSISTANT -> AssistantMessage(message)
                }
            }
        }
        if (isProcessing) {
            item(key = "processing") { ThinkingIndicator() }
        }
    }
}

/** Mensaje del usuario: píldora refinada alineada a la derecha. */
@Composable
private fun UserBubble(message: ChatMessage) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End) {
            message.attachmentName?.let { name ->
                SentAttachmentChip(
                    name = name,
                    mimeType = message.attachmentMime,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(InputSurface)
                    .padding(horizontal = 18.dp, vertical = 11.dp)
            ) {
                Text(message.text, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
            if (message.source != CommandSource.TEXT) {
                Row(
                    modifier = Modifier.padding(top = 6.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (message.source == CommandSource.WAKE_WORD) "Oye Scorpk" else "Voz",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

/**
 * Respuesta de Scorpk con el logo como avatar. Según el tipo: texto libre con Markdown (conversación),
 * tarjeta de lista (agenda, correo, archivos…) o tarjeta de acción (confirmación con ícono).
 */
@Composable
private fun AssistantMessage(message: ChatMessage) {
    val (body, note) = AssistantText.split(message.text)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ScorpkLogo(size = 26.dp, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            when {
                !message.success -> ActionCard(body, message.action, success = false, modifier = Modifier.fillMaxWidth())
                isListAction(message.action) -> ListCard(body, message.action, modifier = Modifier.fillMaxWidth())
                isConversationAction(message.action) -> SelectionContainer {
                    MarkdownText(body, color = TextPrimary)
                }
                else -> ActionCard(body, message.action, success = true, modifier = Modifier.fillMaxWidth())
            }
            note?.let { SystemNote(it) }
        }
    }
}

/** Entrada suave (aparece y sube un poco) para los mensajes recién llegados. */
@Composable
private fun Modifier.appear(enabled: Boolean): Modifier {
    if (!enabled) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 22.dp.toPx()
    }
}

private fun isRecent(message: ChatMessage): Boolean =
    System.currentTimeMillis() - message.timestamp < RECENT_MESSAGE_MS

private const val RECENT_MESSAGE_MS = 3_500L

@Composable
private fun ThinkingIndicator() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        ScorpkLogo(size = 26.dp, alpha = pulse)
        Spacer(Modifier.width(14.dp))
        Text("Pensando…", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.alpha(pulse))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(onSuggestionClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Halo sutil detrás del logo.
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .background(
                        Brush.radialGradient(listOf(AccentBlue.copy(alpha = 0.14f), AccentBlue.copy(alpha = 0f))),
                        CircleShape
                    )
            )
            ScorpkLogo(size = 88.dp, alpha = 0.85f)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Hola, ¿qué hago por ti?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Escribe, habla o di \"Oye Scorpk\".",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(36.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SUGGESTIONS.forEach { suggestion ->
                Surface(
                    onClick = { onSuggestionClick(suggestion) },
                    shape = RoundedCornerShape(50),
                    color = CardSurface,
                    border = BorderStroke(1.dp, DividerColor)
                ) {
                    Text(
                        suggestion,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
