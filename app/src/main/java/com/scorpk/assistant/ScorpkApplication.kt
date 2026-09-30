package com.scorpk.assistant

import android.app.Application
import android.content.Context
import android.util.Log
import com.scorpk.assistant.actions.AccessibilityAutomator
import com.scorpk.assistant.actions.ActionDispatcher
import com.scorpk.assistant.actions.ActionParser
import com.scorpk.assistant.actions.SystemActions
import com.scorpk.assistant.connectors.AccountConnectors
import com.scorpk.assistant.connectors.CalendarManager
import com.scorpk.assistant.connectors.ConnectorTokenStore
import com.scorpk.assistant.connectors.DeviceConnectors
import com.scorpk.assistant.connectors.api.ApiClient
import com.scorpk.assistant.connectors.api.GitHubService
import com.scorpk.assistant.connectors.api.GoogleServices
import com.scorpk.assistant.connectors.ConnectorManager
import com.scorpk.assistant.connectors.SpotifyController
import com.scorpk.assistant.data.local.ScorpkDatabase
import com.scorpk.assistant.data.local.SettingsDataStore
import com.scorpk.assistant.data.remote.FireworksCommandInterpreter
import com.scorpk.assistant.data.remote.supabase.SupabaseProvider
import com.scorpk.assistant.data.repository.AuthRepository
import com.scorpk.assistant.data.repository.ChatRepository
import com.scorpk.assistant.data.repository.ConnectorRepository
import com.scorpk.assistant.data.repository.SubscriptionRepository
import com.scorpk.assistant.data.repository.TaskRepository
import com.scorpk.assistant.domain.CommandProcessor
import com.scorpk.assistant.domain.SpeechOutput
import com.scorpk.assistant.domain.interpreter.CommandInterpreter
import com.scorpk.assistant.domain.interpreter.LocalCommandInterpreter
import com.scorpk.assistant.domain.interpreter.PromptMode
import com.scorpk.assistant.domain.model.AiModel
import com.scorpk.assistant.update.UpdateManager
import com.scorpk.assistant.util.AttachmentReader
import com.scorpk.assistant.voice.WakeWordModelManager
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ScorpkApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Escucha los tokens OAuth de los conectores de cuenta (Drive, Gmail, GitHub).
        container.appScope.launch(Dispatchers.Main) { container.connectorManager.start(this) }
    }
}

/** Contenedor manual de dependencias (singletons de alcance de aplicación). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Scope de aplicación para trabajos que deben sobrevivir a una pantalla (p. ej. descargas). */
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, error -> Log.e("ScorpkAssistant", "Fallo en una tarea de fondo", error) }
    )

    val database: ScorpkDatabase by lazy { ScorpkDatabase.build(appContext) }
    val chatRepository: ChatRepository by lazy { ChatRepository(database.commandDao()) }
    val settings: SettingsDataStore by lazy { SettingsDataStore(appContext) }
    val speechOutput: SpeechOutput by lazy { SpeechOutput(appContext) }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Cliente con timeout total acotado para el LLM de voz (RNF-02: respuesta rápida). */
    private val llmHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
    }

    /** Los modelos grandes del chat pueden tardar más en respuestas largas. */
    private val chatHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .build()
    }

    val wakeWordModelManager: WakeWordModelManager by lazy { WakeWordModelManager(appContext, httpClient) }

    val actionParser: ActionParser by lazy { ActionParser() }
    val localInterpreter: CommandInterpreter by lazy { LocalCommandInterpreter(actionParser) }

    /** Asistente de voz / wake-word / overlay: modelo fijo y rápido, no configurable. */
    val voiceInterpreter: CommandInterpreter by lazy {
        FireworksCommandInterpreter(
            client = llmHttpClient,
            mode = PromptMode.VOICE,
            modelProvider = { AiModel.VOICE },
            sessionToken = { authRepository.accessToken() }
        )
    }

    /** Chat en pantalla: usa el modelo elegido en el selector de la barra superior. */
    val chatInterpreter: CommandInterpreter by lazy {
        FireworksCommandInterpreter(
            client = chatHttpClient,
            mode = PromptMode.CHAT,
            modelProvider = { settings.chatModel() },
            sessionToken = { authRepository.accessToken() }
        )
    }
    val systemActions: SystemActions by lazy { SystemActions(appContext) }
    val automator: AccessibilityAutomator by lazy { AccessibilityAutomator() }
    val spotifyController: SpotifyController by lazy { SpotifyController(appContext) }
    val calendarManager: CalendarManager by lazy { CalendarManager(appContext) }
    val deviceConnectors: DeviceConnectors by lazy { DeviceConnectors(appContext, automator) }
    private val apiClient: ApiClient by lazy { ApiClient(httpClient) }
    val connectorTokens: ConnectorTokenStore by lazy { ConnectorTokenStore(appContext) }
    val accountConnectors: AccountConnectors by lazy {
        AccountConnectors(connectorTokens, GoogleServices(apiClient), GitHubService(apiClient))
    }
    val dispatcher: ActionDispatcher by lazy {
        ActionDispatcher(
            systemActions, automator, spotifyController, calendarManager,
            deviceConnectors, accountConnectors, connectorManager
        )
    }

    val attachmentReader: AttachmentReader by lazy { AttachmentReader(appContext) }

    /** Cliente de Supabase leído de BuildConfig (.env); null si faltan variables. */
    val supabase: SupabaseClient? by lazy { SupabaseProvider.create(appContext) }
    val authRepository: AuthRepository by lazy { AuthRepository(supabase) }
    val taskRepository: TaskRepository by lazy { TaskRepository(supabase) }
    val connectorRepository: ConnectorRepository by lazy { ConnectorRepository(supabase) }
    val subscriptionRepository: SubscriptionRepository by lazy { SubscriptionRepository(supabase) }
    val updateManager: UpdateManager by lazy { UpdateManager(appContext, httpClient, appScope, settings) }
    val connectorManager: ConnectorManager by lazy {
        ConnectorManager(
            appContext, settings, connectorRepository, authRepository,
            connectorTokens, spotifyController, calendarManager
        )
    }

    val commandProcessor: CommandProcessor by lazy {
        CommandProcessor(
            repository = chatRepository,
            voiceInterpreter = voiceInterpreter,
            chatInterpreter = chatInterpreter,
            fallbackInterpreter = localInterpreter,
            parser = actionParser,
            dispatcher = dispatcher,
            speech = speechOutput,
            settings = settings
        )
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ScorpkApplication).container
