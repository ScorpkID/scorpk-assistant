package com.scorpk.assistant.connectors

import com.scorpk.assistant.domain.model.RequiredPermission

enum class ConnectorCategory(val label: String) {
    PRODUCTIVITY("Productividad"),
    MEDIA("Música y video"),
    COMMUNICATION("Comunicación"),
    NAVIGATION("Mapas"),
    DEVELOPMENT("Desarrollo")
}

/** DEVICE: usa apps y permisos del teléfono. ACCOUNT: se autoriza con OAuth (Supabase Auth). */
enum class ConnectionKind { DEVICE, ACCOUNT }

/** Cuenta OAuth de la que un conector ACCOUNT obtiene su token. */
enum class AccountProvider(val key: String, val label: String) {
    GOOGLE("google", "Google"),
    GITHUB("github", "GitHub")
}

/**
 * Catálogo de conectores. [provider] es el valor de `public.user_connectors.provider`;
 * [scopes] se guarda en la columna `scopes text[]` (scopes OAuth reales en los de cuenta,
 * capacidades lógicas en los del dispositivo).
 */
enum class ConnectorType(
    val provider: String,
    val label: String,
    val tagline: String,
    val description: String,
    val category: ConnectorCategory,
    val kind: ConnectionKind,
    val capabilities: List<String>,
    val examples: List<String>,
    val scopes: List<String>,
    val packageName: String? = null,
    val permissions: List<RequiredPermission> = emptyList(),
    val account: AccountProvider? = null
) {
    SPOTIFY(
        provider = "spotify",
        label = "Spotify",
        tagline = "Música con tu voz",
        description = "Controla Spotify sin tocar el teléfono: reproduce canciones, artistas y playlists, pausa, salta y pregunta qué está sonando.",
        category = ConnectorCategory.MEDIA,
        kind = ConnectionKind.DEVICE,
        capabilities = listOf("Reproducir canciones, artistas y playlists", "Pausar, reanudar y cambiar de canción", "Saber qué está sonando"),
        examples = listOf("Pon Bad Bunny en Spotify", "Siguiente canción", "¿Qué está sonando?"),
        scopes = listOf("playback:control", "playback:search"),
        packageName = "com.spotify.music"
    ),
    YOUTUBE(
        provider = "youtube",
        label = "YouTube",
        tagline = "Busca y reproduce videos",
        description = "Abre YouTube directamente en los resultados de lo que pidas.",
        category = ConnectorCategory.MEDIA,
        kind = ConnectionKind.DEVICE,
        capabilities = listOf("Buscar videos por voz", "Abrir los resultados en la app de YouTube"),
        examples = listOf("Busca recetas de arepas en YouTube", "Pon un tutorial de guitarra en YouTube"),
        scopes = listOf("search"),
        packageName = "com.google.android.youtube"
    ),
    GOOGLE_CALENDAR(
        provider = "google_calendar",
        label = "Google Calendar",
        tagline = "Tu agenda al instante",
        description = "Lee tu agenda y crea eventos con recordatorio en cualquiera de los calendarios sincronizados en tu teléfono.",
        category = ConnectorCategory.PRODUCTIVITY,
        kind = ConnectionKind.DEVICE,
        capabilities = listOf("Consultar eventos de hoy, mañana o la semana", "Crear eventos con hora y recordatorio"),
        examples = listOf("¿Qué tengo mañana?", "Agenda dentista el viernes a las 4 de la tarde"),
        scopes = listOf("calendar:read", "calendar:write"),
        permissions = listOf(RequiredPermission.CALENDAR)
    ),
    GOOGLE_DRIVE(
        provider = "google_drive",
        label = "Google Drive",
        tagline = "Encuentra tus archivos",
        description = "Busca en tu Drive por nombre y abre el archivo correcto. Requiere autorizar tu cuenta de Google.",
        category = ConnectorCategory.PRODUCTIVITY,
        kind = ConnectionKind.ACCOUNT,
        capabilities = listOf("Buscar archivos por nombre", "Ver tus archivos más recientes", "Abrir un archivo en Drive"),
        examples = listOf("Busca el presupuesto en mi Drive", "¿Cuáles son mis archivos recientes de Drive?"),
        scopes = listOf("https://www.googleapis.com/auth/drive.metadata.readonly"),
        account = AccountProvider.GOOGLE
    ),
    GMAIL(
        provider = "gmail",
        label = "Gmail",
        tagline = "Tu bandeja, resumida",
        description = "Revisa cuántos correos tienes sin leer y quién te escribió. Solo lectura. Requiere autorizar tu cuenta de Google.",
        category = ConnectorCategory.COMMUNICATION,
        kind = ConnectionKind.ACCOUNT,
        capabilities = listOf("Contar correos sin leer", "Leer remitente y asunto de los más recientes", "Buscar correos por tema"),
        examples = listOf("¿Tengo correos nuevos?", "¿Quién me escribió hoy?"),
        scopes = listOf("https://www.googleapis.com/auth/gmail.readonly"),
        account = AccountProvider.GOOGLE
    ),
    EMAIL(
        provider = "email",
        label = "Correo",
        tagline = "Redacta con tu voz",
        description = "Prepara un correo con destinatario, asunto y mensaje en tu app de correo, listo para enviar.",
        category = ConnectorCategory.COMMUNICATION,
        kind = ConnectionKind.DEVICE,
        capabilities = listOf("Redactar correos por voz", "Buscar el correo de un contacto"),
        examples = listOf("Escribe un correo a Ana diciendo que llego tarde"),
        scopes = listOf("compose"),
        permissions = listOf(RequiredPermission.CONTACTS)
    ),
    CONTACTS(
        provider = "contacts",
        label = "Teléfono",
        tagline = "Llama a tus contactos",
        description = "Encuentra a cualquiera de tus contactos por nombre y prepara la llamada.",
        category = ConnectorCategory.COMMUNICATION,
        kind = ConnectionKind.DEVICE,
        capabilities = listOf("Buscar contactos por nombre", "Iniciar llamadas"),
        examples = listOf("Llama a mamá", "Llama a Juan Pérez"),
        scopes = listOf("contacts:read", "dial"),
        permissions = listOf(RequiredPermission.CONTACTS)
    ),
    WHATSAPP(
        provider = "whatsapp",
        label = "WhatsApp",
        tagline = "Mensajes sin teclear",
        description = "Abre el chat de un contacto con el mensaje ya escrito. Con el servicio de accesibilidad activo, también lo envía.",
        category = ConnectorCategory.COMMUNICATION,
        kind = ConnectionKind.DEVICE,
        capabilities = listOf("Escribir mensajes a tus contactos", "Enviarlos automáticamente (con accesibilidad)"),
        examples = listOf("Escríbele a Ana por WhatsApp: ya voy en camino"),
        scopes = listOf("contacts:read", "message"),
        packageName = "com.whatsapp",
        permissions = listOf(RequiredPermission.CONTACTS)
    ),
    MAPS(
        provider = "maps",
        label = "Google Maps",
        tagline = "Llega sin escribir",
        description = "Inicia la navegación hacia el lugar que pidas.",
        category = ConnectorCategory.NAVIGATION,
        kind = ConnectionKind.DEVICE,
        capabilities = listOf("Navegar a una dirección o lugar", "Abrir la ruta en Google Maps"),
        examples = listOf("Llévame al aeropuerto", "Cómo llego a la Universidad Nacional"),
        scopes = listOf("navigate"),
        packageName = "com.google.android.apps.maps"
    ),
    GITHUB(
        provider = "github",
        label = "GitHub",
        tagline = "Tu código al día",
        description = "Consulta tus notificaciones, repositorios e issues asignados. Requiere autorizar tu cuenta de GitHub.",
        category = ConnectorCategory.DEVELOPMENT,
        kind = ConnectionKind.ACCOUNT,
        capabilities = listOf("Leer tus notificaciones", "Listar tus repositorios recientes", "Ver los issues que tienes asignados"),
        examples = listOf("¿Qué notificaciones tengo en GitHub?", "¿Cuáles son mis issues abiertos?"),
        scopes = listOf("read:user", "notifications", "repo"),
        account = AccountProvider.GITHUB
    );

    companion object {
        fun fromProvider(value: String?): ConnectorType? = entries.firstOrNull { it.provider == value }
    }
}
