package com.scorpk.assistant.connectors

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.scorpk.assistant.data.local.SettingsDataStore
import com.scorpk.assistant.data.remote.supabase.SupabaseErrors
import com.scorpk.assistant.data.repository.AuthRepository
import com.scorpk.assistant.data.repository.ConnectorRepository
import com.scorpk.assistant.data.repository.ProviderTokens
import com.scorpk.assistant.domain.model.ActionResult
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.util.PermissionChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ConnectorState {
    /** Conectado y listo para usarse. */
    CONNECTED,
    /** Todavía no conectado. */
    AVAILABLE,
    /** Conectado, pero falta la app en el dispositivo. */
    NEEDS_APP,
    /** Conectado, pero falta un permiso de Android. */
    NEEDS_PERMISSION,
    /** Conectado, pero el token de la cuenta venció: hay que volver a autorizar. */
    EXPIRED
}

data class ConnectorStatus(
    val type: ConnectorType,
    val connected: Boolean,
    val appInstalled: Boolean,
    val missingPermissions: List<RequiredPermission>,
    /** Solo conectores de cuenta: el token guardado sigue siendo válido. */
    val tokenValid: Boolean,
    /** Solo Spotify: acceso a sesiones de medios para el control directo. */
    val directControl: Boolean
) {
    val state: ConnectorState
        get() = when {
            !connected -> ConnectorState.AVAILABLE
            type.kind == ConnectionKind.ACCOUNT -> if (tokenValid) ConnectorState.CONNECTED else ConnectorState.EXPIRED
            !appInstalled -> ConnectorState.NEEDS_APP
            missingPermissions.isNotEmpty() -> ConnectorState.NEEDS_PERMISSION
            else -> ConnectorState.CONNECTED
        }
}

/** Resultado de pulsar "Conectar": la UI decide qué pedir al usuario. */
sealed interface ConnectResult {
    data class Connected(val warning: String?) : ConnectResult
    data object NeedsInstall : ConnectResult
    data class NeedsPermissions(val permissions: List<RequiredPermission>) : ConnectResult
    data object OAuthStarted : ConnectResult
    data class Failed(val message: String) : ConnectResult
}

/**
 * Orquesta los conectores: disponibilidad en el dispositivo, autorización OAuth de cuentas,
 * estado local (DataStore) y su sincronización con `public.user_connectors` en Supabase.
 */
