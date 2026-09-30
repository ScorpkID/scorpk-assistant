package com.scorpk.assistant.data.repository

import android.content.Intent
import android.net.Uri
import com.scorpk.assistant.connectors.AccountProvider
import com.scorpk.assistant.data.remote.supabase.Profile
import com.scorpk.assistant.data.remote.supabase.SupabaseProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.ExternalAuthConfigDefaults
import io.github.jan.supabase.auth.providers.Github
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Token de acceso del proveedor OAuth (Google/GitHub) entregado por Supabase al iniciar o vincular. */
data class ProviderTokens(val accessToken: String, val refreshToken: String?)

sealed interface AuthState {
    data class NotConfigured(val missing: List<String>) : AuthState
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String?, val provider: String?) : AuthState
}

/** Autenticación con Supabase Auth (email/contraseña, Google y GitHub) y perfil del usuario. */
class AuthRepository(private val client: SupabaseClient?) {

    val authState: Flow<AuthState> = client?.auth?.sessionStatus?.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> {
                val user = status.session.user
                AuthState.SignedIn(
                    userId = user?.id.orEmpty(),
                    email = user?.email,
                    provider = user?.appMetadata?.get("provider")?.toString()?.trim('"')
                )
            }
            SessionStatus.Initializing -> AuthState.Loading
            is SessionStatus.NotAuthenticated, is SessionStatus.RefreshFailure -> AuthState.SignedOut
        }
    } ?: flowOf(AuthState.NotConfigured(SupabaseProvider.missingVariables))

    val currentUserId: String?
        get() = client?.auth?.currentUserOrNull()?.id

    val isConfigured: Boolean get() = client != null

    /** Token de acceso de la sesión actual (Supabase lo renueva solo); null si no hay sesión. */
    fun accessToken(): String? = client?.auth?.currentSessionOrNull()?.accessToken

    /**
     * Cada vez que Supabase entrega un token de proveedor (solo ocurre justo después del
     * login OAuth: los refrescos de sesión no lo incluyen).
     */
    val providerTokens: Flow<ProviderTokens> = client?.auth?.sessionStatus
        ?.mapNotNull { status ->
            val session = (status as? SessionStatus.Authenticated)?.session ?: return@mapNotNull null
            session.providerToken?.takeIf { it.isNotBlank() }?.let { ProviderTokens(it, session.providerRefreshToken) }
        }
        ?.distinctUntilChanged()
        ?: emptyFlow()

    /**
     * Abre el consentimiento OAuth del proveedor pidiendo [scopes] extra (Drive, Gmail, GitHub…).
     * Si ya hay sesión con otra cuenta, se vincula la identidad; si no, se inicia sesión con ella.
     * La sesión y el token regresan por el App Link https://scorpk.tech/auth/callback.
     */
    suspend fun requestProviderAccess(account: AccountProvider, scopes: List<String>): Result<Unit> = call { supabase ->
        val auth = supabase.auth
        val configure: ExternalAuthConfigDefaults.() -> Unit = {
            this.scopes.addAll(scopes)
            if (account == AccountProvider.GOOGLE) {
                queryParams["access_type"] = "offline"
                queryParams["prompt"] = "consent"
                queryParams["include_granted_scopes"] = "true"
            }
        }
        val alreadyLinked = auth.currentIdentitiesOrNull().orEmpty().any { it.provider == account.key }
        val signedIn = auth.currentUserOrNull() != null
        val redirect = SupabaseProvider.REDIRECT_URL
        when (account) {
            AccountProvider.GOOGLE ->
                if (signedIn && !alreadyLinked) auth.linkIdentity(Google, redirect, configure)
                else auth.signInWith(Google, redirect, configure)
            AccountProvider.GITHUB ->
                if (signedIn && !alreadyLinked) auth.linkIdentity(Github, redirect, configure)
                else auth.signInWith(Github, redirect, configure)
        }
        Unit
    }

    suspend fun signInWithEmail(email: String, password: String): Result<Unit> = call { supabase ->
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /**
     * Crea la cuenta. Si el proyecto exige confirmar el correo, no habrá sesión todavía:
     * devuelve true cuando el usuario debe revisar su bandeja de entrada.
     */
    suspend fun signUpWithEmail(email: String, password: String): Result<Boolean> = call { supabase ->
        supabase.auth.signUpWith(Email, redirectUrl = SupabaseProvider.REDIRECT_URL) {
            this.email = email.trim()
            this.password = password
        }
        supabase.auth.currentSessionOrNull() == null
    }

    /** Abre Google OAuth en una Custom Tab; la sesión regresa por el App Link de scorpk.tech. */
    suspend fun signInWithGoogle(): Result<Unit> = call { supabase ->
        supabase.auth.signInWith(Google, redirectUrl = SupabaseProvider.REDIRECT_URL)
    }

    suspend fun signInWithGithub(): Result<Unit> = call { supabase ->
        supabase.auth.signInWith(Github, redirectUrl = SupabaseProvider.REDIRECT_URL)
    }

    suspend fun signOut(): Result<Unit> = call { supabase -> supabase.auth.signOut() }

    private val _callbackMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Errores devueltos por el proveedor en el deep link (acceso denegado, cuenta ya vinculada…). */
    val callbackMessages: SharedFlow<String> = _callbackMessages.asSharedFlow()

    /** Completa el flujo OAuth/PKCE cuando la app se abre desde el deep link de retorno. */
    fun handleDeeplink(intent: Intent) {
        val data = intent.data ?: return
        val isAppLink = data.scheme == SupabaseProvider.REDIRECT_SCHEME &&
            data.host == SupabaseProvider.REDIRECT_HOST &&
            data.path.orEmpty().startsWith(SupabaseProvider.REDIRECT_PATH)
        val isFallbackScheme = data.scheme == SupabaseProvider.FALLBACK_SCHEME &&
            data.host == SupabaseProvider.FALLBACK_HOST
        if (!isAppLink && !isFallbackScheme) return

        // Los errores pueden venir en la query o en el fragmento (#error=...).
        val params = (data.query.orEmpty() + "&" + data.fragment.orEmpty()).split('&')
            .mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { (key, value) -> key to Uri.decode(value.replace('+', ' ')) }
        val error = params["error_description"] ?: params["error"]
        if (error != null) {
            _callbackMessages.tryEmit(callbackErrorMessage(error))
            return
        }
        client?.handleDeeplinks(intent)
    }

    private fun callbackErrorMessage(raw: String): String = when {
        "access_denied" in raw || "denied" in raw.lowercase() -> "Cancelaste la autorización."
        "already" in raw.lowercase() && "linked" in raw.lowercase() -> "Esa cuenta ya está vinculada a otro usuario."
        "manual linking" in raw.lowercase() -> "Activa \"Allow manual linking\" en Supabase (Authentication → Sign In / Providers) para vincular cuentas."
        else -> "No se pudo completar el acceso: $raw"
    }

    suspend fun getProfile(): Result<Profile?> = call { supabase ->
        val userId = requireUserId(supabase)
        supabase.from(TABLE_PROFILES)
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<Profile>()
    }

    /**
     * Guarda el perfil del usuario autenticado. Si la fila aún no existe en `profiles`
     * (p. ej. no hay trigger de alta), la crea con upsert; si existe, solo la actualiza.
     * Solo se envían columnas de texto no vacías: nunca nulos ni tipos incompatibles.
     */
    suspend fun saveProfile(fullName: String?, customWakeWord: String?): Result<Profile> = call { supabase ->
        val userId = requireUserId(supabase)
        val fields = buildJsonObject {
            fullName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("full_name", it) }
            customWakeWord?.trim()?.takeIf { it.isNotEmpty() }?.let { put("custom_wake_word", it) }
        }

        val exists = supabase.from(TABLE_PROFILES)
            .select(Columns.list("id")) { filter { eq("id", userId) } }
            .decodeList<JsonObject>()
            .isNotEmpty()

        if (exists) {
            if (fields.isNotEmpty()) {
                supabase.from(TABLE_PROFILES).update(fields) { filter { eq("id", userId) } }
            }
        } else {
            val row = buildJsonObject {
                put("id", userId)
                fields.forEach { (key, value) -> put(key, value) }
            }
            supabase.from(TABLE_PROFILES).upsert(JsonArray(listOf(row))) { onConflict = "id" }
        }

        // Relee la fila; si las políticas RLS no permiten leerla, devuelve lo guardado.
        supabase.from(TABLE_PROFILES)
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<Profile>()
            ?: Profile(
                id = userId,
                fullName = fullName?.trim()?.ifBlank { null },
                customWakeWord = customWakeWord?.trim()?.ifBlank { null }
            )
    }

    private fun requireUserId(supabase: SupabaseClient): String =
        supabase.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Tu sesión expiró. Vuelve a iniciar sesión.")

    private suspend fun <T> call(block: suspend (SupabaseClient) -> T): Result<T> {
        val supabase = client ?: return Result.failure(
            IllegalStateException("Supabase no está configurado: faltan ${SupabaseProvider.missingVariables.joinToString()} en .env")
        )
        return withContext(Dispatchers.IO) {
            try {
                Result.success(block(supabase))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private companion object {
        const val TABLE_PROFILES = "profiles"
    }
}
