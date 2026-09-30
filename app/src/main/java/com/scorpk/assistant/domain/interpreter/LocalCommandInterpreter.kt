package com.scorpk.assistant.domain.interpreter

import com.scorpk.assistant.actions.ActionParser
import com.scorpk.assistant.domain.model.ActionParameters
import com.scorpk.assistant.domain.model.ActionRequest
import com.scorpk.assistant.domain.model.ActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Intérprete on-device basado en reglas para órdenes en español. Produce exactamente el mismo
 * JSON que devolvería un LLM, de modo que puede sustituirse por un cliente remoto sin tocar
 * el resto del pipeline.
 */
class LocalCommandInterpreter(
    private val parser: ActionParser
) : CommandInterpreter {

    override suspend fun interpret(input: String, history: List<ChatTurn>, images: List<String>): String =
        withContext(Dispatchers.Default) {
        parser.encode(resolve(input))
    }

    private fun resolve(input: String): ActionRequest {
        // Mismo largo en original y norm: los rangos de las regex sobre norm sirven para recortar original.
        val original = Normalizer.normalize(input.trim(), Normalizer.Form.NFC)
            .replace(QUESTION_MARKS, "")
            .replace(WAKE_WORD_PREFIX, "")
            .trim()
            .trimEnd('.', '!', '?')
        val norm = stripAccents(original.lowercase())

        return flashlight(norm)
            ?: spotify(norm, original)
            ?: youtube(norm, original)
            ?: whatsapp(norm, original)
            ?: email(norm, original)
            ?: drive(norm, original)
            ?: gmail(norm)
            ?: github(norm)
            ?: navigate(norm, original)
            ?: call(norm, original)
            ?: calendarCreate(norm, original)
            ?: calendarQuery(norm)
            ?: alarm(norm)
            ?: timer(norm)
            ?: battery(norm)
            ?: volume(norm)
            ?: media(norm)
            ?: readScreen(norm)
            ?: globalAction(norm)
            ?: click(norm, original)
            ?: sendMessage(norm, original)
            ?: openApp(norm, original)
            ?: chat(norm)
    }

    // region Reglas

    private fun flashlight(t: String): ActionRequest? {
        if (!Regex("""\b(linterna|flash|lampara)\b""").containsMatchIn(t)) return null
        val value = when {
            Regex("""\b(apaga|apagar|desactiva|desactivar|quita)\b""").containsMatchIn(t) -> false
            Regex("""\b(prende|prender|enciende|encender|activa|activar|pon)\b""").containsMatchIn(t) -> true
            else -> null
        }
        val speech = when (value) {
            true -> "Linterna encendida"
            false -> "Linterna apagada"
            null -> "Alternando la linterna"
        }
        return request(ActionType.TOGGLE_FLASHLIGHT, speech, value = value?.let(::JsonPrimitive))
    }

    private fun youtube(norm: String, original: String): ActionRequest? {
        val match = YOUTUBE.find(norm) ?: return null
        val query = original.substring(match.groups[1]!!.range).trim()
        return request(ActionType.YOUTUBE_SEARCH, "Buscando en YouTube", target = query)
    }

    private fun whatsapp(norm: String, original: String): ActionRequest? {
        val match = WHATSAPP_TO.find(norm) ?: WHATSAPP_FIRST.find(norm) ?: return null
        val contact = original.substring(match.groups[1]!!.range).trim()
        val message = match.groups[2]?.let { original.substring(it.range).trim().trim('"', '\'', '“', '”') }
            ?.replaceFirstChar { it.uppercase() }
        return request(
            ActionType.WHATSAPP_MESSAGE,
            "Escribiendo a $contact",
            target = contact,
            message = message?.takeIf { it.isNotEmpty() }
        )
    }

    private fun email(norm: String, original: String): ActionRequest? {
        val match = EMAIL_COMPOSE.find(norm) ?: return null
        val to = original.substring(match.groups[1]!!.range).trim()
        val body = match.groups[2]?.let { original.substring(it.range).trim() }
            ?.replaceFirstChar { it.uppercase() }
        return request(ActionType.COMPOSE_EMAIL, "Preparando el correo", target = to, message = body)
    }

    private fun drive(norm: String, original: String): ActionRequest? {
        DRIVE_SEARCH.find(norm)?.let { match ->
            val query = original.substring(match.groups[1]!!.range).trim()
            return request(ActionType.DRIVE_SEARCH, "Buscando en tu Drive", target = query)
        }
        if (DRIVE_RECENT.containsMatchIn(norm)) {
            return request(ActionType.DRIVE_SEARCH, "Tus archivos recientes")
        }
        return null
    }

    private fun gmail(norm: String): ActionRequest? {
        if (!GMAIL.containsMatchIn(norm)) return null
        return request(ActionType.GMAIL_INBOX, "Revisando tu correo")
    }

    private fun github(norm: String): ActionRequest? {
        if (!Regex("""\bgithub\b|\bmis issues\b""").containsMatchIn(norm)) return null
        val value = when {
            Regex("""\b(issues?)\b""").containsMatchIn(norm) -> "issues"
            Regex("""\b(repos|repositorios)\b""").containsMatchIn(norm) -> "repos"
            else -> "notifications"
        }
        return request(ActionType.GITHUB_QUERY, "Consultando GitHub", value = JsonPrimitive(value))
    }

    private fun navigate(norm: String, original: String): ActionRequest? {
        val match = NAVIGATE.find(norm) ?: return null
        val place = original.substring(match.groups[1]!!.range).trim()
        if (place.isEmpty()) return null
        return request(ActionType.NAVIGATE, "Iniciando la ruta", target = place)
    }

    private fun call(norm: String, original: String): ActionRequest? {
        val match = CALL.find(norm) ?: return null
        val who = original.substring(match.groups[1]!!.range).trim()
        return request(ActionType.CALL_CONTACT, "Llamando a $who", target = who)
    }

    private fun spotify(norm: String, original: String): ActionRequest? {
        if (NOW_PLAYING.containsMatchIn(norm)) {
            return request(ActionType.SPOTIFY_CONTROL, "Consultando Spotify", value = JsonPrimitive("now_playing"))
        }
        if (!Regex("""\bspotify\b""").containsMatchIn(norm)) return null
        SPOTIFY_PLAY.find(norm)?.let { match ->
            val query = original.substring(match.groups[2]!!.range).trim()
            if (query.isNotEmpty()) {
                return request(
                    ActionType.SPOTIFY_CONTROL,
                    "Poniendo $query en Spotify",
                    target = query,
                    value = JsonPrimitive("play")
                )
            }
        }
        val value = when {
            Regex("""\b(pausa|pausar|deten|detener|para)\b""").containsMatchIn(norm) -> "pause"
            Regex("""\b(siguiente|salta|adelanta)\b""").containsMatchIn(norm) -> "next"
            Regex("""\b(anterior|previa|regresa)\b""").containsMatchIn(norm) -> "previous"
            else -> "play"
        }
        return request(ActionType.SPOTIFY_CONTROL, "Listo", value = JsonPrimitive(value))
    }

    private fun calendarQuery(norm: String): ActionRequest? {
        if (!CAL_QUERY.containsMatchIn(norm)) return null
        val today = LocalDate.now()
        val value = when {
            Regex("""\besta semana\b""").containsMatchIn(norm) -> "$today/${today.plusDays(6)}"
            else -> resolveDay(norm, today)?.toString()
        }
        return request(ActionType.CALENDAR_QUERY, "Esta es tu agenda", value = value?.let(::JsonPrimitive))
    }

    private fun calendarCreate(norm: String, original: String): ActionRequest? {
        val match = CAL_CREATE.find(norm) ?: return null
        val restRange = match.groups[2]!!.range
        val rest = norm.substring(restRange)
        val today = LocalDate.now()

        val timeMatch = EVENT_TIME.find(rest)
        val time = timeMatch?.let { parseTime(it) }
        if (timeMatch == null || time == null) {
            return request(
                ActionType.RESPOND_CHAT,
                "¿A qué hora?",
                message = "¿A qué hora es el evento? Por ejemplo: \"agenda dentista mañana a las 4 de la tarde\"."
            )
        }
        val restWithoutTime = rest.replaceRange(timeMatch.range, " ".repeat(timeMatch.value.length))
        val dayMatch = DAY_WORDS.find(restWithoutTime)
        var date = resolveDay(restWithoutTime, today) ?: today
        if (dayMatch == null && date == today && time.isBefore(LocalTime.now())) date = today.plusDays(1)

        // El título es el resto sin las expresiones de fecha y hora (recortado del texto original).
        val removed = listOfNotNull(timeMatch.range, dayMatch?.range)
        val originalRest = original.substring(restRange)
        val title = originalRest.indices
            .filter { index -> removed.none { index in it } }
            .map { originalRest[it] }
            .joinToString("")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .removePrefix("un ").removePrefix("una ").removePrefix("el ").removePrefix("la ")
            .trim(' ', ',', '.')
            .replaceFirstChar { it.uppercase() }
            .ifBlank { "Evento" }

        val start = "%sT%02d:%02d".format(date, time.hour, time.minute)
        return request(
            ActionType.CALENDAR_CREATE,
            "Evento agendado",
            target = title,
            value = JsonPrimitive(start)
        )
    }

    private fun parseTime(match: MatchResult): LocalTime? {
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        var minute = match.groupValues[2].toIntOrNull() ?: 0
        when (match.groupValues[3]) {
            "y media" -> minute = 30
            "y cuarto" -> minute = 15
        }
        val period = match.groupValues[4]
        val isPm = period == "pm" || period.endsWith("tarde") || period.endsWith("noche")
        if (isPm && hour < 12) hour += 12
        if ((period == "am" || period.endsWith("manana")) && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /** "hoy", "mañana", "pasado mañana" o un día de la semana ("el viernes") → fecha. */
    private fun resolveDay(text: String, today: LocalDate): LocalDate? {
        val match = DAY_WORDS.find(text) ?: return null
        return when (val word = match.groupValues[1]) {
            "hoy" -> today
            "pasado manana" -> today.plusDays(2)
            "manana" -> today.plusDays(1)
            else -> WEEKDAYS[word]?.let { today.with(TemporalAdjusters.next(it)) }
        }
    }

    private fun alarm(t: String): ActionRequest? {
        if (!Regex("""\b(alarma|despiertame|despertador)\b""").containsMatchIn(t)) return null
        val match = ALARM_TIME.find(t) ?: return request(
            ActionType.RESPOND_CHAT,
            "¿A qué hora quieres la alarma?",
            message = "¿A qué hora quieres la alarma? Por ejemplo: \"pon una alarma a las 7:30\"."
        )
        var hour = match.groupValues[1].toInt()
        var minute = match.groupValues[2].toIntOrNull() ?: 0
        when (match.groupValues[3]) {
            "y media" -> minute = 30
            "y cuarto" -> minute = 15
            "menos cuarto" -> {
                hour = (hour + 23) % 24
                minute = 45
            }
        }
        val period = match.groupValues[4]
        if (period.isNotEmpty()) {
            val isPm = period == "pm" || period.endsWith("tarde") || period.endsWith("noche")
            if (isPm && hour < 12) hour += 12
            if (!isPm && hour == 12) hour = 0
        }
        val label = ALARM_LABEL.find(t)?.groupValues?.get(1)?.trim()?.replaceFirstChar { it.uppercase() }
            ?: "Alarma Scorpk"
        val time = "%02d:%02d".format(hour % 24, minute)
        return request(
            ActionType.SET_ALARM,
            "Alarma configurada para las $time",
            value = JsonPrimitive(time),
            message = label
        )
    }

    private fun timer(t: String): ActionRequest? {
        if (!Regex("""\b(temporizador|cronometro|timer|cuenta regresiva)\b""").containsMatchIn(t)) return null
        val prepared = t
            .replace(Regex("""\bmedia hora\b"""), "30 minutos")
            .replace(Regex("""\bun[oa]?\s+(hora|minuto|segundo)"""), "1 $1")
        val seconds = DURATION.findAll(prepared).sumOf { m ->
            val amount = m.groupValues[1].toInt()
            when {
                m.groupValues[2].startsWith("h") -> amount * 3600
                m.groupValues[2].startsWith("m") -> amount * 60
                else -> amount
            }
        }
        if (seconds <= 0) {
            return request(
                ActionType.RESPOND_CHAT,
                "¿De cuánto tiempo?",
                message = "¿De cuánto tiempo quieres el temporizador? Por ejemplo: \"temporizador de 5 minutos\"."
            )
        }
        return request(ActionType.SET_TIMER, "Temporizador iniciado", value = JsonPrimitive(seconds))
    }

    private fun battery(t: String): ActionRequest? {
        if (!Regex("""\b(bateria|carga)\b""").containsMatchIn(t)) return null
        return request(ActionType.BATTERY_STATUS, "Consultando batería")
    }

    private fun volume(t: String): ActionRequest? {
        val mentionsVolume = Regex("""\b(volumen|sonido)\b""").containsMatchIn(t)
        val mute = Regex("""\b(silencia|silenciar|mutea|mute)\b""").containsMatchIn(t)
        if (!mentionsVolume && !mute) return null
        val percent = Regex("""(\d{1,3})\s*(%|por ?ciento)?""").find(t)?.groupValues?.get(1)
        val value = when {
            mute -> "mute"
            percent != null -> percent
            Regex("""\b(sube|subir|aumenta|aumentar|mas alto|mas)\b""").containsMatchIn(t) -> "up"
            Regex("""\b(baja|bajar|disminuye|disminuir|mas bajo|menos)\b""").containsMatchIn(t) -> "down"
            Regex("""\b(maximo|tope)\b""").containsMatchIn(t) -> "max"
            Regex("""\b(activa|quita el silencio)\b""").containsMatchIn(t) -> "unmute"
            else -> return null
        }
        return request(ActionType.VOLUME_CONTROL, "Ajustando volumen", value = JsonPrimitive(value))
    }

    private fun media(t: String): ActionRequest? {
        val value = when {
            Regex("""\b(siguiente|salta|adelanta|next)\b""").containsMatchIn(t) -> "next"
            Regex("""\b(anterior|previa|regresa la cancion|previous)\b""").containsMatchIn(t) -> "previous"
            Regex("""\b(pausa|pausar|pausa la musica|deten|detener|para la musica)\b""").containsMatchIn(t) -> "pause"
            Regex("""\b(reproduce|reproducir|play|reanuda|continua|pon (la )?musica)\b""").containsMatchIn(t) -> "play"
            else -> return null
        }
        return request(ActionType.MEDIA_CONTROL, "Listo", value = JsonPrimitive(value))
    }

    private fun readScreen(t: String): ActionRequest? {
        if (!Regex("""(que hay en (la )?pantalla|lee (la )?pantalla|leer (la )?pantalla|que dice (la )?pantalla|que ves)""")
                .containsMatchIn(t)
        ) return null
        return request(ActionType.READ_SCREEN, "Esto es lo que hay en pantalla")
    }

    private fun globalAction(t: String): ActionRequest? {
        val target = when {
            Regex("""^(atras|regresa|volver|vuelve|ve atras|ir atras)$""").matches(t) -> "back"
            Regex("""\b(pantalla de inicio|ve al inicio|ir al inicio|home|escritorio)\b""").containsMatchIn(t) -> "home"
            Regex("""\b(ajustes rapidos|panel rapido)\b""").containsMatchIn(t) -> "quick_settings"
            Regex("""\bnotificaciones\b""").containsMatchIn(t) -> "notifications"
            Regex("""\b(recientes|multitarea)\b""").containsMatchIn(t) -> "recents"
            Regex("""\b(bloquea|bloquear) (la )?pantalla\b""").containsMatchIn(t) -> "lock"
            Regex("""\b(captura de pantalla|screenshot|haz una captura)\b""").containsMatchIn(t) -> "screenshot"
            else -> return null
        }
        return request(ActionType.GLOBAL_ACTION, "Hecho", target = target)
    }

    private fun click(norm: String, original: String): ActionRequest? {
        val match = CLICK.find(norm) ?: return null
        val target = original.substring(match.groups[3]!!.range).trim()
        return request(ActionType.CLICK_NODE, "Pulsando $target", target = target)
    }

    private fun sendMessage(norm: String, original: String): ActionRequest? {
        val match = SEND.find(norm) ?: return null
        val message = original.substring(match.groups[4]!!.range).trim().trim('"', '“', '”', '\'')
        if (message.isEmpty()) return null
        return request(ActionType.SEND_MESSAGE, "Mensaje enviado", message = message)
    }

    private fun openApp(norm: String, original: String): ActionRequest? {
        val match = OPEN.find(norm) ?: return null
        val name = match.groupValues[3].trim()
        val displayName = original.substring(match.groups[3]!!.range).trim()
        val target = APP_ALIASES[name] ?: displayName
        return request(ActionType.OPEN_APP, "Abriendo $displayName", target = target)
    }

    private fun chat(t: String): ActionRequest {
        val reply = when {
            Regex("""^(hola|buenas|hey|buenos dias|buenas tardes|buenas noches)\b""").containsMatchIn(t) ->
                "¡Hola! Soy Scorpk. Dime qué quieres hacer en tu teléfono."
            Regex("""\b(que puedes hacer|ayuda|comandos|como funcionas)\b""").containsMatchIn(t) -> HELP_TEXT
            Regex("""\b(gracias|genial|perfecto)\b""").containsMatchIn(t) -> "¡Con gusto!"
            else -> "Todavía no sé cómo hacer eso. Escribe \"ayuda\" para ver lo que puedo hacer."
        }
        return request(ActionType.RESPOND_CHAT, reply, message = reply)
    }

    // endregion

    private fun request(
        type: ActionType,
        speech: String,
        target: String? = null,
        value: JsonPrimitive? = null,
        message: String? = null
    ) = ActionRequest(
        action = type.wireName,
        parameters = ActionParameters(target = target, value = value, message = message),
        feedbackSpeech = speech
    )

    private fun stripAccents(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(DIACRITICS, "")

    private companion object {
        val DIACRITICS = Regex("""\p{Mn}+""")
        val QUESTION_MARKS = Regex("""[¿¡]""")
        val WAKE_WORD_PREFIX = Regex("""^(?i)(hey|oye|ok|hola)?\s*e?scorp\w*[,.]?\s*""")

        val ALARM_TIME = Regex(
            """\b(\d{1,2})(?::(\d{2}))?(?:\s*(y media|y cuarto|menos cuarto))?\s*(am|pm|de la manana|de la tarde|de la noche|de la madrugada)?\b"""
        )
        val ALARM_LABEL = Regex("""\bpara (?!las\b)(.+)$""")
        val DURATION = Regex("""(\d+)\s*(horas?|h|minutos?|min|segundos?|seg|s)\b""")

        val YOUTUBE = Regex("""^(?:busca|buscar|pon|reproduce|muestrame|quiero ver)\s+(.+?)\s+en\s+youtube$""")
        val WHATSAPP_TO = Regex("""^(?:escribele|escribe|mandale|manda|enviale|envia)\s+(?:a\s+)?(.+?)\s+(?:por|en|via|un)\s+whatsapp\s*(?:que|diciendo|:)?\s*(.*)$""")
        val WHATSAPP_FIRST = Regex("""^whatsapp\s+(?:a\s+)?(.+?)\s*(?::|que|diciendo)\s+(.+)$""")
        val EMAIL_COMPOSE = Regex("""^(?:escribe|redacta|manda|envia|mandale|enviale)\s+(?:un\s+)?(?:correo|email|mail)\s+(?:a\s+)?(.+?)(?:\s+(?:diciendo|diciendole|que diga|para decirle)\s+(.+))?$""")
        val DRIVE_SEARCH = Regex("""^(?:busca|buscar|encuentra|abre)\s+(?:el\s+|la\s+|mi\s+)?(.+?)\s+en\s+(?:mi\s+)?(?:google\s+)?drive$""")
        val DRIVE_RECENT = Regex("""\b(archivos|documentos)\s+recientes\b|\brecientes de drive\b""")
        val GMAIL = Regex("""(tengo\s+(?:algun\s+)?(?:correo|email|mail)s?|(?:correos?|emails?|mails?)\s+(?:nuevos|sin leer|no leidos)|revisa\s+(?:mi\s+)?(?:correo|gmail|bandeja)|mis correos|que correos)""")
        val NAVIGATE = Regex("""^(?:llevame|navega|navegame|guiame|ruta|como llego|como ir)\s+(?:a la |a los |a las |al |a |hasta |hacia )?(.+)$""")
        val CALL = Regex("""^(?:llama|llamar|llamale|marca|comunicame con)\s+(?:a\s+)?(.+)$""")
        val NOW_PLAYING = Regex("""(que (cancion )?(esta sonando|suena)|como se llama (esta|la) cancion)""")
        val SPOTIFY_PLAY = Regex("""^(pon|reproduce|toca|quiero escuchar|escuchar|play)\s+(.+?)\s+en\s+spotify$""")
        val CAL_QUERY = Regex("""\b(mi agenda|mis eventos|mi calendario|que tengo (hoy|manana|pasado manana|esta semana|el \w+)|que eventos tengo|tengo reuniones)\b""")
        val CAL_CREATE = Regex("""^(agendame|agenda|agendar|crea un evento|crear un evento|crea evento|anade al calendario|programa)\s+(?!(?:una |un )?(?:alarma|temporizador)|de (?:hoy|manana))(.+)$""")
        val EVENT_TIME = Regex("""\b(?:a las|a la|para las)\s+(\d{1,2})(?::(\d{2}))?(?:\s*(y media|y cuarto))?\s*(am|pm|de la manana|de la tarde|de la noche)?""")
        val DAY_WORDS = Regex("""\b(?:el\s+)?(hoy|pasado manana|manana|lunes|martes|miercoles|jueves|viernes|sabado|domingo)\b""")
        val WEEKDAYS = mapOf(
            "lunes" to DayOfWeek.MONDAY,
            "martes" to DayOfWeek.TUESDAY,
            "miercoles" to DayOfWeek.WEDNESDAY,
            "jueves" to DayOfWeek.THURSDAY,
            "viernes" to DayOfWeek.FRIDAY,
            "sabado" to DayOfWeek.SATURDAY,
            "domingo" to DayOfWeek.SUNDAY
        )

        val CLICK = Regex("""^(pulsa|toca|presiona|haz clic|haz click|dale|click|clic)(\s+(?:en|a|sobre)\b)?\s+(.+)$""")
        val SEND = Regex("""^(envia|manda|escribe)(\s+el)?(\s+mensaje)?\s*:?\s+(.+)$""")
        val OPEN = Regex("""^(abre|abrir|abreme|lanza|inicia|ejecuta|open)\s+(la app |la aplicacion |el |la )?(.+)$""")

        val APP_ALIASES = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "spotify" to "com.spotify.music",
            "instagram" to "com.instagram.android",
            "telegram" to "org.telegram.messenger",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "tiktok" to "com.zhiliaoapp.musically",
            "facebook" to "com.facebook.katana",
            "netflix" to "com.netflix.mediaclient",
            "play store" to "com.android.vending",
            "ajustes" to "com.android.settings",
            "configuracion" to "com.android.settings"
        )

        const val HELP_TEXT = "Puedo:\n" +
            "• Abrir apps: \"abre WhatsApp\"\n" +
            "• Linterna: \"prende la linterna\"\n" +
            "• Volumen: \"sube el volumen\", \"volumen al 40\"\n" +
            "• Música: \"pausa\", \"siguiente canción\"\n" +
            "• Spotify: \"pon Bad Bunny en Spotify\", \"¿qué está sonando?\"\n" +
            "• YouTube y Maps: \"busca recetas en YouTube\", \"llévame al aeropuerto\"\n" +
            "• Contactos: \"llama a mamá\", \"escríbele a Ana por WhatsApp: ya voy\"\n" +
            "• Cuentas: \"busca el presupuesto en mi Drive\", \"¿tengo correos nuevos?\", \"mis issues de GitHub\"\n" +
            "• Calendario: \"¿qué tengo mañana?\", \"agenda dentista el viernes a las 4 pm\"\n" +
            "• Alarmas: \"alarma a las 7:30 para gimnasio\"\n" +
            "• Temporizador: \"temporizador de 5 minutos\"\n" +
            "• Batería: \"¿cuánta batería tengo?\"\n" +
            "• Navegación: \"atrás\", \"ve al inicio\", \"notificaciones\"\n" +
            "• Pantalla: \"¿qué hay en pantalla?\", \"pulsa Aceptar\"\n" +
            "• Mensajes: \"envía hola, voy en camino\""
    }
}
