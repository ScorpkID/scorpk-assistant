package com.scorpk.assistant.data.repository

import com.scorpk.assistant.data.remote.supabase.SupabaseProvider
import com.scorpk.assistant.data.remote.supabase.TaskItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CRUD de la tabla `tasks` vía Postgrest.
 * Todas las consultas se filtran por el usuario autenticado (compatible con RLS).
 */
class TaskRepository(private val client: SupabaseClient?) {

    suspend fun tasks(): Result<List<TaskItem>> = call { supabase, userId ->
        supabase.from(TABLE_TASKS)
            .select {
                filter { eq("user_id", userId) }
                order("is_completed", Order.ASCENDING)
                order("due_date", Order.ASCENDING, nullsFirst = false)
            }
            .decodeList<TaskItem>()
    }

    suspend fun addTask(
        title: String,
        description: String? = null,
        dueDate: String? = null,
        priority: String? = null,
        isReminder: Boolean = false
    ): Result<TaskItem> = call { supabase, userId ->
        val task = TaskItem(
            userId = userId,
            title = title.trim(),
            description = description?.trim()?.ifBlank { null },
            dueDate = dueDate,
            priority = priority,
            isReminder = isReminder
        )
        supabase.from(TABLE_TASKS)
            .insert(task) { select() }
            .decodeSingle<TaskItem>()
    }

    suspend fun setCompleted(taskId: String, completed: Boolean): Result<Unit> = call { supabase, userId ->
        supabase.from(TABLE_TASKS).update({ set("is_completed", completed) }) {
            filter {
                eq("id", taskId)
                eq("user_id", userId)
            }
        }
        Unit
    }

    suspend fun deleteTask(taskId: String): Result<Unit> = call { supabase, userId ->
        supabase.from(TABLE_TASKS).delete {
            filter {
                eq("id", taskId)
                eq("user_id", userId)
            }
        }
        Unit
    }

    private suspend fun <T> call(block: suspend (SupabaseClient, String) -> T): Result<T> {
        val supabase = client ?: return Result.failure(
            IllegalStateException("Supabase no está configurado: faltan ${SupabaseProvider.missingVariables.joinToString()} en .env")
        )
        val userId = supabase.auth.currentUserOrNull()?.id
            ?: return Result.failure(IllegalStateException("Inicia sesión para ver tus tareas."))
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
        const val TABLE_TASKS = "tasks"
    }
}
