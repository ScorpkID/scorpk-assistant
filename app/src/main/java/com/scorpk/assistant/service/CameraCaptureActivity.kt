package com.scorpk.assistant.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

/** Canal entre la actividad de cámara y el overlay (que es un servicio y no recibe resultados). */
object CameraCaptureBus {
    private val _results = MutableSharedFlow<Uri?>(extraBufferCapacity = 1)
    val results: SharedFlow<Uri?> = _results.asSharedFlow()

    fun post(uri: Uri?) {
        _results.tryEmit(uri)
    }
}

/**
 * Actividad transparente que abre la cámara del sistema y devuelve la foto al asistente
 * flotante mediante [CameraCaptureBus]. Pide el permiso de cámara si hace falta.
 */
class CameraCaptureActivity : ComponentActivity() {

    private lateinit var photoUri: Uri
    private var delivered = false

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        deliver(if (saved) photoUri else null)
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePicture.launch(photoUri) else deliver(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "scorpk_${System.currentTimeMillis()}.jpg")
        photoUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        if (savedInstanceState != null) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) takePicture.launch(photoUri) else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    override fun onDestroy() {
        if (!delivered && isFinishing) CameraCaptureBus.post(null)
        super.onDestroy()
    }

    private fun deliver(uri: Uri?) {
        delivered = true
        CameraCaptureBus.post(uri)
        finish()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(
                Intent(context, CameraCaptureActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        }
    }
}
