package com.scorpk.assistant.ui.connectors

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scorpk.assistant.connectors.ConnectionKind
import com.scorpk.assistant.connectors.ConnectorCategory
import com.scorpk.assistant.connectors.ConnectorState
import com.scorpk.assistant.connectors.ConnectorStatus
import com.scorpk.assistant.connectors.ConnectorType
import com.scorpk.assistant.domain.model.RequiredPermission
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorsScreen(onBack: () -> Unit, viewModel: ConnectorsViewModel = viewModel()) {
    val context = LocalContext.current
    val statuses by viewModel.statuses.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPermissionType by remember { mutableStateOf<ConnectorType?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val type = pendingPermissionType
        pendingPermissionType = null
        if (type != null && results.isNotEmpty() && results.values.all { it }) {
            viewModel.connect(type)
        } else {
            viewModel.refresh()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ConnectorEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is ConnectorEvent.OpenInstall -> viewModel.installIntent(event.type)?.let {
                    runCatching { context.startActivity(it) }
                }
                is ConnectorEvent.RequestPermissions -> {
                    pendingPermissionType = event.type
                    permissionLauncher.launch(event.permissions)
                }
            }
        }
    }

    val normalizedQuery = query.trim().lowercase()
    val visible = statuses.filter { status ->
        val type = status.type
        (category == null || type.category.name == category) &&
            (normalizedQuery.isEmpty() ||
                type.label.lowercase().contains(normalizedQuery) ||
                type.tagline.lowercase().contains(normalizedQuery) ||
                type.category.label.lowercase().contains(normalizedQuery))
    }
    val connected = visible.filter { it.connected }
    val available = visible.filterNot { it.connected }

    Scaffold(
        containerColor = AppBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Conectores") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Conecta tus apps y servicios para que Scorpk actúe por ti con la voz o el chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                SearchField(query = query, onChange = { query = it })
                Spacer(Modifier.height(12.dp))
                CategoryChips(selected = category, onSelect = { category = it })
                Spacer(Modifier.height(10.dp))
            }
            if (connected.isNotEmpty()) {
                item { SectionLabel("Conectados · ${connected.size}") }
                items(connected, key = { it.type.provider }) { status ->
                    ConnectorCard(status, working == status.type) { selectedProvider = status.type.provider }
                }
            }
            if (available.isNotEmpty()) {
                item { SectionLabel(if (connected.isNotEmpty()) "Disponibles" else "Todos los conectores") }
                items(available, key = { it.type.provider }) { status ->
                    ConnectorCard(status, working == status.type) { selectedProvider = status.type.provider }
                }
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        "No encontré conectores para \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
        }
    }

    val selected = statuses.firstOrNull { it.type.provider == selectedProvider }
    if (selected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedProvider = null },
            sheetState = sheetState,
            containerColor = CardSurface,
            contentColor = TextPrimary
        ) {
            ConnectorSheet(
                status = selected,
                working = working == selected.type,
                onConnect = { viewModel.connect(selected.type) },
                onDisconnect = {
                    viewModel.disconnect(selected.type)
                    selectedProvider = null
                },
                onOpen = { viewModel.launchIntent(selected.type)?.let { runCatching { context.startActivity(it) } } },
                onEnableDirectControl = {
                    context.startActivity(PermissionChecker.settingsIntent(context, RequiredPermission.NOTIFICATION_ACCESS))
                }
            )
        }
    }
}

// region Lista

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Buscar conectores", color = TextSecondary) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextSecondary) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Borrar", tint = TextSecondary)
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentBlue,
            unfocusedBorderColor = DividerColor,
            focusedContainerColor = InputSurface,
            unfocusedContainerColor = InputSurface,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = AccentBlue
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CategoryChips(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Chip("Todos", selected == null) { onSelect(null) }
        ConnectorCategory.entries.forEach { category ->
            Chip(category.label, selected == category.name) { onSelect(category.name) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) AccentBlueContainer else CardSurface,
        border = BorderStroke(1.dp, if (selected) AccentBlue.copy(alpha = 0.5f) else DividerColor)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) AccentBlue else TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ConnectorCard(status: ConnectorStatus, working: Boolean, onClick: () -> Unit) {
    val type = status.type
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, DividerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectorLogo(type, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    type.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    type.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            if (working) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AccentBlue)
            } else {
                StatePill(status)
            }
        }
    }
}

