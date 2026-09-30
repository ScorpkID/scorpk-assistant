# Especificación de Requerimientos de Software (Scorpk Assistant)

## 1. Requerimientos Funcionales (RF)

### Módulo UI & Navegación
- **RF-01 (Interfaz Principal):** La aplicación debe contar con una pantalla principal tipo chat que liste la conversación e historial de comandos ejecutados en tiempo real.
- **RF-02 (Barra de Navegación Lateral - Drawer):** 
  - Activada mediante el ícono de 3 líneas (hamburguesa) en la parte superior izquierda.
  - Debe listar el historial de conversaciones pasadas.
  - Debe fijar en la parte inferior dos accesos directos: **Configuración (⚙️)** y **Cuenta / Perfil (👤)**.
- **RF-03 (Entrada Multimodal):** La barra inferior de la pantalla principal debe permitir ingresar comandos mediante texto escrito o activando el botón de micrófono táctil.

### Módulo de Voz y Detección
- **RF-04 (Wake-word "Hey Scorpk"):** La aplicación debe contar con un servicio en segundo plano persistente capaz de detectar la frase clave *"Hey Scorpk"* con el dispositivo bloqueado o en uso.
- **RF-05 (Speech-to-Text / STT):** Tras detectar el wake-word, el sistema debe transcribir la orden del usuario a texto continuo.
- **RF-06 (Respuesta por Voz / TTS):** El asistente debe confirmar la ejecución de la acción mediante síntesis de voz (Text-to-Speech) o sonido sutil de confirmación.

### Módulo de Control del Sistema (Intents directos)
- **RF-07 (Lanzador de Aplicaciones):** Abrir aplicaciones instaladas a partir de su nombre o alias (ej. *"Abre WhatsApp"*, *"Abre YouTube"*).
- **RF-08 (Control Multimedia):** Controlar reproducción multimedia global (Play, Pause, Siguiente canción, Canción anterior) y ajuste de volumen.
- **RF-09 (Herramientas del Dispositivo):**
  - Encender y apagar la linterna.
  - Programar alarmas y temporizadores con hora y etiqueta.
  - Consultar nivel de batería actual.

### Módulo de Automatización Profunda (AccessibilityService)
- **RF-10 (Navegación Asistida):** Ejecutar acciones globales del sistema: Volver atrás (`GLOBAL_ACTION_BACK`), Pantalla de inicio (`GLOBAL_ACTION_HOME`), Notificaciones y Recientes.
- **RF-11 (Simulación de Acciones en Apps Externas):** Capacidad para buscar nodos en pantalla, enfocar cajas de texto y pulsar botones de envío (ej. enviar mensajes predefinidos en WhatsApp).
- **RF-12 (Lectura de Pantalla):** Capacidad de inspeccionar la jerarquía visual de la app activa para extraer texto ante la orden *"¿Qué hay en pantalla?"*.

---

## 2. Requerimientos No Funcionales (RNF)

- **RNF-01 (Eficiencia de Batería):** El servicio de escucha de Wake-Word no debe sobrepasar un consumo excesivo de batería en segundo plano (usar modelos on-device optimizados como Porcupine o Vosk).
- **RNF-02 (Latencia de Respuesta):** El tiempo entre el fin de la orden de voz del usuario y la ejecución de la acción local no debe superar los 1.5 segundos en tareas directas.
- **RNF-03 (Diseño y Estilo):** Interfaz ultraliviana, sin animaciones pesadas, con Dark Theme nativo estricto siguiendo lineamientos de Material Design 3.
- **RNF-04 (Robustez y Recuperación):** Si el sistema Android detiene el Foreground Service por memoria, el sistema debe reiniciarse mediante `START_STICKY`.
- **RNF-05 (Privacidad de Audio):** El audio escuchado en segundo plano no debe enviarse a servidores externos a menos que se haya disparado el wake-word explícito.