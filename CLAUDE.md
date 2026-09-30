# Scorpk Assistant - Guía de Desarrollo para Claude

## Descripción del Proyecto
Scorpk es un asistente de voz y automatización nativo para Android escrito en **Kotlin**.
Su propósito es brindar control del dispositivo mediante comandos de voz (con detección de wake-word "Hey Scorpk") y una interfaz de chat minimalista (estilo Gemini Dark Mode), ejecutando acciones directas en el sistema mediante Intents y `AccessibilityService`.

---

## Stack Tecnológico
- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose + Material 3 (Dark Theme prioritario)
- **Asincronía:** Kotlin Coroutines + Flow
- **Inyección de dependencias / Arquitectura:** MVVM modular, Repository Pattern
- **Persistencia local:** Room Database o DataStore para configuraciones
- **Servicios:**
  - `ForegroundService` para escucha en background (wake-word)
  - `AccessibilityService` (`ScorpkAccessService`) para automatización de UI e inspección de pantalla
- **Compilador:** Gradle Kotlin DSL (`build.gradle.kts`)

---

## Comandos de Terminal (Windows CMD / PowerShell)
- **Compilar APK Debug:** `.\gradlew assembleDebug`
- **Compilar e Instalar directo al celular conectado:** `.\gradlew installDebug`
- **Limpiar proyecto:** `.\gradlew clean`
- **Revisar logs en tiempo real:** `adb logcat -s ScorpkAssistant`

---

## Reglas de Codificación
1. **Sin XML para UI:** Todo el frontend debe estar en Jetpack Compose.
2. **Modo Oscuro Puro:** Fondos `#000000` o `#121212`, superficies `#1E1E1E`, texto claro, acentos discretos.
3. **Manejo Seguro de Permisos:** Antes de invocar acciones protegidas (cámara, linterna, overlay, accesibilidad), validar el estado del permiso y proveer navegación a Settings si no está concedido.
4. **Function Calling Desacoplado:** El intérprete de LLM debe responder estrictamente con un JSON predecible (ver `ARCHITECTURE.md`), el cual un despachador (`ActionDispatcher`) procesa y ejecuta.
5. **No bloquear el hilo principal:** Toda acción pesada (STT, TTS, APIs, acceso a Room) se ejecuta bajo `Dispatchers.IO` o `Dispatchers.Default`.