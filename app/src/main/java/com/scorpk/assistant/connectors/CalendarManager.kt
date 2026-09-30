package com.scorpk.assistant.connectors

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import com.scorpk.assistant.domain.model.ActionResult
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.util.PermissionChecker
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.TimeZone

data class CalendarEvent(
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?
)

/**
 * Agenda del dispositivo mediante CalendarContract (Google Calendar y cualquier cuenta
 * sincronizada). Debe llamarse fuera del hilo principal: consulta el ContentProvider.
 */
class CalendarManager(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val zone: ZoneId get() = ZoneId.systemDefault()

    val hasPermission: Boolean
        get() = PermissionChecker.isGranted(appContext, RequiredPermission.CALENDAR)

    // region Consulta

    /**
     * @param range null = desde ahora hasta el final de mañana; "yyyy-MM-dd" = ese día;
     * "yyyy-MM-dd/yyyy-MM-dd" = rango de días (inclusive).
     */
    fun describeAgenda(range: String?): ActionResult {
        if (!hasPermission) return permissionRequired()
        val (from, to, label) = resolveRange(range)
            ?: return ActionResult.Failure("No entendí las fechas de la consulta.")
        val events = try {
            events(from, to)
        } catch (e: SecurityException) {
            return permissionRequired()
        }
        if (events.isEmpty()) return ActionResult.Success("No tienes eventos $label.")

        val multiDay = to - from > DAY_MS
        val lines = events.take(MAX_EVENTS).map { event ->
            val start = Instant.ofEpochMilli(event.begin).atZone(zone)
            val time = if (event.allDay) "Todo el día" else start.format(TIME_FORMAT)
            val day = if (multiDay) start.format(DAY_FORMAT).replaceFirstChar { it.uppercase() } + ", " else ""
            val place = event.location?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            "• $day$time — ${event.title}$place"
        }
        val header = if (events.size == 1) "Tienes 1 evento $label:" else "Tienes ${events.size} eventos $label:"
        return ActionResult.Success(header + "\n" + lines.joinToString("\n"))
    }

    fun events(fromMillis: Long, toMillis: Long): List<CalendarEvent> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMillis)
            ContentUris.appendId(it, toMillis)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION
        )
        val result = mutableListOf<CalendarEvent>()
        resolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                result += CalendarEvent(
                    title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(Sin título)",
                    begin = c.getLong(1),
                    end = c.getLong(2),
                    allDay = c.getInt(3) == 1,
                    location = c.getString(4)
                )
            }
        }
        return result
    }

    // endregion

    // region Creación

    /**
     * @param when inicio "yyyy-MM-ddTHH:mm" (hora local) o intervalo "inicio/fin".
     * Si no hay un calendario con permiso de escritura, abre el editor de la app de calendario.
     */
    fun createEvent(title: String?, `when`: String?, description: String?): ActionResult {
        val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ActionResult.Failure("¿Cómo se llama el evento?")
        val (start, end) = parseInterval(`when`)
            ?: return ActionResult.Failure("¿Para qué fecha y hora es \"$cleanTitle\"?")
        if (!hasPermission) return permissionRequired()

        val calendarId = writableCalendarId()
            ?: return openEditor(cleanTitle, start, end, description)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, cleanTitle)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            description?.takeIf { it.isNotBlank() }?.let { put(CalendarContract.Events.DESCRIPTION, it) }
        }
        return try {
            val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
                ?: return openEditor(cleanTitle, start, end, description)
            addReminder(ContentUris.parseId(uri))
            val formatted = Instant.ofEpochMilli(start).atZone(zone).format(FULL_FORMAT)
            ActionResult.Success("Evento \"$cleanTitle\" agendado para el $formatted")
        } catch (e: SecurityException) {
            permissionRequired()
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo crear el evento", e)
            openEditor(cleanTitle, start, end, description)
        }
    }

    private fun addReminder(eventId: Long) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, REMINDER_MINUTES)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        try {
            resolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo añadir el recordatorio", e)
        }
    }

    /** Calendario visible con permiso de escritura; prioriza el principal de la cuenta de Google. */
    private fun writableCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_TYPE
        )
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        var best: Pair<Long, Int>? = null
        resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null)?.use { c ->
            while (c.moveToNext()) {
                var score = 0
                if (c.getInt(1) == 1) score += 2
                if (c.getString(2) == "com.google") score += 1
                if (best == null || score > best!!.second) best = c.getLong(0) to score
            }
        }
        return best?.first
    }

    private fun openEditor(title: String, start: Long, end: Long, description: String?): ActionResult {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(intent)
            ActionResult.Success("Abrí el calendario para confirmar \"$title\"")
        } catch (e: ActivityNotFoundException) {
            ActionResult.Failure("No hay una app de calendario instalada.")
        }
    }

    // endregion

    // region Fechas

    private fun resolveRange(range: String?): Triple<Long, Long, String>? {
        val now = LocalDateTime.now(zone)
        if (range.isNullOrBlank()) {
            val end = now.toLocalDate().plusDays(2).atStartOfDay()
            return Triple(now.toMillis(), end.toMillis(), "hoy y mañana")
        }
        val parts = range.split('/').map { it.trim() }
        val first = parseDate(parts[0]) ?: return null
        val last = parts.getOrNull(1)?.let { parseDate(it) ?: return null } ?: first
        val label = when {
            first == last && first == now.toLocalDate() -> "hoy"
            first == last && first == now.toLocalDate().plusDays(1) -> "mañana"
            first == last -> "el ${first.format(DAY_FORMAT)}"
            else -> "entre el ${first.format(DAY_FORMAT)} y el ${last.format(DAY_FORMAT)}"
        }
        return Triple(first.atStartOfDay().toMillis(), last.plusDays(1).atStartOfDay().toMillis(), label)
    }

    private fun parseInterval(value: String?): Pair<Long, Long>? {
        if (value.isNullOrBlank()) return null
        val parts = value.split('/').map { it.trim() }
        val start = parseDateTime(parts[0]) ?: return null
        val end = parts.getOrNull(1)?.let { parseDateTime(it) }?.takeIf { it > start }
            ?: (start + DEFAULT_DURATION_MS)
        return start to end
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value.take(10))
    } catch (e: DateTimeParseException) {
        null
    }

    private fun parseDateTime(value: String): Long? {
        val normalized = value.replace(' ', 'T')
        return try {
            OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            try {
                LocalDateTime.parse(normalized).toMillis()
            } catch (e: DateTimeParseException) {
                parseDate(normalized)?.atTime(9, 0)?.toMillis()
            }
        }
    }

    private fun LocalDateTime.toMillis(): Long = atZone(zone).toInstant().toEpochMilli()

    // endregion

    private fun permissionRequired() = ActionResult.PermissionRequired(
        RequiredPermission.CALENDAR,
        "Necesito permiso de calendario para consultar y crear eventos."
    )

    private companion object {
        const val TAG = "ScorpkAssistant"
        const val MAX_EVENTS = 10
        const val REMINDER_MINUTES = 10
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val DEFAULT_DURATION_MS = 60 * 60 * 1000L
        val SPANISH: Locale = Locale.forLanguageTag("es-ES")
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SPANISH)
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", SPANISH)
        val FULL_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", SPANISH)
    }
}
