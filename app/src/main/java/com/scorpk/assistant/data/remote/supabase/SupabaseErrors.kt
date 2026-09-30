package com.scorpk.assistant.data.remote.supabase

import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException

/** Traduce errores de Supabase a mensajes claros (nunca "null"). */
object SupabaseErrors {

    fun describe(error: Throwable): String {
        val raw = when (error) {
            is RestException -> listOf(error.error, error.description)
                .filter { !it.isNullOrBlank() && it != "null" }
                .joinToString(": ")
            is HttpRequestException -> "Sin conexión con Supabase."
            else -> error.message.orEmpty()
        }.trim()
        val lower = raw.lowercase()
        return when {
            "row-level security" in lower || "42501" in lower ->
                "Supabase rechazó el cambio por las políticas RLS de la tabla. Revisa que permitan insertar/actualizar tus propias filas."
            "invalid login credentials" in lower -> "Correo o contraseña incorrectos."
            "email not confirmed" in lower -> "Confirma tu correo antes de iniciar sesión."
            "user already registered" in lower -> "Ya existe una cuenta con ese correo."
            "jwt expired" in lower -> "Tu sesión expiró. Vuelve a iniciar sesión."
            raw.isBlank() || raw == "null" -> "Error inesperado de Supabase (${error::class.simpleName})."
            else -> raw
        }
    }
}
