package com.sanarei.sanareimobileapp

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class USSDInterceptorService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val className = event.className?.toString()
        val packageName = event.packageName?.toString()
        val eventType = event.eventType

        Log.d("USSDInterceptor", "Event: $eventType | Class: $className " +
                "| Package: $packageName")

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {

            val nodeInfo = event.source ?: return

            // Try to detect common USSD dialog patterns
            if (isUssdDialog(className)) {
                Log.d("USSDInterceptor", "Potential USSD dialog detected")

                val okNode = findNodeWithText(nodeInfo, "OK")
                    ?: findNodeWithText(nodeInfo, "Dismiss")
                    ?: findNodeWithText(nodeInfo, "CLOSE")
                    ?: findNodeWithText(nodeInfo, "Cancel")

                if (okNode != null) {
                    Log.d("USSDInterceptor", "Dismissing USSD popup")
                    okNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    Log.d("USSDInterceptor", "Dismiss button not found")
                }
            }


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

    private fun isUssdDialog(className: String?): Boolean {
        if (className == null) return false
        return listOf(
            "com.android.phone",                   // AOSP
            "com.android.server.telecom",          // AOSP
            "com.samsung.android.incallui",        // Samsung
            "android.app.AlertDialog",             // Generic dialogs
            "com.android.internal.app.AlertDialogActivity"
        ).any { className.contains(it, ignoreCase = true) }
    }

    override fun onServiceConnected() {
        Log.d("USSDInterceptor", "Accessibility Service is connected")
    }
}
