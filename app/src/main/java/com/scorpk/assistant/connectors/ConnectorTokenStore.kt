package com.scorpk.assistant.connectors

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scorpk.assistant.util.SecureCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.tokenStore: DataStore<Preferences> by preferencesDataStore(name = "scorpk_connector_tokens")

@Serializable
data class StoredToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val savedAt: Long,
    val expired: Boolean = false
) {
    /** Los tokens de Google duran ~1 h y Supabase no los renueva: se consideran vencidos antes. */
    fun isUsable(account: AccountProvider, now: Long = System.currentTimeMillis()): Boolean {
        if (expired) return false
        return account != AccountProvider.GOOGLE || now - savedAt < GOOGLE_TOKEN_TTL_MS
    }
}

/**
 * Constante a nivel de archivo a propósito: una clase @Serializable con `private companion object`
 * genera un acceso a `Companion` que falla en tiempo de ejecución (IllegalAccessError).
 */
private const val GOOGLE_TOKEN_TTL_MS = 55 * 60 * 1000L

/** Tokens OAuth de las cuentas conectadas, cifrados con AES-GCM (Android Keystore). */
class ConnectorTokenStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val tokens: Flow<Map<AccountProvider, StoredToken>> = context.tokenStore.data.map { prefs ->
        AccountProvider.entries.mapNotNull { account ->
            prefs[key(account)]?.let(::decode)?.let { account to it }
        }.toMap()
    }

    suspend fun get(account: AccountProvider): StoredToken? = tokens.first()[account]

    suspend fun save(account: AccountProvider, accessToken: String, refreshToken: String?) =
        withContext(Dispatchers.IO) {
            val token = StoredToken(accessToken, refreshToken, System.currentTimeMillis())
            val encrypted = SecureCipher.encrypt(json.encodeToString(token))
            context.tokenStore.edit { it[key(account)] = encrypted }
        }

    suspend fun markExpired(account: AccountProvider) = withContext(Dispatchers.IO) {
        val current = get(account) ?: return@withContext
        val encrypted = SecureCipher.encrypt(json.encodeToString(current.copy(expired = true)))
        context.tokenStore.edit { it[key(account)] = encrypted }
    }

    suspend fun clear(account: AccountProvider) = withContext(Dispatchers.IO) {
        context.tokenStore.edit { it.remove(key(account)) }
        Unit
    }

    private fun decode(encrypted: String): StoredToken? {
        val plain = SecureCipher.decrypt(encrypted) ?: return null
        return try {
            json.decodeFromString<StoredToken>(plain)
        } catch (e: Exception) {
            null
        }
    }

    private fun key(account: AccountProvider) = stringPreferencesKey("token_${account.key}")
}
