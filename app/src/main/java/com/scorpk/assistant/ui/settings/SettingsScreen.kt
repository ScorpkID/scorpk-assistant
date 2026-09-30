package com.scorpk.assistant.ui.settings

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scorpk.assistant.appContainer
import com.scorpk.assistant.data.local.AssistantSettings
import com.scorpk.assistant.data.local.ThemeMode
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.service.WakeWordForegroundService
import com.scorpk.assistant.ui.components.ScorpkLogo
import com.scorpk.assistant.update.CheckResult
import com.scorpk.assistant.ui.theme.AccentBlue
import com.scorpk.assistant.ui.theme.AccentBlueContainer
import com.scorpk.assistant.ui.theme.AppBackground
import com.scorpk.assistant.ui.theme.CardSurface
import com.scorpk.assistant.ui.theme.DividerColor
import com.scorpk.assistant.ui.theme.ErrorRed
import com.scorpk.assistant.ui.theme.InputSurface
import com.scorpk.assistant.ui.theme.OledBlack
import com.scorpk.assistant.ui.theme.SuccessGreen
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.ui.theme.TextSecondary
import com.scorpk.assistant.util.PermissionChecker
import com.scorpk.assistant.voice.WakeWordModelManager.ModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PERMISSION_ICONS: List<Pair<RequiredPermission, ImageVector>> = listOf(
    RequiredPermission.MICROPHONE to Icons.Outlined.Mic,
    RequiredPermission.CAMERA to Icons.Outlined.PhotoCamera,
    RequiredPermission.ACCESSIBILITY to Icons.Outlined.Accessibility,
    RequiredPermission.OVERLAY to Icons.Outlined.Layers,
    RequiredPermission.NOTIFICATIONS to Icons.Outlined.Notifications,
    RequiredPermission.CALENDAR to Icons.Outlined.CalendarMonth,
    RequiredPermission.CONTACTS to Icons.Outlined.Contacts,
    RequiredPermission.NOTIFICATION_ACCESS to Icons.Outlined.NotificationsActive
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenConnectors: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var checkingUpdate by remember { mutableStateOf(false) }

    val settings by container.settings.settings.collectAsStateWithLifecycle(AssistantSettings())
    val wakeWordRunning by WakeWordForegroundService.isRunning.collectAsStateWithLifecycle()
    val modelState by container.wakeWordModelManager.state.collectAsStateWithLifecycle()

    // Se incrementa al volver de Ajustes del sistema para refrescar el estado de los permisos.
    var refreshTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refreshTick++ }
    val permissionStates = remember(refreshTick) {
        RequiredPermission.entries.associateWith { PermissionChecker.isGranted(context, it) }
    }

    fun message(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    fun messageWithAction(text: String, action: String, onAction: () -> Unit) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(text, actionLabel = action, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshTick++
        val granted = results.isNotEmpty() && results.values.all { it }
        if (!granted) message("Permiso denegado. Puedes concederlo desde los ajustes de la app.")
    }

    fun requestPermission(permission: RequiredPermission) {
        val runtime = PermissionChecker.runtimePermissionsFor(permission)
        if (runtime != null) {
            permissionLauncher.launch(runtime)
        } else {
            context.startActivity(PermissionChecker.settingsIntent(context, permission))
        }
    }

    // region Escucha "Oye Scorpk"

    /**
     * Activa la escucha en segundo plano (con el micrófono ya concedido) validando la
     * superposición y el modelo on-device, que se descarga si hace falta en el scope de la app.
     */
    fun enableWakeWord() {
        if (!PermissionChecker.isGranted(context, RequiredPermission.OVERLAY)) {
            messageWithAction(
                "Scorpk necesita mostrarse sobre otras apps para el asistente flotante.",
                "Conceder"
            ) { context.startActivity(PermissionChecker.settingsIntent(context, RequiredPermission.OVERLAY)) }
            return
        }
        val appContext = context.applicationContext
        container.appScope.launch {
            container.settings.setWakeWordEnabled(true)
            val ready = container.wakeWordModelManager.download()
            if (!ready) {
                container.settings.setWakeWordEnabled(false)
                return@launch
            }
            val started = withContext(Dispatchers.Main) { WakeWordForegroundService.start(appContext) }
            if (!started) container.settings.setWakeWordEnabled(false)
        }
    }

    val micForWakeWordLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshTick++
        if (granted) enableWakeWord() else message("Se necesita el micrófono para escuchar \"Oye Scorpk\".")
    }

    fun setWakeWord(enabled: Boolean) {
        when {
            !enabled -> {
                scope.launch { container.settings.setWakeWordEnabled(false) }
                WakeWordForegroundService.stop(context)
            }
            !PermissionChecker.isGranted(context, RequiredPermission.MICROPHONE) ->
                micForWakeWordLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else -> enableWakeWord()
        }
    }

    // endregion

    // region Visión

    val cameraForVisionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshTick++
        if (granted) {
            scope.launch { container.settings.setCameraVisionEnabled(true) }
        } else {
            message("Sin permiso de cámara no puedo analizar fotos.")
        }
    }

    fun setScreenVision(enabled: Boolean) {
        if (!enabled) {
            scope.launch { container.settings.setScreenVisionEnabled(false) }
        } else if (!PermissionChecker.isGranted(context, RequiredPermission.ACCESSIBILITY)) {
            messageWithAction(
                "Activa \"Scorpk Automatización\" en Accesibilidad para poder ver la pantalla.",
                "Abrir ajustes"
            ) { context.startActivity(PermissionChecker.settingsIntent(context, RequiredPermission.ACCESSIBILITY)) }
        } else {
            scope.launch { container.settings.setScreenVisionEnabled(true) }
        }
    }

    fun setCameraVision(enabled: Boolean) {
        if (!enabled) {
            scope.launch { container.settings.setCameraVisionEnabled(false) }
        } else if (!PermissionChecker.isGranted(context, RequiredPermission.CAMERA)) {
            cameraForVisionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            scope.launch { container.settings.setCameraVisionEnabled(true) }
        }
    }

    // endregion

    Scaffold(
        containerColor = AppBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Group("Asistente") {
                SwitchRow(
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    title = "Respuesta por voz",
                    subtitle = "Scorpk confirma cada acción en voz alta.",
                    checked = settings.voiceFeedbackEnabled,
                    onCheckedChange = { scope.launch { container.settings.setVoiceFeedbackEnabled(it) } }
                )
                Divider()
                SwitchRow(
                    icon = Icons.Outlined.RecordVoiceOver,
                    title = "Activar con \"Oye Scorpk\"",
                    subtitle = "Detección silenciosa en tu teléfono. Abre el asistente flotante sobre cualquier app.",
                    checked = wakeWordRunning || modelState is ModelState.Downloading,
                    onCheckedChange = ::setWakeWord
                )
                if (modelState !is ModelState.NotDownloaded) {
                    Divider()
                    ModelStatusRow(
                        state = modelState,
                        onRetry = { setWakeWord(true) },
                        onDelete = {
                            WakeWordForegroundService.stop(context)
                            scope.launch { container.settings.setWakeWordEnabled(false) }
                            container.wakeWordModelManager.delete()
                        }
                    )
                }
            }

            Group(
                "Visión",
                footer = "El asistente flotante mostrará los botones Pantalla y Cámara para enviar una imagen junto a tu pregunta. Analizar imágenes con IA es parte del plan Pro."
            ) {
                SwitchRow(
                    icon = Icons.Outlined.Smartphone,
                    title = "Ver la pantalla",
                    subtitle = "Envía una captura de lo que tienes abierto. Requiere accesibilidad.",
                    checked = settings.screenVisionEnabled && permissionStates[RequiredPermission.ACCESSIBILITY] == true,
                    onCheckedChange = ::setScreenVision
                )
                Divider()
                SwitchRow(
                    icon = Icons.Outlined.PhotoCamera,
                    title = "Usar la cámara",
                    subtitle = "Toma una foto para que Scorpk la analice.",
                    checked = settings.cameraVisionEnabled && permissionStates[RequiredPermission.CAMERA] == true,
                    onCheckedChange = ::setCameraVision
                )
            }

            Group("Apariencia") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThemeOption(
                        label = "Negro puro",
                        caption = "OLED",
                        swatch = OledBlack,
                        selected = settings.themeMode == ThemeMode.OLED,
                        modifier = Modifier.weight(1f)
                    ) { scope.launch { container.settings.setThemeMode(ThemeMode.OLED) } }
                    ThemeOption(
                        label = "Oscuro",
                        caption = "Atenuado",
                        swatch = Color(0xFF121212),
                        selected = settings.themeMode == ThemeMode.DIM,
                        modifier = Modifier.weight(1f)
                    ) { scope.launch { container.settings.setThemeMode(ThemeMode.DIM) } }
                }
            }

            Group("Servicios") {
                NavigationRow(
                    icon = Icons.Outlined.Hub,
                    title = "Conectores",
                    subtitle = if (settings.connectedProviders.isEmpty()) {
                        "Spotify, Calendar, Drive, Gmail, WhatsApp y más"
                    } else {
                        "${settings.connectedProviders.size} conectados"
                    },
                    onClick = onOpenConnectors
                )
            }

            val updateManager = container.updateManager
            Group("Actualizaciones") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(Icons.Outlined.SystemUpdate)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Versión ${updateManager.installedVersionName}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Text(
                            if (checkingUpdate) "Buscando…" else "Se descargan e instalan desde la app, sin navegador.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    TextButton(
                        enabled = !checkingUpdate,
                        onClick = {
                            checkingUpdate = true
                            scope.launch {
                                val result = updateManager.checkNow()
                                checkingUpdate = false
                                message(
                                    when (result) {
                                        CheckResult.UpToDate -> "Ya tienes la última versión."
                                        is CheckResult.Available -> "Hay una versión nueva: ${result.info.versionName}."
                                        is CheckResult.Failed -> result.message
                                    }
                                )
                            }
                        }
                    ) { Text("Buscar", color = AccentBlue) }
                }
            }

            val granted = permissionStates.count { it.value }
            Group("Permisos", footer = "$granted de ${permissionStates.size} concedidos") {
                PERMISSION_ICONS.forEachIndexed { index, (permission, icon) ->
                    if (index > 0) Divider()
                    PermissionRow(
                        icon = icon,
                        name = PermissionChecker.displayName(permission),
                        granted = permissionStates[permission] == true,
                        onGrant = { requestPermission(permission) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScorpkLogo(size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Scorpk ${appVersion(context)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// region Componentes

@Composable
private fun Group(title: String, footer: String? = null, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, DividerColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column { content() }
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp)
            )
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(color = DividerColor, modifier = Modifier.padding(start = 66.dp))
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color = AccentBlue) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(InputSurface),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OledBlack,
                checkedTrackColor = AccentBlue,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = InputSurface,
                uncheckedBorderColor = DividerColor
            )
        )
    }
}

