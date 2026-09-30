package com.scorpk.assistant.connectors

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import com.scorpk.assistant.domain.model.ActionResult
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.service.ScorpkNotificationListener
import com.scorpk.assistant.util.PermissionChecker

enum class SpotifyCommand {
    PLAY, PAUSE, TOGGLE, NEXT, PREVIOUS, NOW_PLAYING;

    companion object {
        fun from(value: String?): SpotifyCommand? = when (value?.trim()?.lowercase()) {
            "play", "reproducir", "resume", "search", "buscar" -> PLAY
            "pause", "pausa", "pausar", "stop" -> PAUSE
            "play_pause", "toggle" -> TOGGLE
            "next", "siguiente" -> NEXT
            "previous", "prev", "anterior" -> PREVIOUS
            "now_playing", "que_suena", "current" -> NOW_PLAYING
            else -> null
        }
    }
}

/**
 * Control de Spotify en tres niveles, del más directo al más compatible:
 * 1. MediaController de la sesión activa de Spotify (requiere acceso a notificaciones).
 * 2. Intents nativos: MEDIA_PLAY_FROM_SEARCH y el esquema URI `spotify:`.
 * 3. Botones multimedia enviados directamente al receptor de Spotify.
 */
class SpotifyController(context: Context) {

    private val appContext = context.applicationContext
    private val sessionManager = appContext.getSystemService(MediaSessionManager::class.java)

    val isInstalled: Boolean
        get() = try {
            appContext.packageManager.getPackageInfo(PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }

    val hasDirectControl: Boolean
        get() = PermissionChecker.isGranted(appContext, RequiredPermission.NOTIFICATION_ACCESS)

    fun execute(command: SpotifyCommand, query: String?): ActionResult {
        if (!isInstalled) return ActionResult.Failure("Spotify no está instalado en este dispositivo.")
        return when (command) {
            SpotifyCommand.PLAY -> if (query.isNullOrBlank()) resume() else playFromSearch(query.trim())
            SpotifyCommand.PAUSE -> transport("En pausa", KeyEvent.KEYCODE_MEDIA_PAUSE) { it.pause() }
            SpotifyCommand.TOGGLE -> {
                val playing = controller()?.playbackState?.state == PlaybackState.STATE_PLAYING
                if (playing) {
                    transport("En pausa", KeyEvent.KEYCODE_MEDIA_PAUSE) { it.pause() }
                } else {
                    resume()
                }
            }
            SpotifyCommand.NEXT -> transport("Siguiente canción", KeyEvent.KEYCODE_MEDIA_NEXT) { it.skipToNext() }
            SpotifyCommand.PREVIOUS -> transport("Canción anterior", KeyEvent.KEYCODE_MEDIA_PREVIOUS) { it.skipToPrevious() }
            SpotifyCommand.NOW_PLAYING -> nowPlaying()
        }
    }

    private fun playFromSearch(query: String): ActionResult {
        controller()?.let { session ->
            session.transportControls.playFromSearch(query, Bundle())
            return ActionResult.Success("Reproduciendo \"$query\" en Spotify")
        }
        val searchIntent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(PACKAGE)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            appContext.startActivity(searchIntent)
            ActionResult.Success("Reproduciendo \"$query\" en Spotify")
        } catch (e: ActivityNotFoundException) {
            openUri("spotify:search:${Uri.encode(query)}", "Buscando \"$query\" en Spotify")
        } catch (e: SecurityException) {
            openUri("spotify:search:${Uri.encode(query)}", "Buscando \"$query\" en Spotify")
        }
    }

    private fun resume(): ActionResult {
        controller()?.let { session ->
            session.transportControls.play()
            return ActionResult.Success("Reproduciendo en Spotify")
        }
        sendMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY)
        return ActionResult.Success("Reproduciendo en Spotify")
    }

    private inline fun transport(
        successMessage: String,
        fallbackKey: Int,
        action: (MediaController.TransportControls) -> Unit
    ): ActionResult {
        val session = controller()
        if (session != null) {
            action(session.transportControls)
        } else {
            sendMediaButton(fallbackKey)
        }
        return ActionResult.Success(successMessage)
    }

    private fun nowPlaying(): ActionResult {
        if (!hasDirectControl) {
            return ActionResult.PermissionRequired(
                RequiredPermission.NOTIFICATION_ACCESS,
                "Activa el acceso a notificaciones de Scorpk para saber qué suena en Spotify."
            )
        }
        val session = controller() ?: return ActionResult.Success("Spotify no está reproduciendo nada ahora.")
        val metadata = session.metadata ?: return ActionResult.Success("No hay información de la canción actual.")
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val playing = session.playbackState?.state == PlaybackState.STATE_PLAYING
        val base = listOfNotNull(title, artist?.let { "de $it" }).joinToString(" ")
        return ActionResult.Success(if (playing) "Está sonando $base" else "En pausa: $base")
    }

    /** Sesión de medios activa de Spotify, si hay acceso a notificaciones. */
    private fun controller(): MediaController? {
        if (!hasDirectControl) return null
        return try {
            sessionManager.getActiveSessions(ComponentName(appContext, ScorpkNotificationListener::class.java))
                .firstOrNull { it.packageName == PACKAGE }
        } catch (e: SecurityException) {
            null
        }
    }

    /** Envía el botón multimedia al receptor de Spotify (funciona aunque otra app tenga el foco de audio). */
    private fun sendMediaButton(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP).forEach { action ->
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setPackage(PACKAGE)
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(now, now, action, keyCode, 0))
            appContext.sendBroadcast(intent)
        }
    }

    private fun openUri(uri: String, successMessage: String): ActionResult = try {
        appContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ActionResult.Success(successMessage)
    } catch (e: ActivityNotFoundException) {
        ActionResult.Failure("No se pudo abrir Spotify.")
    }

    companion object {
        const val PACKAGE = "com.spotify.music"
    }
}
