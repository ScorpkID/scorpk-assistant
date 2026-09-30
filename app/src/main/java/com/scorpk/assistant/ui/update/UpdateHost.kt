package com.scorpk.assistant.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scorpk.assistant.appContainer
import com.scorpk.assistant.update.UpdateInfo
import com.scorpk.assistant.update.UpdateState
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.ElevatedSurface
import com.scorpk.assistant.ui.theme.ErrorRed
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary

/**
 * Diálogo de actualización, montado una sola vez sobre toda la app. Si la versión instalada ya no está
 * soportada ([UpdateState] con mandatory = true) no se puede cerrar: hay que actualizar para seguir.
 */
@Composable
fun UpdateHost() {
    val context = LocalContext.current
    val manager = context.appContainer.updateManager
    val state by manager.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { manager.onAppResumed() }

    when (val current = state) {
        UpdateState.Idle -> Unit

        is UpdateState.Available -> UpdateDialog(
            title = "Nueva versión disponible",
            info = current.info,
            mandatory = current.mandatory,
            confirmLabel = "Actualizar ahora",
            onConfirm = manager::startUpdate,
            dismissLabel = if (current.mandatory) null else "Más tarde",
            onDismiss = manager::postpone
        ) {
            if (current.mandatory) {
                Text(
                    "Para seguir usando Scorpk necesitas actualizar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
        }

        is UpdateState.Downloading -> UpdateDialog(
            title = "Descargando actualización",
            info = current.info,
            mandatory = current.mandatory,
            confirmLabel = null,
            onConfirm = {},
            dismissLabel = null,
            onDismiss = {}
        ) {
            LinearProgressIndicator(
                progress = { current.progress },
                modifier = Modifier.fillMaxWidth(),
                color = AccentBlue,
                trackColor = DividerColor
            )
            Text(
                "${(current.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }

        is UpdateState.NeedsInstallPermission -> UpdateDialog(
            title = "Permiso para instalar",
            info = current.info,
            mandatory = current.mandatory,
            confirmLabel = "Abrir ajustes",
            onConfirm = { context.startActivity(manager.installPermissionIntent()) },
            dismissLabel = if (current.mandatory) null else "Ahora no",
            onDismiss = manager::postpone
        ) {
            Text(
                "Android necesita que permitas a Scorpk instalar aplicaciones. Actívalo en la pantalla que se abre y vuelve aquí: la actualización continúa sola.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }

        is UpdateState.Installing -> UpdateDialog(
            title = "Instalando",
            info = current.info,
            mandatory = current.mandatory,
            confirmLabel = null,
            onConfirm = {},
            dismissLabel = null,
            onDismiss = {}
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = AccentBlue)
            Text(
                "Confirma la instalación en el aviso de Android. Scorpk se reiniciará al terminar.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }

        is UpdateState.Failed -> UpdateDialog(
            title = "No se pudo actualizar",
            info = current.info,
            mandatory = current.mandatory,
            confirmLabel = "Reintentar",
            onConfirm = manager::startUpdate,
            dismissLabel = if (current.mandatory) null else "Cerrar",
            onDismiss = manager::postpone
        ) {
            Text(current.message, style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
        }
    }
}

@Composable
private fun UpdateDialog(
    title: String,
    info: UpdateInfo,
    mandatory: Boolean,
    confirmLabel: String?,
    onConfirm: () -> Unit,
    dismissLabel: String?,
    onDismiss: () -> Unit,
    extra: @Composable () -> Unit
) {
    AlertDialog(
        // Una actualización obligatoria no se puede descartar tocando fuera del diálogo.
        onDismissRequest = { if (!mandatory) onDismiss() },
        containerColor = ElevatedSurface,
        title = { Text(title, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Scorpk ${info.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = AccentBlue
                )
                info.notes.take(MAX_NOTES).forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Spacer(Modifier.height(2.dp))
                extra()
            }
        },
        confirmButton = {
            if (confirmLabel != null) {
                TextButton(onClick = onConfirm) { Text(confirmLabel, color = AccentBlue) }
            }
        },
        dismissButton = {
            if (dismissLabel != null) {
                TextButton(onClick = onDismiss) { Text(dismissLabel, color = TextSecondary) }
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

private const val MAX_NOTES = 5
