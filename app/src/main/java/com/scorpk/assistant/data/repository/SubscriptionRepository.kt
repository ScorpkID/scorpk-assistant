package com.scorpk.assistant.data.repository

import com.scorpk.assistant.data.remote.supabase.Subscription
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lectura del plan del usuario en `subscriptions` (la escribe la web con el webhook de Stripe). */
class SubscriptionRepository(private val client: SupabaseClient?) {

    /** Sin sesión o sin fila devuelve éxito con null (plan Free); nunca consulta sin sesión activa. */
    suspend fun current(): Result<Subscription?> {
        val supabase = client ?: return Result.success(null)
        val userId = supabase.auth.currentUserOrNull()?.id ?: return Result.success(null)
        return withContext(Dispatchers.IO) {
            try {
                Result.success(
                    supabase.from("subscriptions")
                        .select { filter { eq("user_id", userId) } }
                        .decodeSingleOrNull<Subscription>()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
