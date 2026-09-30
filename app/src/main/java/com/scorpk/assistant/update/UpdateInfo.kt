package com.scorpk.assistant.update

import kotlinx.serialization.Serializable
import java.net.URI

/**
 * Descripción de la última versión publicada, servida en https://scorpk.tech/android/latest.json.
 * Todas las actualizaciones son obligatorias; [minSupportedVersionCode] se conserva en el archivo por compatibilidad.
 */
@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val minSupportedVersionCode: Int = 1,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long = 0,
    val releasedAt: String? = null,
    val notes: List<String> = emptyList()
)

/** Reglas puras de decisión: versión más nueva, actualización obligatoria y validez del anuncio. */
object UpdatePolicy {

    /** Solo se descargan APK desde estos dominios (GitHub redirige luego a su CDN, ya bajo https). */
    private val ALLOWED_HOSTS = setOf("scorpk.tech", "github.com")
    private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")

    fun isNewer(info: UpdateInfo, installedVersionCode: Int): Boolean = info.versionCode > installedVersionCode

    /** Toda versión nueva es obligatoria: hasta actualizar no se puede usar la app. */
    fun isMandatory(info: UpdateInfo, installedVersionCode: Int): Boolean = isNewer(info, installedVersionCode)

    /** Un anuncio inválido se ignora: no se ofrece ni se descarga nada. */
    fun isTrustworthy(info: UpdateInfo): Boolean {
        if (info.versionCode <= 0 || !SHA256_REGEX.matches(info.sha256)) return false
        val uri = try {
            URI(info.apkUrl)
        } catch (e: Exception) {
            return false
        }
        return uri.scheme == "https" && uri.host?.lowercase() in ALLOWED_HOSTS
    }
}
