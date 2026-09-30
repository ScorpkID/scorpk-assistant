package com.scorpk.assistant.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scorpk.assistant.data.remote.supabase.Profile
import com.scorpk.assistant.data.remote.supabase.Subscription
import com.scorpk.assistant.data.remote.supabase.TaskItem
import com.scorpk.assistant.data.repository.AuthState
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.AccentBlueContainer
import com.scorpk.assistant.ui.theme.CardSurface
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.ElevatedSurface
import com.scorpk.assistant.ui.theme.ErrorRed
import com.scorpk.assistant.ui.theme.InputSurface
import com.scorpk.assistant.ui.theme.OledBlack
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary
import com.scorpk.assistant.util.ScorpkLinks
import com.scorpk.assistant.util.openUrl
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Contenido de la cuenta con sesión iniciada: encabezado, perfil, tareas y conectores. */
@Composable
fun ProfileContent(
    state: AuthState.SignedIn,
    viewModel: AccountViewModel,
    busy: Boolean,
    onOpenConnectors: () -> Unit
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val subscription by viewModel.subscription.collectAsStateWithLifecycle()
    val planLoaded by viewModel.planLoaded.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProfileHeader(state, profile, subscription)
        if (planLoaded && subscription?.isPro != true) UpgradeCard()
        ProfileEditor(profile = profile, busy = busy, onSave = viewModel::saveProfile)
        TasksCard(
            tasks = tasks,
            busy = busy,
            onAdd = viewModel::addTask,
            onToggle = viewModel::toggleTask,
            onDelete = viewModel::deleteTask
        )
        ConnectorsLinkCard(onClick = onOpenConnectors)
    }
}

@Composable
private fun ProfileHeader(state: AuthState.SignedIn, profile: Profile?, subscription: Subscription?) {
    val displayName = profile?.fullName?.takeIf { it.isNotBlank() }
        ?: state.email?.substringBefore('@')
        ?: "Usuario"
    AccountCard {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialAvatar(name = displayName, size = 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                state.email?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanBadge(isPro = subscription?.isPro == true)
                    AuthProvider.from(state.provider)?.let { provider -> ProviderBadge(provider) }
                }
                if (subscription?.isPro == true) {
                    renewalLabel(subscription.currentPeriodEnd)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

/** Invitación a Pro para quien tiene el plan gratuito: explica qué se desbloquea y abre la página de precios. */
@Composable
private fun UpgradeCard() {
    val context = LocalContext.current
    AccountCard(title = "Desbloquea la IA con Pro", icon = Icons.Outlined.AutoAwesome) {
        Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Tu plan gratuito incluye los comandos por voz y texto, «Oye Scorpk» y los conectores. Con Pro sumas:",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            listOf(
                "Lenguaje natural: pídele cualquier cosa, sin frases exactas",
                "Chat con varios modelos de IA",
                "Visión: analiza tu pantalla, fotos y archivos"
            ).forEach { benefit ->
                Text("• $benefit", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
            FilledTonalButton(
                onClick = { openUrl(context, ScorpkLinks.PRICING) },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentBlueContainer, contentColor = AccentBlue),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 6.dp)
            ) { Text("Ver plan Pro") }
        }
    }
}

/** "Renueva el 5 de octubre de 2026", o null si la fecha no viene o no se puede leer. */
private fun renewalLabel(isoDate: String?): String? = try {
    val date = LocalDate.parse(isoDate?.take(10) ?: return null)
    "Renueva el " + date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")))
} catch (e: DateTimeParseException) {
    null
}

@Composable
private fun ProfileEditor(profile: Profile?, busy: Boolean, onSave: (String, String) -> Unit) {
    var fullName by remember(profile?.fullName) { mutableStateOf(profile?.fullName.orEmpty()) }
    var wakeWord by remember(profile?.customWakeWord) { mutableStateOf(profile?.customWakeWord.orEmpty()) }
    val changed = fullName != profile?.fullName.orEmpty() || wakeWord != profile?.customWakeWord.orEmpty()

    AccountCard(title = "Perfil", icon = Icons.Outlined.Badge) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Nombre completo") },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = accountFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = wakeWord,
                onValueChange = { wakeWord = it },
                label = { Text("Palabra de activación") },
                leadingIcon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
                placeholder = { Text("Ej. Scorpk", color = TextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = accountFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            FilledTonalButton(
                onClick = { onSave(fullName, wakeWord) },
                enabled = changed && !busy,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = AccentBlueContainer,
                    contentColor = AccentBlue
                ),
                modifier = Modifier.align(Alignment.End)
            ) { Text("Guardar cambios") }
        }
    }
}