@Composable
private fun StatePill(status: ConnectorStatus) {
    val (label, color) = when (status.state) {
        ConnectorState.CONNECTED -> "Conectado" to SuccessGreen
        ConnectorState.AVAILABLE -> "Conectar" to AccentBlue
        ConnectorState.NEEDS_APP -> "Falta la app" to ErrorRed
        ConnectorState.NEEDS_PERMISSION -> "Falta permiso" to ErrorRed
        ConnectorState.EXPIRED -> "Reconectar" to ErrorRed
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status.state == ConnectorState.CONNECTED) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Medium)
    }
}

// endregion

// region Ficha

@Composable
private fun ConnectorSheet(
    status: ConnectorStatus,
    working: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpen: () -> Unit,
    onEnableDirectControl: () -> Unit
) {
    val type = status.type
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConnectorLogo(type, size = 60.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    type.category.label + if (type.kind == ConnectionKind.ACCOUNT) " · Con tu cuenta" else " · En tu teléfono",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            StatePill(status)
        }

        Spacer(Modifier.height(18.dp))
        Text(type.description, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)

        SheetSection("Qué puede hacer")
        type.capabilities.forEach { capability ->
            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier
                    .padding(top = 3.dp)
                    .size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(capability, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }

        SheetSection("Prueba diciendo")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            type.examples.forEach { example ->
                Text(
                    "“$example”",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(InputSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        val accessRows = accessRows(status)
        if (accessRows.isNotEmpty()) {
            SheetSection("Permisos y acceso")
            accessRows.forEach { (label, ok) ->
                Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (ok) SuccessGreen else TextSecondary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                    Text(if (ok) "Listo" else "Pendiente", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                }
            }
        }
        if (type == ConnectorType.SPOTIFY && status.connected && !status.directControl) {
            TextButton(onClick = onEnableDirectControl) {
                Text("Activar control directo (acceso a notificaciones)", color = AccentBlue)
            }
        }

        Spacer(Modifier.height(24.dp))
        PrimaryAction(status, working, onConnect, onDisconnect)
        if (type.packageName != null && status.appInstalled && status.connected) {
            TextButton(onClick = onOpen, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Abrir ${type.label}", color = TextSecondary)
            }
        }
    }
}

private fun accessRows(status: ConnectorStatus): List<Pair<String, Boolean>> = buildList {
    val type = status.type
    type.packageName?.let { add("App de ${type.label} instalada" to status.appInstalled) }
    type.permissions.forEach { permission ->
        add("Permiso: ${PermissionChecker.displayName(permission)}" to (permission !in status.missingPermissions))
    }
    type.account?.let { add("Autorización de tu cuenta de ${it.label}" to (status.connected && status.tokenValid)) }
}

@Composable
private fun SheetSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)
    )
}

@Composable
private fun ColumnScope.PrimaryAction(
    status: ConnectorStatus,
    working: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val type = status.type
    val state = status.state
    val connectedAndHealthy = state == ConnectorState.CONNECTED

    if (connectedAndHealthy) {
        OutlinedButton(
            onClick = onDisconnect,
            enabled = !working,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) { Text("Desconectar", style = MaterialTheme.typography.titleMedium) }
        return
    }

    val label = when (state) {
        ConnectorState.NEEDS_APP -> "Instalar ${type.label}"
        ConnectorState.NEEDS_PERMISSION -> "Conceder permisos"
        ConnectorState.EXPIRED -> "Reconectar con ${type.account?.label ?: type.label}"
        else -> if (type.kind == ConnectionKind.ACCOUNT) "Conectar con ${type.account?.label}" else "Conectar ${type.label}"
    }
    Button(
        onClick = onConnect,
        enabled = !working,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TextPrimary,
            contentColor = OledBlack,
            disabledContainerColor = InputSurface,
            disabledContentColor = TextSecondary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (working) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = OledBlack)
        } else {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
    if (state == ConnectorState.EXPIRED) {
        TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Quitar conector", color = TextSecondary)
        }
    }
}

// endregion
