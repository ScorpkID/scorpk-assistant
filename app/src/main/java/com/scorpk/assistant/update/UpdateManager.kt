package com.scorpk.assistant.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.scorpk.assistant.data.local.SettingsDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Actualizaciones directas desde la app, sin navegador:
 * consulta scorpk.tech/android/latest.json → descarga el APK → verifica su SHA-256 → lo instala con
 * PackageInstaller. Android siempre pide una confirmación al usuario y solo acepta APK firmados con
 * la misma llave que la app instalada, así que un archivo alterado no puede instalarse.
 */
class UpdateManager(
    context: Context,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val settings: SettingsDataStore
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastCheckAt = 0L
    /** Versión que el usuario pospuso en esta sesión (solo si no es obligatoria). */
    private var postponedVersion = 0

    val installedVersionCode: Int
        get() = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode.toInt()
        } catch (e: Exception) {
            0
        }

    val installedVersionName: String
        get() = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }

    init {
        // Si el instalador del sistema rechaza el paquete, se le explica al usuario.
        scope.launch {
            InstallEvents.outcomes.collect { outcome -> onInstallOutcome(outcome) }
        }
    }

    // region Comprobación

    /** Comprobación silenciosa cada vez que la app vuelve al primer plano. Con [force] ignora el intervalo mínimo. */
    fun check(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckAt < MIN_CHECK_INTERVAL_MS) return
        if (_state.value !is UpdateState.Idle) return
        scope.launch { checkNow(manual = false) }
    }

    /**
     * Comprobación con resultado, para el botón «Buscar actualizaciones». Una comprobación manual
     * vuelve a ofrecer la versión aunque el usuario la haya pospuesto antes.
     */
    suspend fun checkNow(manual: Boolean = true): CheckResult = withContext(Dispatchers.IO) {
        lastCheckAt = System.currentTimeMillis()
        val info = try {
            fetchLatest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo comprobar actualizaciones", e)
            return@withContext CheckResult.Failed("No se pudo comprobar. Revisa tu conexión.")
        }
        val installed = installedVersionCode
        if (!UpdatePolicy.isTrustworthy(info) || !UpdatePolicy.isNewer(info, installed)) {
            settings.setRequiredUpdateCode(0)
            return@withContext CheckResult.UpToDate
        }
        val mandatory = UpdatePolicy.isMandatory(info, installed)
        // Se recuerda para bloquear también el asistente flotante y el arranque sin conexión.
        if (mandatory) settings.setRequiredUpdateCode(info.versionCode)
        if (_state.value is UpdateState.Idle && (mandatory || manual || info.versionCode != postponedVersion)) {
            _state.value = UpdateState.Available(info, mandatory)
        }
        CheckResult.Available(info)
    }

    /** true si hay una actualización obligatoria pendiente que esta versión aún no tiene. */
    suspend fun isUpdateRequired(): Boolean = settings.requiredUpdateCode() > installedVersionCode

    private fun fetchLatest(): UpdateInfo {
        val request = Request.Builder().url(LATEST_URL).header("Cache-Control", "no-cache").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            return try {
                json.decodeFromString(UpdateInfo.serializer(), body)
            } catch (e: SerializationException) {
                throw IOException("latest.json inválido", e)
            }
        }
    }

    // endregion

    // region Descarga e instalación

    /** El usuario pospone una actualización opcional; se le vuelve a ofrecer en la próxima apertura. */
    fun postpone() {
        val current = _state.value
        if (current is UpdateState.Available && !current.mandatory) {
            postponedVersion = current.info.versionCode
            _state.value = UpdateState.Idle
        }
    }

    /** Botón «Actualizar»: pide el permiso si falta, y luego descarga, verifica e instala. */
    fun startUpdate() {
        val (info, mandatory) = when (val s = _state.value) {
            is UpdateState.Available -> s.info to s.mandatory
            is UpdateState.NeedsInstallPermission -> s.info to s.mandatory
            is UpdateState.Failed -> s.info to s.mandatory
            else -> return
        }
        if (!canInstall()) {
            _state.value = UpdateState.NeedsInstallPermission(info, mandatory)
            return
        }
        job?.cancel()
        job = scope.launch { downloadAndInstall(info, mandatory) }
    }

    /** Al volver de los ajustes de Android: si ya concedió el permiso, continúa solo. */
    fun onAppResumed() {
        if (_state.value is UpdateState.NeedsInstallPermission && canInstall()) startUpdate()
    }

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()

    private suspend fun downloadAndInstall(info: UpdateInfo, mandatory: Boolean) {
        try {
            _state.value = UpdateState.Downloading(info, mandatory, 0f)
            val apk = withContext(Dispatchers.IO) { download(info, mandatory) }
            _state.value = UpdateState.Installing(info, mandatory)
            withContext(Dispatchers.IO) { install(apk) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al actualizar", e)
            _state.value = UpdateState.Failed(
                if (e is IOException) e.message ?: "No se pudo descargar la actualización." else "No se pudo instalar la actualización.",
                info,
                mandatory
            )
        }
    }

    private fun download(info: UpdateInfo, mandatory: Boolean): File {
        val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "scorpk-${info.versionCode}.apk")

        val request = Request.Builder().url(info.apkUrl).build()
        val digest = MessageDigest.getInstance("SHA-256")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("El servidor de descargas respondió ${response.code}.")
            val body = response.body ?: throw IOException("Descarga vacía.")
            val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = (downloaded * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.update { UpdateState.Downloading(info, mandatory, percent / 100f) }
                            }
                        }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(info.sha256, ignoreCase = true)) {
            target.delete()
            throw IOException("La descarga está dañada (verificación fallida). Inténtalo de nuevo.")
        }
        return target
    }

    private fun install(apk: File) {
        val installer = appContext.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(appContext.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("scorpk.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val resultIntent = Intent(appContext, InstallResultReceiver::class.java).setPackage(appContext.packageName)
            val pending = PendingIntent.getBroadcast(
                appContext,
                sessionId,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(pending.intentSender)
        }
    }

    private fun onInstallOutcome(outcome: InstallOutcome) {
        val (info, mandatory) = when (val s = _state.value) {
            is UpdateState.Installing -> s.info to s.mandatory
            else -> return
        }
        // Cancelar el diálogo del sistema no es un error: se vuelve a ofrecer la actualización.
        if (outcome.status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            _state.value = UpdateState.Available(info, mandatory)
            return
        }
        val message = when (outcome.status) {
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "Esta versión está firmada con otra llave. Desinstala Scorpk e instala la versión oficial de scorpk.tech."
            PackageInstaller.STATUS_FAILURE_STORAGE -> "No hay espacio suficiente para instalar la actualización."
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android bloqueó la instalación. Revisa Play Protect o los permisos."
            else -> "No se pudo instalar la actualización (${outcome.message ?: "código ${outcome.status}"})."
        }
        _state.value = UpdateState.Failed(message, info, mandatory)
    }

    // endregion

    private companion object {
        const val TAG = "ScorpkAssistant"
        const val LATEST_URL = "https://scorpk.tech/android/latest.json"
        const val MIN_CHECK_INTERVAL_MS = 10 * 60 * 1000L
        const val BUFFER_SIZE = 64 * 1024
    }
}
