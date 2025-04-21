package com.sanarei.sanareimobileapp

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class USSDInterceptorService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {

            val className = event.className?.toString()
            if (className?.contains("AlertDialog") == true || className?.contains("Ussd") == true) {
                val node = event.source ?: return
                val dismissButton =
                    findNodeWithText(node, "OK") ?: findNodeWithText(node, "Dismiss")
                dismissButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
    }

    private fun findNodeWithText(
        node: AccessibilityNodeInfo?,
        text: String,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) return node

        for (i in 0 until node.childCount) {
            val child = findNodeWithText(node.getChild(i), text)
            if (child != null) return child
        }

        return null
    }

    override fun onInterrupt() {}
}
