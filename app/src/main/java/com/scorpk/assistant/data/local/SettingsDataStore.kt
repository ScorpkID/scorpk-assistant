package com.scorpk.assistant.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scorpk.assistant.domain.model.AiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "scorpk_settings")

/** Variantes del modo oscuro permitidas por la guía de estilo (fondo #000000 o #121212). */
enum class ThemeMode(val label: String) {
    OLED("Negro puro (OLED)"),
    DIM("Oscuro")
}

/** Preferencias de usuario. Los secretos NO viven aquí: se inyectan desde .env vía BuildConfig. */
data class AssistantSettings(
    val voiceFeedbackEnabled: Boolean = true,
    val wakeWordEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.OLED,
    val chatModel: AiModel = AiModel.CHAT_DEFAULT,
    /** Proveedores de conectores marcados como conectados en este dispositivo. */
    val connectedProviders: Set<String> = emptySet(),
    /** El asistente flotante puede adjuntar una captura de la pantalla a la consulta. */
    val screenVisionEnabled: Boolean = false,
    /** El asistente flotante puede tomar una foto con la cámara para analizarla. */
    val cameraVisionEnabled: Boolean = false
)

class SettingsDataStore(private val context: Context) {

    val settings: Flow<AssistantSettings> = context.settingsStore.data.map { prefs ->
        AssistantSettings(
            voiceFeedbackEnabled = prefs[KEY_VOICE_FEEDBACK] ?: true,
            wakeWordEnabled = prefs[KEY_WAKE_WORD] ?: false,
            themeMode = prefs[KEY_THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.OLED,
            chatModel = AiModel.fromId(prefs[KEY_CHAT_MODEL]),
            connectedProviders = prefs[KEY_CONNECTORS].orEmpty(),
            screenVisionEnabled = prefs[KEY_SCREEN_VISION] ?: false,
            cameraVisionEnabled = prefs[KEY_CAMERA_VISION] ?: false
        )
    }

    suspend fun setVoiceFeedbackEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_VOICE_FEEDBACK] = enabled }
    }

    suspend fun setWakeWordEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_WAKE_WORD] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun chatModel(): AiModel = settings.first().chatModel

    suspend fun setChatModel(model: AiModel) {
        context.settingsStore.edit { it[KEY_CHAT_MODEL] = model.id }
    }

    suspend fun setScreenVisionEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_SCREEN_VISION] = enabled }
    }

    suspend fun setCameraVisionEnabled(enabled: Boolean) {
        context.settingsStore.edit { it[KEY_CAMERA_VISION] = enabled }
    }

    /** Conector cuyo consentimiento OAuth está en curso (se completa al volver por el deep link). */
    suspend fun pendingOAuth(): String? = context.settingsStore.data.first()[KEY_PENDING_OAUTH]

    suspend fun setPendingOAuth(provider: String?) {
        context.settingsStore.edit {
            if (provider == null) {
                it.remove(KEY_PENDING_OAUTH)
            } else {
                it[KEY_PENDING_OAUTH] = provider
            }
        }
    }

    /** Versión mínima exigida por una actualización obligatoria pendiente (0 = ninguna). */
    suspend fun requiredUpdateCode(): Int = context.settingsStore.data.first()[KEY_REQUIRED_UPDATE] ?: 0

    suspend fun setRequiredUpdateCode(code: Int) {
        context.settingsStore.edit {
            if (code <= 0) {
                it.remove(KEY_REQUIRED_UPDATE)
            } else {
                it[KEY_REQUIRED_UPDATE] = code
            }
        }
    }

    suspend fun setConnectedProviders(providers: Set<String>) {
        context.settingsStore.edit { it[KEY_CONNECTORS] = providers }
    }

    private companion object {
        val KEY_VOICE_FEEDBACK = booleanPreferencesKey("voice_feedback_enabled")
        val KEY_WAKE_WORD = booleanPreferencesKey("wake_word_enabled")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_CHAT_MODEL = stringPreferencesKey("chat_model")
        val KEY_CONNECTORS = stringSetPreferencesKey("connected_providers")
        val KEY_REQUIRED_UPDATE = intPreferencesKey("required_update_code")
        val KEY_PENDING_OAUTH = stringPreferencesKey("pending_oauth")
        val KEY_SCREEN_VISION = booleanPreferencesKey("screen_vision_enabled")
        val KEY_CAMERA_VISION = booleanPreferencesKey("camera_vision_enabled")
    }
}
