package com.scorpk.assistant

import com.scorpk.assistant.update.UpdateInfo
import com.scorpk.assistant.update.UpdatePolicy
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val LENIENT_JSON = Json { ignoreUnknownKeys = true }

class UpdatePolicyTest {

    private val goodHash = "a".repeat(64)

    private fun info(
        code: Int = 5,
        min: Int = 1,
        url: String = "https://github.com/ScorpkID/scorpk-web/releases/download/android-v1.2.0/scorpk-assistant-1.2.0.apk",
        hash: String = goodHash
    ) = UpdateInfo(
        versionCode = code,
        versionName = "1.2.0",
        minSupportedVersionCode = min,
        apkUrl = url,
        sha256 = hash
    )

    @Test
    fun `detecta si hay una version mas nueva`() {
        assertTrue(UpdatePolicy.isNewer(info(code = 5), installedVersionCode = 4))
        assertFalse(UpdatePolicy.isNewer(info(code = 5), installedVersionCode = 5))
        assertFalse(UpdatePolicy.isNewer(info(code = 5), installedVersionCode = 9))
    }

    @Test
    fun `toda version nueva es obligatoria`() {
        // Aunque el archivo diga que se soporta desde la 1, una versión más nueva ya es obligatoria.
        assertTrue(UpdatePolicy.isMandatory(info(code = 5, min = 1), installedVersionCode = 4))
        assertTrue(UpdatePolicy.isMandatory(info(code = 5, min = 5), installedVersionCode = 1))
        assertFalse(UpdatePolicy.isMandatory(info(code = 5, min = 1), installedVersionCode = 5))
        assertFalse(UpdatePolicy.isMandatory(info(code = 5, min = 9), installedVersionCode = 6))
    }

    @Test
    fun `acepta solo https de dominios permitidos`() {
        assertTrue(UpdatePolicy.isTrustworthy(info()))
        assertTrue(UpdatePolicy.isTrustworthy(info(url = "https://scorpk.tech/android/scorpk.apk")))
        assertFalse(UpdatePolicy.isTrustworthy(info(url = "http://scorpk.tech/android/scorpk.apk")))
        assertFalse(UpdatePolicy.isTrustworthy(info(url = "https://evil.example.com/scorpk.apk")))
        assertFalse(UpdatePolicy.isTrustworthy(info(url = "https://scorpk.tech.evil.com/scorpk.apk")))
        assertFalse(UpdatePolicy.isTrustworthy(info(url = "no es una url")))
    }

    @Test
    fun `exige un sha256 valido`() {
        assertFalse(UpdatePolicy.isTrustworthy(info(hash = "")))
        assertFalse(UpdatePolicy.isTrustworthy(info(hash = "abc123")))
        assertFalse(UpdatePolicy.isTrustworthy(info(hash = "z".repeat(64))))
        assertTrue(UpdatePolicy.isTrustworthy(info(hash = "F".repeat(64))))
    }

    @Test
    fun `lee el latest json publicado y tolera campos extra`() {
        val raw = """
            {"versionCode":2,"versionName":"1.1.0","minSupportedVersionCode":1,
             "apkUrl":"https://github.com/ScorpkID/scorpk-web/releases/download/android-v1.1.0/a.apk",
             "sha256":"${goodHash}","sizeBytes":123,"releasedAt":"2026-09-30","notes":["Nuevo"],"futuro":true}
        """.trimIndent()
        val parsed = LENIENT_JSON.decodeFromString(UpdateInfo.serializer(), raw)
        assertEquals(2, parsed.versionCode)
        assertEquals(listOf("Nuevo"), parsed.notes)
        assertTrue(UpdatePolicy.isTrustworthy(parsed))
    }
}
