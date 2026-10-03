package com.provacor.sathi.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.provacor.sathi.agent.ScreenController
import com.provacor.sathi.core.model.Bounds
import com.provacor.sathi.core.model.Direction
import com.provacor.sathi.core.model.ScreenState
import com.provacor.sathi.core.model.UiElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * [ScreenController] backed by [AgentAccessibilityService].
 *
 * Text is never read from password fields. Nodes from the last [observe] are
 * kept only so the next action can press the element the agent picked.
 */
object AccessibilityBridge : ScreenController {

    @Volatile private var service: AccessibilityService? = null
    @Volatile private var lastNodes: List<AccessibilityNodeInfo> = emptyList()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val changes = MutableStateFlow(0L)

    override val available: Boolean get() = service != null

    internal fun attach(s: AccessibilityService) {
        service = s
        _connected.value = true
    }

    internal fun detach(s: AccessibilityService) {
        if (service === s) {
            service = null
            lastNodes = emptyList()
            _connected.value = false
        }
    }

    internal fun onEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> changes.value = changes.value + 1
        }
    }

    /** True when the service is switched on in system settings, even before it binds. */
    fun isEnabledInSettings(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, AgentAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    // ---- observe ---------------------------------------------------------------

    override suspend fun observe(): ScreenState? = withContext(Dispatchers.Default) {
        val s = service ?: return@withContext null
        val root = s.rootInActiveWindow ?: return@withContext null
        val nodes = ArrayList<AccessibilityNodeInfo>()
        val elements = ArrayList<UiElement>()
        collect(root, clickableAncestor = null, depth = 0, nodes, elements)
        lastNodes = nodes
        val metrics = s.resources.displayMetrics
        ScreenState(root.packageName?.toString(), elements, metrics.widthPixels, metrics.heightPixels)
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        clickableAncestor: Int?,
        depth: Int,
        nodes: MutableList<AccessibilityNodeInfo>,
        out: MutableList<UiElement>,
    ) {
        if (out.size >= MAX_NODES || depth > MAX_DEPTH) return
        var ancestor = clickableAncestor
        if (node.isVisibleToUser) {
            val index = out.size
            val rect = Rect().also(node::getBoundsInScreen)
            val clickable = node.isClickable || node.isLongClickable
            out += UiElement(
                index = index,
                text = if (node.isPassword) null else node.text?.toString()?.take(MAX_TEXT),
                description = node.contentDescription?.toString()?.take(MAX_TEXT),
                viewId = node.viewIdResourceName,
                className = node.className?.toString(),
                clickable = clickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                focused = node.isFocused,
                bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                clickTarget = if (clickable) index else clickableAncestor,
            )
            nodes += node
            if (clickable) ancestor = index
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, ancestor, depth + 1, nodes, out)
        }
    }

    // ---- waiting ---------------------------------------------------------------

    override fun changeCount(): Long = changes.value

    override suspend fun awaitChange(since: Long, timeoutMillis: Long): ScreenState? {
        val changed = withTimeoutOrNull(timeoutMillis) { changes.first { it > since } } != null
        if (!changed) return null
        // Let animations and list loads settle before reading the screen.
        delay(SETTLE_MILLIS)
        return observe()
    }

    override suspend fun awaitForeground(packageName: String, timeoutMillis: Long): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            while (service?.rootInActiveWindow?.packageName?.toString() != packageName) delay(POLL_MILLIS)
            true
        } ?: false

    // ---- actions ---------------------------------------------------------------

    private fun node(e: UiElement): AccessibilityNodeInfo? = lastNodes.getOrNull(e.index)

    override suspend fun click(element: UiElement): Boolean {
        val n = node(element)
        if (n != null && n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        // Fallback: a tap gesture at the element's centre.
        return tap(element.bounds.centerX.toFloat(), element.bounds.centerY.toFloat())
    }

    override suspend fun setText(element: UiElement, text: String): Boolean {
        val n = node(element) ?: return false
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override suspend fun submit(element: UiElement): Boolean {
        val n = node(element) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return n.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
        return false
    }

    override suspend fun scroll(direction: Direction): Boolean {
        observe() ?: return false
        val target = lastNodes.filter { it.isScrollable && it.isVisibleToUser }.maxByOrNull {
            val r = Rect().also(it::getBoundsInScreen); r.width().toLong() * r.height()
        }
        val action = if (direction == Direction.DOWN) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (target != null && target.performAction(action)) return true
        // Fallback: a swipe in the middle of the screen.
        val s = service ?: return false
        val m = s.resources.displayMetrics
        val x = m.widthPixels / 2f
        val (from, to) = if (direction == Direction.DOWN) m.heightPixels * 0.7f to m.heightPixels * 0.3f else m.heightPixels * 0.3f to m.heightPixels * 0.7f
        return swipe(x, from, x, to)
    }

    override fun back(): Boolean = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) ?: false

    override fun home(): Boolean = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) ?: false

    private suspend fun tap(x: Float, y: Float): Boolean = gesture(Path().apply { moveTo(x, y) }, 60)

    private suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean =
        gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, 300)

    private suspend fun gesture(path: Path, durationMillis: Long): Boolean {
        val s = service ?: return false
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMillis)).build()
        return suspendCancellableCoroutine { cont ->
            val dispatched = s.dispatchGesture(
                g,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null,
            )
            if (!dispatched && cont.isActive) cont.resume(false)
        }
    }

    private const val MAX_NODES = 600
    private const val MAX_DEPTH = 40
    private const val MAX_TEXT = 500
    private const val SETTLE_MILLIS = 600L
    private const val POLL_MILLIS = 250L
}
