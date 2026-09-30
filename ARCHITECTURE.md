# Arquitectura Técnica - Scorpk Assistant

## 1. Estructura de Paquetes Sugerida

```text
com.scorpk.assistant/
├── actions/
│   ├── ActionDispatcher.kt         # Enrutador que mapea la acción del LLM con la ejecución
│   ├── SystemActions.kt            # Linterna, Volumen, Alarmas, Lanzar Apps
│   └── AccessibilityAutomator.kt   # Gestos, clicks e interacción con apps externas
├── data/
│   ├── local/                      # Room (Historial de comandos y preferencias)
│   └── remote/                     # Cliente LLM / Function Calling API
├── domain/
│   └── model/                      # Modelos: ActionRequest, ChatMessage, VoiceCommand
├── presentation/
│   ├── MainActivity.kt             # Entrada única
│   ├── chat/                       # ChatScreen, ChatViewModel, Componentes de mensaje
│   ├── drawer/                     # NavigationDrawerContent, DrawerFooter (Config y Cuenta)
│   └── theme/                      # Theme, Color (Dark palette), Type
└── service/
    ├── WakeWordForegroundService.kt # Foreground service para detección continua
    └── ScorpkAccessibilityService.kt# Servicio de accesibilidad registrado en el sistema
```

---

## 2. Contrato JSON para Function Calling (LLM Output)

El modelo de IA debe devolver una estructura JSON estricta para que `ActionDispatcher` la interprete sin ambigüedades:

```json
{
  "action": "open_app | media_control | toggle_flashlight | set_alarm | click_node | send_message | respond_chat",
  "parameters": {
    "target": "string (nombre de app, contacto o valor)",
    "value": "string | number | boolean",
    "message": "string"
  },
  "feedback_speech": "Texto breve que Scorpk dirá en voz alta para confirmar la acción"
}
```

### Ejemplos de Mapeo:
- **"Prende la linterna":**
  `{"action": "toggle_flashlight", "parameters": {"value": true}, "feedback_speech": "Linterna encendida"}`
- **"Abre Spotify":**
  `{"action": "open_app", "parameters": {"target": "com.spotify.music"}, "feedback_speech": "Abriendo Spotify"}`
- **"Pon la alarma a las 7:30":**
  `{"action": "set_alarm", "parameters": {"value": "07:30", "message": "Despertar"}, "feedback_speech": "Alarma configurada para las 7:30 AM"}`

---

## 3. Permisos Clave en `AndroidManifest.xml`

```xml
<!-- Micrófono y Servicio en segundo plano -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

<!-- Control de hardware e Intents -->
<uses-permission android:name="android.permission.CAMERA" /> <!-- Linterna -->
<uses-permission android:name="com.android.alarm.permission.SET_ALARM" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" /> <!-- Overlay flotante -->
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" /> <!-- Listar apps instaladas -->

<!-- Configuración de AccessibilityService dentro de <application> -->
<!-- Requiere service con android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" -->
```