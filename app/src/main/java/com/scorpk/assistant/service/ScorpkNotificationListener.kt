package com.scorpk.assistant.service

import android.service.notification.NotificationListenerService

/**
 * Listener vacío: su única función es que Android conceda a Scorpk acceso a
 * MediaSessionManager.getActiveSessions() para controlar Spotify directamente.
 * No lee, guarda ni envía el contenido de ninguna notificación.
 */
class ScorpkNotificationListener : NotificationListenerService()
