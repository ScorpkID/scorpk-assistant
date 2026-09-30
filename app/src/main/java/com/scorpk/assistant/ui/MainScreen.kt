package com.scorpk.assistant.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scorpk.assistant.domain.model.CommandSource
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.ui.account.AccountScreen
import com.scorpk.assistant.ui.chat.ChatEvent
import com.scorpk.assistant.ui.chat.ChatScreen
import com.scorpk.assistant.ui.chat.ChatViewModel
import com.scorpk.assistant.ui.chat.InputBar
import com.scorpk.assistant.ui.chat.ListeningUi
import com.scorpk.assistant.ui.chat.ModelSelector
import com.scorpk.assistant.ui.connectors.ConnectorsScreen
import com.scorpk.assistant.ui.drawer.DrawerContent
import com.scorpk.assistant.ui.update.UpdateHost
import com.scorpk.assistant.ui.settings.SettingsScreen
import com.scorpk.assistant.ui.theme.AppBackground
import com.scorpk.assistant.ui.theme.TextPrimary
import com.scorpk.assistant.util.PermissionChecker
import com.scorpk.assistant.voice.VoiceCapture
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object ChatDestination

@Serializable
data object SettingsDestination

@Serializable
data object AccountDestination

@Serializable
data object ConnectorsDestination

@Composable
fun MainScreen(viewModel: ChatViewModel = viewModel()) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val onChat = backStackEntry?.destination?.hasRoute<ChatDestination>() ?: true

    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val currentConversationId by viewModel.currentConversationId.collectAsStateWithLifecycle()

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = onChat,
        drawerContent = {
            DrawerContent(
                conversations = conversations,
                selectedConversationId = currentConversationId,
                onNewConversation = { closeDrawerThen(viewModel::newConversation) },
                onConversationClick = { id -> closeDrawerThen { viewModel.selectConversation(id) } },
                onDeleteConversation = viewModel::deleteConversation,
                onConnectorsClick = { closeDrawerThen { navController.navigate(ConnectorsDestination) } },
                onSettingsClick = { closeDrawerThen { navController.navigate(SettingsDestination) } },
                onAccountClick = { closeDrawerThen { navController.navigate(AccountDestination) } }
            )
        }
    ) {
        NavHost(navController = navController, startDestination = ChatDestination) {
            composable<ChatDestination> {
                ChatRoute(
                    viewModel = viewModel,
                    onMenuClick = { scope.launch { drawerState.open() } }
                )
            }
            composable<SettingsDestination> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenConnectors = { navController.navigate(ConnectorsDestination) }
                )
            }
            composable<AccountDestination> {
                AccountScreen(
                    onBack = { navController.popBackStack() },
                    onHistoryCleared = viewModel::newConversation,
                    onOpenConnectors = { navController.navigate(ConnectorsDestination) }
                )
            }
            composable<ConnectorsDestination> {
                ConnectorsScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    UpdateHost()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoute(viewModel: ChatViewModel, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val chatModel by viewModel.chatModel.collectAsStateWithLifecycle()
    val attachment by viewModel.attachment.collectAsStateWithLifecycle()
    val loadingAttachment by viewModel.loadingAttachment.collectAsStateWithLifecycle()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::attach)
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::attach)
    }

    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val listening = (voiceState as? VoiceCapture.State.Listening)?.let { ListeningUi(it.partial, it.level) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleVoice()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Se necesita el micrófono para hablar con Scorpk.") }
        }
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.isNotEmpty() && results.values.all { it }
        scope.launch {
            snackbarHostState.showSnackbar(
                if (granted) "Permiso concedido. Repite la orden." else "Permiso denegado."
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.PermissionNeeded -> {
                    val runtime = PermissionChecker.runtimePermissionsFor(event.permission)
                    if (runtime != null) {
                        runtimePermissionLauncher.launch(runtime)
                    } else {
                        val result = snackbarHostState.showSnackbar(
                            message = event.message,
                            actionLabel = "Abrir ajustes",
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            context.startActivity(PermissionChecker.settingsIntent(context, event.permission))
                        }
                    }
                }
                is ChatEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    fun onMicClick() {
        if (listening != null || PermissionChecker.isGranted(context, RequiredPermission.MICROPHONE)) {
            viewModel.toggleVoice()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        containerColor = AppBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    ModelSelector(selected = chatModel, onSelect = viewModel::selectChatModel)
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Abrir menú")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::newConversation) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Nueva consulta")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            InputBar(
                value = input,
                onValueChange = viewModel::onInputChange,
                onSend = viewModel::submitInput,
                onMicClick = ::onMicClick,
                onCancelListening = viewModel::cancelVoice,
                listening = listening,
                attachment = attachment,
                loadingAttachment = loadingAttachment,
                onPickImage = {
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onPickDocument = { documentPicker.launch("*/*") },
                onRemoveAttachment = viewModel::clearAttachment,
                enabled = !isProcessing,
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
            )
        }
    ) { padding ->
        ChatScreen(
            messages = messages,
            isProcessing = isProcessing,
            onSuggestionClick = { viewModel.send(it, CommandSource.TEXT) },
            modifier = Modifier.padding(padding)
        )
    }
}