@Composable
private fun TasksCard(
    tasks: List<TaskItem>,
    busy: Boolean,
    onAdd: (String) -> Unit,
    onToggle: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit
) {
    var newTitle by rememberSaveable { mutableStateOf("") }
    val pending = tasks.count { !it.isCompleted }

    fun add() {
        if (newTitle.isBlank()) return
        onAdd(newTitle)
        newTitle = ""
    }

    AccountCard(
        title = "Mis tareas",
        icon = Icons.Outlined.Checklist,
        trailing = {
            if (tasks.isNotEmpty()) CountChip(if (pending == 1) "1 pendiente" else "$pending pendientes")
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                placeholder = { Text("Agregar una tarea…", color = TextSecondary) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                shape = RoundedCornerShape(18.dp),
                colors = accountFieldColors(),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = ::add,
                enabled = newTitle.isNotBlank() && !busy,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = AccentBlue, contentColor = OledBlack),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar tarea")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (tasks.isEmpty()) {
            EmptyHint("No tienes tareas. ¡Agrega la primera!")
        } else {
            tasks.forEachIndexed { index, task ->
                if (index > 0) HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 64.dp, end = 20.dp))
                TaskRow(task = task, onToggle = { onToggle(task) }, onDelete = { onDelete(task) })
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = AccentBlue,
                checkmarkColor = OledBlack,
                uncheckedColor = TextSecondary
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.isCompleted) TextSecondary else TextPrimary,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                task.dueDate?.take(16)?.replace('T', ' '),
                task.priority?.let { "Prioridad $it" }
            ).joinToString(" · ")
            if (meta.isNotBlank() || task.isReminder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.isReminder) {
                        Icon(
                            Icons.Outlined.NotificationsActive,
                            contentDescription = "Recordatorio",
                            tint = AccentBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(meta.ifBlank { "Recordatorio" }, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Eliminar tarea", tint = TextSecondary)
        }
    }
}

/** Acceso al directorio de conectores desde la cuenta. */
@Composable
private fun ConnectorsLinkCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, DividerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Hub, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Conectores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    "Spotify, Calendar, Drive, Gmail, WhatsApp y más",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

/** Estadísticas del historial local y acción para borrarlo. */
@Composable
fun LocalHistoryCard(viewModel: AccountViewModel, onHistoryCleared: () -> Unit) {
    val conversationCount by viewModel.conversationCount.collectAsStateWithLifecycle()
    val commandCount by viewModel.commandCount.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }

    AccountCard(title = "En este dispositivo", icon = Icons.Outlined.PhoneAndroid) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Conversaciones", conversationCount, Modifier.weight(1f))
                StatTile("Comandos", commandCount, Modifier.weight(1f))
            }
            TextButton(
                onClick = { showConfirm = true },
                enabled = conversationCount > 0,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp)
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Borrar historial", color = ErrorRed)
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            containerColor = ElevatedSurface,
            title = { Text("¿Borrar todo el historial?", color = TextPrimary) },
            text = {
                Text(
                    "Se eliminarán todas las conversaciones guardadas en este dispositivo. Esta acción no se puede deshacer.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.clearLocalHistory()
                    onHistoryCleared()
                }) { Text("Borrar", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar", color = TextPrimary) }
            }
        )
    }
}

/** Botón de cierre de sesión, al final de la pantalla. */
@Composable
fun SignOutButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Cerrar sesión", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(InputSurface)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    }
}

@Composable
private fun CountChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = AccentBlue,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(AccentBlueContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}
