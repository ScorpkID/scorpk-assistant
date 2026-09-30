package com.scorpk.assistant.data.repository

import android.util.Log
import com.scorpk.assistant.data.remote.supabase.SupabaseProvider
import com.scorpk.assistant.data.remote.supabase.UserConnector
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Estado de los conectores en `public.user_connectors`: una fila por (usuario, proveedor)
 * significa "conectado"; eliminarla significa "desconectado".
 */
class ConnectorRepository(private val client: SupabaseClient?) {

    val isSignedIn: Boolean
        get() = client?.auth?.currentUserOrNull() != null

    suspend fun list(): Result<List<UserConnector>> = call { supabase, userId ->
        supabase.from(TABLE)
            .select { filter { eq("user_id", userId) } }
            .decodeList<UserConnector>()
    }

    /**
     * Marca el proveedor como conectado: upsert con conflicto en (user_id, provider). Si la base
     * aún no tiene esa restricción única, cae a borrar + insertar para no duplicar filas.
     */
    suspend fun connect(provider: String, scopes: List<String>): Result<Unit> = call { supabase, userId ->
        val row = UserConnector(userId = userId, provider = provider, scopes = scopes)
        try {
            supabase.from(TABLE).upsert(row) { onConflict = "user_id,provider" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "upsert de user_connectors falló; se usa borrar + insertar", e)
            supabase.from(TABLE).delete {
                filter {
                    eq("user_id", userId)
                    eq("provider", provider)
                }
            }
            supabase.from(TABLE).insert(row)
        }
        Unit
    }

    suspend fun disconnect(provider: String): Result<Unit> = call { supabase, userId ->
        supabase.from(TABLE).delete {
            filter {
                eq("user_id", userId)
                eq("provider", provider)
            }
        }
        Unit
    }

    private suspend fun <T> call(block: suspend (SupabaseClient, String) -> T): Result<T> {
        val supabase = client ?: return Result.failure(
            IllegalStateException("Supabase no está configurado: faltan ${SupabaseProvider.missingVariables.joinToString()} en .env")
        )
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: return Result.failure(IllegalStateException("Inicia sesión para sincronizar tus conectores."))
        return withContext(Dispatchers.IO) {
            try {
                Result.success(block(supabase, userId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private companion object {
        const val TAG = "ScorpkAssistant"
        const val TABLE = "user_connectors"
    }
}
