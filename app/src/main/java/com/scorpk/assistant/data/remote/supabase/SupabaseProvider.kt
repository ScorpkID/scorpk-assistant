package com.scorpk.assistant.data.remote.supabase

import android.content.Context
import android.util.Log
import com.scorpk.assistant.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

/**
 * Crea el cliente de Supabase con las credenciales de [BuildConfig] (inyectadas desde .env).
 * Si falta alguna variable, [missingVariables] la indica y no se crea el cliente.
 */
object SupabaseProvider {

    /**
     * Destino del retorno OAuth: App Link verificado del dominio oficial. Es la única URL de retorno
     * que la lista "Redirect URLs" de Supabase acepta hoy; cualquier otra cae en la Site URL.
     */
    const val REDIRECT_SCHEME = "https"
    const val REDIRECT_HOST = "scorpk.tech"
    const val REDIRECT_PATH = "/auth/callback"
    const val REDIRECT_URL = "$REDIRECT_SCHEME://$REDIRECT_HOST$REDIRECT_PATH"

    /** Respaldo por esquema propio (solo funciona si se añade a las Redirect URLs de Supabase). */
    const val FALLBACK_SCHEME = "com.scorpk.assistant"
    const val FALLBACK_HOST = "login-callback"

    val missingVariables: List<String> = buildList {
        if (BuildConfig.SUPABASE_URL.isBlank()) add("SUPABASE_URL")
        if (BuildConfig.SUPABASE_ANON_KEY.isBlank()) add("SUPABASE_ANON_KEY")
    }

    val isConfigured: Boolean get() = missingVariables.isEmpty()

    /** Json tolerante: acepta ids numéricos o uuid y columnas extra que la app no usa. */
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun create(context: Context): SupabaseClient? {
        if (!isConfigured) {
            Log.w(TAG, "Supabase deshabilitado: faltan ${missingVariables.joinToString()} en .env")
            return null
        }
        return try {
            createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_ANON_KEY
            ) {
                defaultSerializer = KotlinXSerializer(json)
                install(Auth) {
                    flowType = FlowType.PKCE
                    scheme = REDIRECT_SCHEME
                    host = REDIRECT_HOST
                    defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
                    sessionManager = EncryptedSessionManager(context.applicationContext)
                }
                install(Postgrest)
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo crear el cliente de Supabase (¿SUPABASE_URL válida?)", e)
            null
        }
    }

    private const val TAG = "ScorpkAssistant"
}
