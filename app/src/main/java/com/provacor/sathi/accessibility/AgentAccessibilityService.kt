package com.provacor.sathi.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Sathi's screen access. Turned on by the user in Settings → Accessibility, and
 * visible there at all times. It only reads the screen when the agent runs a
 * step; events are used to notice changes and the app in front.
 */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null) AccessibilityBridge.onEvent(event)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityBridge.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityBridge.detach(this)
        super.onDestroy()
    }
}
