package com.scorpk.assistant.update

/** Estado del flujo de actualización que observa la interfaz. */
sealed interface UpdateState {
    /** Nada que mostrar (al día, sin comprobar, o el usuario pospuso una opcional). */
    data object Idle : UpdateState

    /** Hay una versión nueva. [mandatory]: la versión instalada ya no está soportada. */
    data class Available(val info: UpdateInfo, val mandatory: Boolean) : UpdateState

    data class Downloading(val info: UpdateInfo, val mandatory: Boolean, val progress: Float) : UpdateState

    /** Falta el permiso de Android «instalar apps desconocidas» para Scorpk. */
    data class NeedsInstallPermission(val info: UpdateInfo, val mandatory: Boolean) : UpdateState

    /** El APK ya está verificado y Android muestra su diálogo de instalación. */
    data class Installing(val info: UpdateInfo, val mandatory: Boolean) : UpdateState

    data class Failed(val message: String, val info: UpdateInfo, val mandatory: Boolean) : UpdateState
}

/** Resultado de una comprobación manual (Configuración → Buscar actualizaciones). */
sealed interface CheckResult {
    data object UpToDate : CheckResult
    data class Available(val info: UpdateInfo) : CheckResult
    data class Failed(val message: String) : CheckResult
}
