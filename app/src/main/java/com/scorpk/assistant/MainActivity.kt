package com.scorpk.assistant

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.scorpk.assistant.data.local.AssistantSettings
import com.scorpk.assistant.domain.model.RequiredPermission
import com.scorpk.assistant.service.WakeWordForegroundService
import com.scorpk.assistant.ui.MainScreen
import com.scorpk.assistant.ui.theme.ScorpkTheme
import com.scorpk.assistant.util.PermissionChecker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val settings by appContainer.settings.settings.collectAsStateWithLifecycle(AssistantSettings())
            ScorpkTheme(themeMode = settings.themeMode) {
                MainScreen()
            }
        }
        appContainer.authRepository.handleDeeplink(intent)
        restoreWakeWordService()
    }

    override fun onStart() {
        super.onStart()
        // Cada vez que la app vuelve al primer plano (con un intervalo mínimo entre comprobaciones).
        appContainer.updateManager.check()
    }

    /** Retorno del login OAuth (Google / GitHub): scorpk://auth-callback. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appContainer.authRepository.handleDeeplink(intent)
    }

    /** Reanuda la escucha de "Hey Scorpk" si el usuario la dejó activada. */
    private fun restoreWakeWordService() {
        lifecycleScope.launch {
            val enabled = appContainer.settings.settings.first().wakeWordEnabled
            if (enabled &&
                !WakeWordForegroundService.isRunning.value &&
                appContainer.wakeWordModelManager.isReady &&
                PermissionChecker.isGranted(this@MainActivity, RequiredPermission.MICROPHONE)
            ) {
                WakeWordForegroundService.start(this@MainActivity)
            }
        }
    }
}
