package com.sanarei.sanareimobileapp

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class UssdAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val source = event?.source ?: return

        val text = source.text?.toString() ?: "" ?: ""
        if (text.isNotEmpty()) {
            Log.d("USSD_RESPONSE", "USSD Text: $text")
            // You could broadcast this back to your app or show it in a UI
        }
    }

    override fun onInterrupt() {}
}
