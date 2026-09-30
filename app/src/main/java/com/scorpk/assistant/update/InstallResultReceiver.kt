package com.scorpk.assistant.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Resultado que el instalador del sistema devuelve tras confirmar (o rechazar) la instalación. */
data class InstallOutcome(val status: Int, val message: String?)

object InstallEvents {
    private val _outcomes = MutableSharedFlow<InstallOutcome>(extraBufferCapacity = 4)
    val outcomes: SharedFlow<InstallOutcome> = _outcomes.asSharedFlow()

    fun post(outcome: InstallOutcome) {
        _outcomes.tryEmit(outcome)
    }
}

/**
 * Recibe el estado de la sesión de PackageInstaller. Android pide al usuario confirmar la
 * instalación: aquí se abre ese diálogo del sistema; si falla, se avisa a [UpdateManager].
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmation == null) {
                    InstallEvents.post(InstallOutcome(PackageInstaller.STATUS_FAILURE, "sin diálogo de confirmación"))
                    return
                }
                try {
                    context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo abrir la confirmación de instalación", e)
                    InstallEvents.post(InstallOutcome(PackageInstaller.STATUS_FAILURE, e.message))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "Actualización instalada")
            else -> InstallEvents.post(InstallOutcome(status, message))
        }
    }

    private companion object {
        const val TAG = "ScorpkAssistant"
    }
}
