package com.scorpk.assistant.connectors

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.scorpk.assistant.actions.AccessibilityAutomator
import com.scorpk.assistant.domain.model.ActionResult
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.service.ScorpkAccessService
import com.scorpk.assistant.util.PermissionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

data class Contact(val name: String, val number: String?, val email: String?)

/**
 * Acciones directas sobre apps del teléfono mediante intents nativos:
 * llamadas, WhatsApp, Google Maps, YouTube y correo.
 */
class DeviceConnectors(
    context: Context,
    private val automator: AccessibilityAutomator
) {
    private val appContext = context.applicationContext

    // region Llamadas

    suspend fun call(target: String?): ActionResult {
        if (target.isNullOrBlank()) return ActionResult.Failure("¿A quién quieres llamar?")
        val number = if (target.any { it.isDigit() } && target.count { it.isDigit() } >= 5) {
            target.filter { it.isDigit() || it == '+' }
        } else {
            val contact = findContact(target) ?: return contactFailure(target)
            contact.number ?: return ActionResult.Failure("${contact.name} no tiene un número guardado.")
        }
        return start(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")),
            "Llamando a ${target.trim()}"
        )
    }

    // endregion

    // region WhatsApp

    suspend fun whatsapp(target: String?, message: String?): ActionResult {
        if (target.isNullOrBlank()) return ActionResult.Failure("¿A quién le escribo por WhatsApp?")
        val digits = if (target.count { it.isDigit() } >= 7) {
            normalizePhone(target)
        } else {
            val contact = findContact(target) ?: return contactFailure(target)
            normalizePhone(contact.number ?: return ActionResult.Failure("${contact.name} no tiene número guardado."))
        }
        val text = message?.trim().orEmpty()
        val uri = Uri.parse("https://wa.me/$digits" + if (text.isNotEmpty()) "?text=${Uri.encode(text)}" else "")
        val opened = start(
            Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"),
            "Chat de WhatsApp abierto"
        )
        if (opened !is ActionResult.Success || text.isEmpty()) return opened

        // Con accesibilidad activa se pulsa "Enviar" para que el mensaje salga solo.
        if (ScorpkAccessService.instance.value != null) {
            delay(SEND_DELAY_MS)
            val sent = withContext(Dispatchers.Default) { SEND_LABELS.any { automator.clickNode(it) is ActionResult.Success } }
            if (sent) return ActionResult.Success("Mensaje enviado por WhatsApp a ${target.trim()}")
        }
        return ActionResult.Success("Dejé el mensaje listo en WhatsApp. Solo pulsa enviar.")
    }

    // endregion

    // region Mapas y YouTube

    fun navigate(place: String?): ActionResult {
        if (place.isNullOrBlank()) return ActionResult.Failure("¿A dónde quieres ir?")
        val navigation = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(place)}"))
            .setPackage("com.google.android.apps.maps")
        val result = start(navigation, "Navegando a ${place.trim()}")
        if (result is ActionResult.Success) return result
        return start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(place)}")), "Buscando ${place.trim()} en el mapa")
    }

    fun youtube(query: String?): ActionResult {
        if (query.isNullOrBlank()) return ActionResult.Failure("¿Qué quieres buscar en YouTube?")
        val search = Intent(Intent.ACTION_SEARCH)
            .setPackage("com.google.android.youtube")
            .putExtra("query", query.trim())
        val result = start(search, "Buscando \"${query.trim()}\" en YouTube")
        if (result is ActionResult.Success) return result
        return start(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query.trim())}")),
            "Buscando \"${query.trim()}\" en YouTube"
        )
    }

    // endregion

    // region Correo

    suspend fun composeEmail(to: String?, subject: String?, body: String?): ActionResult {
        val address = when {
            to.isNullOrBlank() -> null
            to.contains('@') -> to.trim()
            else -> findContact(to)?.email ?: return ActionResult.Failure("No encontré el correo de \"${to.trim()}\" en tus contactos.")
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, address?.let { arrayOf(it) } ?: emptyArray())
            .putExtra(Intent.EXTRA_SUBJECT, subject.orEmpty())
            .putExtra(Intent.EXTRA_TEXT, body.orEmpty())
        return start(intent, "Correo listo para enviar" + (address?.let { " a $it" }.orEmpty()))
    }

    // endregion

    // region Contactos

    /** Mejor coincidencia por nombre: exacta > empieza por > contiene (ignora tildes y mayúsculas). */
    suspend fun findContact(name: String): Contact? = withContext(Dispatchers.IO) {
        if (!PermissionChecker.isGranted(appContext, RequiredPermission.CONTACTS)) return@withContext null
        val needle = normalize(name)
        val candidates = mutableMapOf<Long, Contact>()
        val resolver = appContext.contentResolver

        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val display = c.getString(1) ?: continue
                if (normalize(display).let { needle !in it }) continue
                val id = c.getLong(0)
                val existing = candidates[id]
                if (existing?.number == null || c.getInt(3) == 1) {
                    candidates[id] = Contact(display, c.getString(2), existing?.email)
                }
            }
        }
        resolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTACT_ID,
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.ADDRESS
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val display = c.getString(1) ?: continue
                if (needle !in normalize(display)) continue
                val id = c.getLong(0)
                val existing = candidates[id]
                candidates[id] = Contact(display, existing?.number, existing?.email ?: c.getString(2))
            }
        }
        candidates.values.minByOrNull { contact ->
            val n = normalize(contact.name)
            when {
                n == needle -> 0
                n.startsWith(needle) -> 1
                else -> 2
            } * 1000 + n.length
        }
    }

    private fun contactFailure(name: String): ActionResult =
        if (!PermissionChecker.isGranted(appContext, RequiredPermission.CONTACTS)) {
            ActionResult.PermissionRequired(RequiredPermission.CONTACTS, "Necesito acceso a tus contactos para encontrar a \"${name.trim()}\".")
        } else {
            ActionResult.Failure("No encontré a \"${name.trim()}\" en tus contactos.")
        }

    // endregion

    private fun start(intent: Intent, successMessage: String): ActionResult = try {
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionResult.Success(successMessage)
    } catch (e: ActivityNotFoundException) {
        ActionResult.Failure("No hay una app disponible para hacer eso.")
    } catch (e: SecurityException) {
        ActionResult.Failure("Android bloqueó la acción. Concede \"Mostrar sobre otras apps\" a Scorpk.")
    }

    /** Deja solo dígitos con prefijo de país (usa el del dispositivo si el número no lo trae). */
    private fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (trimmed.startsWith("+")) return digits
        if (trimmed.startsWith("00")) return digits.removePrefix("00")
        val code = COUNTRY_CODES[Locale.getDefault().country] ?: return digits
        // Un número nacional (sin prefijo): se antepone el código del país.
        return if (digits.startsWith(code) && digits.length > 10) digits else code + digits.removePrefix("0")
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase().trim(), Normalizer.Form.NFD).replace(DIACRITICS, "")

    private companion object {
        const val SEND_DELAY_MS = 1_800L
        val SEND_LABELS = listOf("Enviar", "Send")
        val DIACRITICS = Regex("""\p{Mn}+""")
        val COUNTRY_CODES = mapOf(
            "CO" to "57", "MX" to "52", "AR" to "54", "ES" to "34", "US" to "1", "PE" to "51",
            "CL" to "56", "EC" to "593", "VE" to "58", "BR" to "55", "UY" to "598", "PA" to "507",
            "CR" to "506", "GT" to "502", "DO" to "1", "BO" to "591", "PY" to "595", "HN" to "504"
        )
    }
}
