package com.scorpk.assistant.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.service.ScorpkAccessService

object PermissionChecker {

    fun isGranted(context: Context, permission: RequiredPermission): Boolean = when (permission) {
        RequiredPermission.MICROPHONE -> hasRuntime(context, Manifest.permission.RECORD_AUDIO)
        RequiredPermission.CAMERA -> hasRuntime(context, Manifest.permission.CAMERA)
        RequiredPermission.OVERLAY -> Settings.canDrawOverlays(context)
        RequiredPermission.ACCESSIBILITY -> isAccessibilityServiceEnabled(context)
        RequiredPermission.NOTIFICATIONS ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasRuntime(context, Manifest.permission.POST_NOTIFICATIONS)
        RequiredPermission.CALENDAR ->
            hasRuntime(context, Manifest.permission.READ_CALENDAR) &&
                hasRuntime(context, Manifest.permission.WRITE_CALENDAR)
        RequiredPermission.CONTACTS -> hasRuntime(context, Manifest.permission.READ_CONTACTS)
        RequiredPermission.NOTIFICATION_ACCESS ->
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    /** Permisos de runtime asociados, o null si solo se conceden desde Ajustes del sistema. */
    fun runtimePermissionsFor(permission: RequiredPermission): Array<String>? = when (permission) {
        RequiredPermission.MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO)
        RequiredPermission.CAMERA -> arrayOf(Manifest.permission.CAMERA)
        RequiredPermission.CALENDAR -> arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        RequiredPermission.CONTACTS -> arrayOf(Manifest.permission.READ_CONTACTS)
        RequiredPermission.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                null
            }
        RequiredPermission.OVERLAY, RequiredPermission.ACCESSIBILITY, RequiredPermission.NOTIFICATION_ACCESS -> null
    }

    fun settingsIntent(context: Context, permission: RequiredPermission): Intent {
        val intent = when (permission) {
            RequiredPermission.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            RequiredPermission.OVERLAY -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            RequiredPermission.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            RequiredPermission.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            RequiredPermission.MICROPHONE, RequiredPermission.CAMERA, RequiredPermission.CALENDAR,
            RequiredPermission.CONTACTS -> Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun displayName(permission: RequiredPermission): String = when (permission) {
        RequiredPermission.MICROPHONE -> "Micrófono"
        RequiredPermission.CAMERA -> "Cámara (linterna)"
        RequiredPermission.ACCESSIBILITY -> "Servicio de accesibilidad"
        RequiredPermission.OVERLAY -> "Mostrar sobre otras apps"
        RequiredPermission.NOTIFICATIONS -> "Notificaciones"
        RequiredPermission.CALENDAR -> "Calendario"
        RequiredPermission.CONTACTS -> "Contactos"
        RequiredPermission.NOTIFICATION_ACCESS -> "Acceso a notificaciones (control de Spotify)"
    }

    private fun hasRuntime(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (ScorpkAccessService.instance.value != null) return true
        val expected = ComponentName(context, ScorpkAccessService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}