class ConnectorManager(
    context: Context,
    private val settings: SettingsDataStore,
    private val repository: ConnectorRepository,
    private val auth: AuthRepository,
    private val tokens: ConnectorTokenStore,
    val spotify: SpotifyController,
    val calendar: CalendarManager
) {
    private val appContext = context.applicationContext
    private val refreshTick = MutableStateFlow(0)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Avisos globales, p. ej. "Google Drive conectado" al volver del consentimiento OAuth. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val statuses: Flow<List<ConnectorStatus>> =
        combine(settings.settings, tokens.tokens, refreshTick) { prefs, stored, _ ->
            ConnectorType.entries.map { type ->
                ConnectorStatus(
                    type = type,
                    connected = type.provider in prefs.connectedProviders,
                    appInstalled = type.packageName?.let(::isInstalled) ?: true,
                    missingPermissions = type.permissions.filterNot { PermissionChecker.isGranted(appContext, it) },
                    tokenValid = type.account?.let { account -> stored[account]?.isUsable(account) == true } ?: true,
                    directControl = type == ConnectorType.SPOTIFY && spotify.hasDirectControl
                )
            }
        }

    /** Empieza a escuchar los tokens OAuth que llegan tras el consentimiento. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            auth.providerTokens
                .catch { Log.w(TAG, "Flujo de tokens OAuth interrumpido", it) }
                .collect { token ->
                    try {
                        onProviderToken(token)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: VirtualMachineError) {
                        throw e
                    } catch (e: Throwable) {
                        // Cualquier fallo local (Keystore, DataStore, incluso un Error de enlace) no debe cerrar la app.
                        Log.e(TAG, "No se pudo guardar la autorización del conector", e)
                        _messages.emit("No se pudo guardar la autorización. Vuelve a intentarlo.")
                    }
                }
        }
    }

    /** Vuelve a leer permisos y apps instaladas (p. ej. al volver de Ajustes). */
    fun refresh() {
        refreshTick.value++
    }

    // region Conectar / desconectar

    suspend fun connect(type: ConnectorType): ConnectResult {
        if (type.kind == ConnectionKind.ACCOUNT) return startOAuth(type)

        if (type.packageName != null && !isInstalled(type.packageName)) return ConnectResult.NeedsInstall
        val missing = type.permissions.filterNot { PermissionChecker.isGranted(appContext, it) }
        if (missing.isNotEmpty()) return ConnectResult.NeedsPermissions(missing)
        return ConnectResult.Connected(markConnected(type))
    }

    suspend fun disconnect(type: ConnectorType): String? {
        updateLocal { it - type.provider }
        val account = type.account
        // El token de la cuenta se conserva mientras otro conector de la misma cuenta siga conectado.
        if (account != null) {
            val stillUsed = settings.settings.first().connectedProviders
                .mapNotNull(ConnectorType::fromProvider)
                .any { it.account == account }
            if (!stillUsed) tokens.clear(account)
        }
        if (!repository.isSignedIn) return null
        return repository.disconnect(type.provider).exceptionOrNull()?.let(::syncWarning)
    }

    private suspend fun startOAuth(type: ConnectorType): ConnectResult {
        val account = type.account ?: return ConnectResult.Failed("Este conector no usa una cuenta.")
        if (!auth.isConfigured) {
            return ConnectResult.Failed("Supabase no está configurado: agrega SUPABASE_URL y SUPABASE_ANON_KEY al .env.")
        }
        settings.setPendingOAuth(type.provider)
        return auth.requestProviderAccess(account, type.scopes).fold(
            onSuccess = { ConnectResult.OAuthStarted },
            onFailure = {
                settings.setPendingOAuth(null)
                ConnectResult.Failed(SupabaseErrors.describe(it))
            }
        )
    }

    /** Llega el token del proveedor tras volver del consentimiento: se guarda y se marca conectado. */
    private suspend fun onProviderToken(token: ProviderTokens) {
        val pending = settings.pendingOAuth()?.let(ConnectorType::fromProvider) ?: return
        val account = pending.account ?: return
        // Se consume primero: si guardar falla, el mismo token no se reintenta en cada arranque de la app.
        settings.setPendingOAuth(null)
        tokens.save(account, token.accessToken, token.refreshToken)
        val warning = markConnected(pending)
        _messages.emit(warning ?: "${pending.label} conectado")
        refresh()
    }

    private suspend fun markConnected(type: ConnectorType): String? {
        updateLocal { it + type.provider }
        if (!repository.isSignedIn) return null
        return repository.connect(type.provider, type.scopes).exceptionOrNull()?.let(::syncWarning)
    }

    // endregion

    // region Uso desde el asistente

    /**
     * Comprueba que el conector esté conectado y listo antes de ejecutar una acción.
     * @return null si se puede usar; si no, el resultado a devolver al usuario.
     */
    suspend fun gate(type: ConnectorType): ActionResult? {
        val prefs = settings.settings.first()
        if (type.provider !in prefs.connectedProviders) {
            return ActionResult.Failure("Conecta ${type.label} en Conectores para usar esto.")
        }
        if (type.packageName != null && !isInstalled(type.packageName)) {
            return ActionResult.Failure("${type.label} no está instalado en este dispositivo.")
        }
        type.permissions.firstOrNull { !PermissionChecker.isGranted(appContext, it) }?.let { missing ->
            return ActionResult.PermissionRequired(missing, "Necesito el permiso de ${PermissionChecker.displayName(missing)} para usar ${type.label}.")
        }
        return null
    }

    // endregion

    /**
     * Al iniciar sesión: une lo conectado en este dispositivo con lo guardado en Supabase y
     * sube lo que falte, para que ambos queden iguales.
     */
    suspend fun syncWithRemote(): String? {
        if (!repository.isSignedIn) return null
        val remote = repository.list().getOrElse { return syncWarning(it) }
            .mapNotNull { ConnectorType.fromProvider(it.provider) }
            .toSet()
        val local = settings.settings.first().connectedProviders.mapNotNull(ConnectorType::fromProvider).toSet()
        (local - remote).forEach { repository.connect(it.provider, it.scopes) }
        settings.setConnectedProviders((local + remote).map { it.provider }.toSet())
        return null
    }

    /** Ficha de Play Store de la app del conector. */
    fun installIntent(type: ConnectorType): Intent? = type.packageName?.let { pkg ->
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun launchIntent(type: ConnectorType): Intent? =
        type.packageName?.let { appContext.packageManager.getLaunchIntentForPackage(it) }
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private suspend fun updateLocal(transform: (Set<String>) -> Set<String>) {
        val current = settings.settings.first().connectedProviders
        settings.setConnectedProviders(transform(current))
    }

    private fun isInstalled(packageName: String): Boolean = try {
        appContext.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun syncWarning(error: Throwable): String {
        Log.w(TAG, "Sincronización de conectores fallida", error)
        return "Guardado en el dispositivo, pero no se pudo sincronizar con Supabase: ${SupabaseErrors.describe(error)}"
    }

    private companion object {
        const val TAG = "ScorpkAssistant"
    }
}
