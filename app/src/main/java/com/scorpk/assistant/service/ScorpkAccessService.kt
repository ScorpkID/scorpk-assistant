package com.scorpk.assistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Servicio de accesibilidad de Scorpk. Expone primitivas de bajo nivel (lectura del árbol de
 * nodos, clicks, escritura de texto, gestos y acciones globales) que consume AccessibilityAutomator.
 */
class ScorpkAccessService : AccessibilityService() {

    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    override fun onServiceConnected() {
        super.onServiceConnected()
        _instance.value = this
        Log.i(TAG, "Servicio de accesibilidad conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString()
            if (!pkg.isNullOrBlank() && pkg != packageName) {
                _foregroundPackage.value = pkg
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Servicio de accesibilidad interrumpido")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        _instance.value = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _instance.value = null
        super.onDestroy()
    }

    // region Lectura de pantalla

    /** Recorre la ventana activa y devuelve los textos visibles en orden de aparición. */
    fun readScreenText(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val texts = LinkedHashSet<String>()
        traverse(root) { node ->
            if (node.isVisibleToUser) {
                node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
                    ?: node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let(texts::add)
            }
            false
        }
        return texts.toList()
    }

    /** Busca el primer nodo cuyo texto, descripción o id de vista coincida (sin distinguir mayúsculas). */
    fun findNode(query: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return null
        root.findAccessibilityNodeInfosByText(query).firstOrNull { it.isVisibleToUser }?.let { return it }
        var match: AccessibilityNodeInfo? = null
        traverse(root) { node ->
            val text = node.text?.toString()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
            val viewId = node.viewIdResourceName?.lowercase().orEmpty()
            if (node.isVisibleToUser && (needle in text || needle in desc || viewId.endsWith("/$needle"))) {
                match = node
                true
            } else {
                false
            }
        }
        return match
    }

    fun findFirstEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }?.let { return it }
        var match: AccessibilityNodeInfo? = null
        traverse(root) { node ->
            if (node.isEditable && node.isVisibleToUser) {
                match = node
                true
            } else {
                false
            }
        }
        return match
    }

    // endregion

    // region Acciones sobre nodos

    /** Pulsa el nodo o, si no es clickable, su ancestro clickable más cercano. */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }

    fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun nodeBounds(node: AccessibilityNodeInfo): Rect = Rect().also(node::getBoundsInScreen)

    // endregion

    // region Captura de pantalla

    /**
     * Captura la pantalla actual (Android 11+). Devuelve null si el sistema la rechaza
     * (p. ej. apps con FLAG_SECURE como las bancarias, que salen en negro o se bloquean).
     */
    suspend fun captureScreen(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { cont ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                        buffer.close()
                        if (cont.isActive) cont.resume(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "takeScreenshot falló: $errorCode")
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
        }
    }

    // endregion

    // region Gestos y acciones globales

    fun performGlobal(action: Int): Boolean = performGlobalAction(action)

    suspend fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
    }

    suspend fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = SWIPE_DURATION_MS): Boolean {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        return dispatch(GestureDescription.StrokeDescription(path, 0, durationMs))
    }

    private suspend fun dispatch(stroke: GestureDescription.StrokeDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val accepted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null
            )
            if (!accepted && cont.isActive) cont.resume(false)
        }

    // endregion

    private fun traverse(root: AccessibilityNodeInfo, visitor: (AccessibilityNodeInfo) -> Boolean) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            val node = stack.removeLast()
            visited++
            if (visitor(node)) return
            for (i in node.childCount - 1 downTo 0) {
                node.getChild(i)?.let(stack::addLast)
            }
        }
    }

    companion object {
        private const val TAG = "ScorpkAssistant"
        private const val TAP_DURATION_MS = 50L
        private const val SWIPE_DURATION_MS = 300L
        private const val MAX_NODES = 2000

        private val _instance = MutableStateFlow<ScorpkAccessService?>(null)

        /** Instancia activa del servicio, o null si el usuario no lo ha habilitado. */
        val instance: StateFlow<ScorpkAccessService?> = _instance.asStateFlow()
    }
}