@Composable
private fun NavigationRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconTile(icon)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, name: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(icon, tint = if (granted) SuccessGreen else TextSecondary)
        Spacer(Modifier.width(14.dp))
        Text(name, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        if (granted) {
            Text(
                "Concedido",
                style = MaterialTheme.typography.labelMedium,
                color = SuccessGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SuccessGreen.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        } else {
            TextButton(onClick = onGrant) { Text("Conceder", color = AccentBlue) }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    caption: String,
    swatch: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AccentBlueContainer else InputSurface,
        border = BorderStroke(1.dp, if (selected) AccentBlue else DividerColor),
        modifier = modifier
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(swatch)
                    .border(1.dp, DividerColor, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(caption, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun ModelStatusRow(state: ModelState, onRetry: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(start = 66.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)) {
        when (state) {
            ModelState.NotDownloaded -> Unit
            is ModelState.Downloading -> {
                Text(
                    "Descargando modelo de voz… ${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentBlue,
                    trackColor = DividerColor
                )
            }
            ModelState.Ready -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Modelo de voz listo (~40 MB, en tu teléfono)",
                    style = MaterialTheme.typography.labelMedium,
                    color = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDelete) { Text("Eliminar", color = ErrorRed) }
            }
            is ModelState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.message, style = MaterialTheme.typography.labelMedium, color = ErrorRed, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("Reintentar", color = AccentBlue) }
            }
        }
    }
}

// endregion

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}
