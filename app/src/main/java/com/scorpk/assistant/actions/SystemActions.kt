package com.scorpk.assistant.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.AlarmClock
import android.view.KeyEvent
import com.scorpk.assistant.domain.model.ActionResult
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.util.PermissionChecker
import java.text.Normalizer
import kotlin.math.roundToInt

enum class MediaCommand(val keyCode: Int, val label: String) {
    PLAY_PAUSE(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Reproducir/pausar"),
    PLAY(KeyEvent.KEYCODE_MEDIA_PLAY, "Reproduciendo"),
    PAUSE(KeyEvent.KEYCODE_MEDIA_PAUSE, "En pausa"),
    NEXT(KeyEvent.KEYCODE_MEDIA_NEXT, "Siguiente canción"),
    PREVIOUS(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Canción anterior"),
    STOP(KeyEvent.KEYCODE_MEDIA_STOP, "Reproducción detenida");

    companion object {
        fun from(value: String?): MediaCommand? = when (value?.trim()?.lowercase()) {
            "play_pause", "toggle" -> PLAY_PAUSE
            "play", "reproducir", "resume", "reanudar" -> PLAY
            "pause", "pausa", "pausar" -> PAUSE
            "next", "siguiente" -> NEXT
            "previous", "prev", "anterior" -> PREVIOUS
            "stop", "detener" -> STOP
            else -> null
        }
    }
}

/**
 * Acciones directas sobre el sistema mediante Intents y servicios de Android.
 * Todas son no bloqueantes; las que lanzan Activities se invocan desde el hilo principal
 * a través de [ActionDispatcher].
 */
class SystemActions(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)
    private val batteryManager = appContext.getSystemService(BatteryManager::class.java)

    @Volatile
    private var torchOn = false
    private var torchCameraId: String? = null

    init {
        torchCameraId = findTorchCameraId()
        torchCameraId?.let { id ->
            cameraManager.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == id) torchOn = enabled
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    // region Apps

    fun openApp(target: String?): ActionResult {
        if (target.isNullOrBlank()) return ActionResult.Failure("No indicaste qué aplicación abrir.")
        val pm = appContext.packageManager
        val packageName = if (isInstalled(target.trim())) target.trim() else findPackageByLabel(target)
            ?: return ActionResult.Failure("No encontré la aplicación \"$target\" en el dispositivo.")

        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: return ActionResult.Failure("La aplicación \"$target\" no se puede abrir directamente.")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            appContext.startActivity(launchIntent)
            val label = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            ActionResult.Success("Abriendo $label")
        } catch (e: ActivityNotFoundException) {
            ActionResult.Failure("No se pudo abrir \"$target\".")
        } catch (e: SecurityException) {
            ActionResult.PermissionRequired(
                RequiredPermission.OVERLAY,
                "Android bloqueó la apertura en segundo plano. Concede \"Mostrar sobre otras apps\"."
            )
        }
    }

    private fun isInstalled(packageName: String): Boolean = try {
        appContext.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun findPackageByLabel(query: String): String? {
        val pm = appContext.packageManager
        val needle = normalize(query)
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcherIntent, 0).map {
            it.activityInfo.packageName to normalize(it.loadLabel(pm).toString())
        }
        return apps.firstOrNull { it.second == needle }?.first
            ?: apps.firstOrNull { it.second.startsWith(needle) }?.first
            ?: apps.firstOrNull { needle in it.second }?.first
            ?: apps.firstOrNull { it.second in needle && it.second.length >= 3 }?.first
    }

    // endregion

    // region Volumen y multimedia

    fun adjustVolume(direction: String?): ActionResult {
        val stream = AudioManager.STREAM_MUSIC
        val flags = AudioManager.FLAG_SHOW_UI
        val numeric = direction?.trim()?.removeSuffix("%")?.toIntOrNull()
        if (numeric != null) return setVolumePercent(numeric)
        return when (direction?.trim()?.lowercase()) {
            "up", "subir", "raise", "+" -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, flags)
                ActionResult.Success("Volumen al ${currentVolumePercent()}%")
            }
            "down", "bajar", "lower", "-" -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, flags)
                ActionResult.Success("Volumen al ${currentVolumePercent()}%")
            }
            "mute", "silenciar" -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, flags)
                ActionResult.Success("Volumen silenciado")
            }
            "unmute", "activar" -> {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, flags)
                ActionResult.Success("Sonido activado")
            }
            "max", "maximo" -> setVolumePercent(100)
            else -> ActionResult.Failure("No entendí cómo ajustar el volumen.")
        }
    }

    fun setVolumePercent(percent: Int): ActionResult {
        val stream = AudioManager.STREAM_MUSIC
        val max = audioManager.getStreamMaxVolume(stream)
        val target = (percent.coerceIn(0, 100) / 100f * max).roundToInt()
        audioManager.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
        return ActionResult.Success("Volumen al ${currentVolumePercent()}%")
    }

    private fun currentVolumePercent(): Int {
        val stream = AudioManager.STREAM_MUSIC
        val max = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        return (audioManager.getStreamVolume(stream) * 100f / max).roundToInt()
    }

    fun mediaControl(command: MediaCommand): ActionResult {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, command.keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, command.keyCode, 0))
        return ActionResult.Success(command.label)
    }

    // endregion

    // region Linterna

    /** @param enable true/false para forzar el estado, null para alternar. */
    fun setFlashlight(enable: Boolean?): ActionResult {
        if (!PermissionChecker.isGranted(appContext, RequiredPermission.CAMERA)) {
            return ActionResult.PermissionRequired(
                RequiredPermission.CAMERA,
                "Necesito acceso a la cámara para controlar la linterna."
            )
        }
        val cameraId = torchCameraId ?: findTorchCameraId().also { torchCameraId = it }
            ?: return ActionResult.Failure("Este dispositivo no tiene linterna.")
        val target = enable ?: !torchOn
        return try {
            cameraManager.setTorchMode(cameraId, target)
            torchOn = target
            ActionResult.Success(if (target) "Linterna encendida" else "Linterna apagada")
        } catch (e: Exception) {
            ActionResult.Failure("No se pudo cambiar la linterna: ${e.message ?: "cámara en uso"}")
        }
    }

    private fun findTorchCameraId(): String? = try {
        cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Exception) {
        null
    }

    // endregion

    // region Alarmas, temporizadores y batería

    /** @param time formato "HH:mm" (24h). */
    fun setAlarm(time: String?, label: String?): ActionResult {
        val match = time?.trim()?.let { TIME_REGEX.matchEntire(it) }
            ?: return ActionResult.Failure("No entendí la hora de la alarma.")
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour !in 0..23 || minute !in 0..59) return ActionResult.Failure("Hora inválida: $time")

        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!label.isNullOrBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return startIntent(intent, "Alarma configurada para las %02d:%02d".format(hour, minute))
    }

    fun setTimer(seconds: Int?, label: String?): ActionResult {
        if (seconds == null || seconds <= 0) return ActionResult.Failure("No entendí la duración del temporizador.")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!label.isNullOrBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return startIntent(intent, "Temporizador de ${formatDuration(seconds)} iniciado")
    }

    fun batteryStatus(): ActionResult {
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = batteryManager.isCharging
        return ActionResult.Success(
            "Batería al $level%" + if (charging) ", cargando" else ""
        )
    }

    private fun startIntent(intent: Intent, successMessage: String): ActionResult = try {
        appContext.startActivity(intent)
        ActionResult.Success(successMessage)
    } catch (e: ActivityNotFoundException) {
        ActionResult.Failure("No hay una app de reloj compatible instalada.")
    } catch (e: SecurityException) {
        ActionResult.Failure("Android no permitió completar la acción: ${e.message}")
    }

    private fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildList {
            if (h > 0) add("$h h")
            if (m > 0) add("$m min")
            if (s > 0) add("$s s")
        }.joinToString(" ")
    }

    // endregion

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase().trim(), Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")

    private companion object {
        val TIME_REGEX = Regex("""(\d{1,2}):(\d{2})""")
        val DIACRITICS_REGEX = Regex("""\p{Mn}+""")
    }
}
