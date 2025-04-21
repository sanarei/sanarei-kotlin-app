package com.sanarei.sanareimobileapp

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class UssdAccessibilityService : AccessibilityService() {

    private val TAG = "USSD_HACK"

    private val responseBuffer = mutableListOf<String>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.text.isNullOrEmpty()) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString()
            Log.d("USSD_HACK", "Window changed: $className")

            if (className?.contains("AlertDialog", ignoreCase = true) == true ||
                className?.contains("ussd", ignoreCase = true) == true
            ) {
                Handler(Looper.getMainLooper()).postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    Log.d("USSD_HACK", "Back action triggered for class: $className")
                }, 2000)
            }
        }
    }


    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted.")
    }

    override fun onServiceConnected() {
        Log.d(TAG, "USSD Accessibility Service connected.")
    }

    fun getResponses(): List<String> = responseBuffer
}
