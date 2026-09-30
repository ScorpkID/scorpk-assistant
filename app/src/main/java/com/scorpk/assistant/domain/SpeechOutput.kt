package com.scorpk.assistant.domain

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * Voz de Scorpk sobre TextToSpeech, afinada para sonar natural:
 * - Español (variante del dispositivo si es hispana; si no, es-ES o es-US).
 * - Elige la voz instalada de mayor calidad (neuronal / QUALITY_VERY_HIGH), priorizando
 *   voces locales de baja latencia y el mismo país que el idioma objetivo.
 * - Tono 1.05 y velocidad 1.0; el texto se limpia de Markdown, código y URLs.
 * Encola frases hasta que el motor está listo.
 */
class SpeechOutput(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<String>()

    fun speak(text: String) {
        val clean = SpeechTextSanitizer.sanitize(text).take(MAX_LENGTH)
        if (clean.isBlank()) return
        synchronized(this) {
            val engine = tts
            if (engine == null) {
                pending.addLast(clean)
                initEngine()
                return
            }
            if (!ready) {
                pending.addLast(clean)
                return
            }
            engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
    }

    fun stop() {
        synchronized(this) { tts?.stop() }
    }

    fun shutdown() {
        synchronized(this) {
            tts?.shutdown()
            tts = null
            ready = false
            pending.clear()
        }
    }

    private fun initEngine() {
        tts = TextToSpeech(appContext) { status ->
            synchronized(this) {
                val engine = tts ?: return@synchronized
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "TextToSpeech no disponible (status=$status)")
                    pending.clear()
                    return@synchronized
                }
                configure(engine)
                ready = true
                while (pending.isNotEmpty()) {
                    engine.speak(pending.removeFirst(), TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
                }
            }
        }
    }

    private fun configure(engine: TextToSpeech) {
        val target = targetLocale()
        val languageResult = engine.setLanguage(target)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(FALLBACK_LOCALES.firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE } ?: Locale.getDefault())
        }
        bestVoice(engine, target)?.let { voice ->
            engine.voice = voice
            Log.i(TAG, "Voz TTS: ${voice.name} (${voice.locale}, calidad ${voice.quality}, latencia ${voice.latency})")
        }
        engine.setPitch(PITCH)
        engine.setSpeechRate(SPEECH_RATE)
    }

    /** Idioma objetivo: el del dispositivo si ya es español; si no, español de España. */
    private fun targetLocale(): Locale {
        val device = Locale.getDefault()
        return if (device.language == "es") device else Locale.forLanguageTag("es-ES")
    }

    /**
     * Puntúa las voces en español instaladas: calidad (neuronal primero), mismo país,
     * sin red (menor latencia) y latencia declarada.
     */
    private fun bestVoice(engine: TextToSpeech, target: Locale): Voice? {
        val voices = try {
            engine.voices.orEmpty()
        } catch (e: Exception) {
            emptySet()
        }
        return voices
            .filter { it.locale.language == "es" }
            .filterNot { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in it.features.orEmpty() }
            .maxByOrNull { voice ->
                var score = voice.quality * 10
                if (voice.locale.country.equals(target.country, ignoreCase = true)) score += 25
                if (!voice.isNetworkConnectionRequired) score += 15
                score -= voice.latency / 10
                score
            }
    }

    private companion object {
        const val TAG = "ScorpkAssistant"
        const val MAX_LENGTH = 400
        const val PITCH = 1.05f
        const val SPEECH_RATE = 1.0f
        val FALLBACK_LOCALES = listOf(Locale.forLanguageTag("es-ES"), Locale.forLanguageTag("es-US"))
    }
}
