package com.scorpk.assistant.data.remote.supabase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scorpk.assistant.util.SecureCipher
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "scorpk_session")

/**
 * Persiste la sesión de Supabase (access/refresh token) cifrada con AES-GCM del Android
 * Keystore, en lugar de las SharedPreferences en texto plano que usa el SDK por defecto.
 */
class EncryptedSessionManager(private val context: Context) : SessionManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        val encrypted = SecureCipher.encrypt(json.encodeToString(UserSession.serializer(), session))
        context.sessionStore.edit { it[KEY_SESSION] = encrypted }
        Unit
    }

    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        val encrypted = context.sessionStore.data.first()[KEY_SESSION] ?: return@withContext null
        val plain = SecureCipher.decrypt(encrypted) ?: return@withContext null
        try {
            json.decodeFromString(UserSession.serializer(), plain)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        context.sessionStore.edit { it.remove(KEY_SESSION) }
        Unit
    }

    private companion object {
        val KEY_SESSION = stringPreferencesKey("supabase_session_enc")
    }
}
