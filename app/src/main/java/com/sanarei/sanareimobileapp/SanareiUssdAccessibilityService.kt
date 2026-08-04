package com.sanarei.sanareimobileapp

import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.romellfudi.ussdlibrary.USSDServiceKT

/** Runs the library's USSD automation and owns the high-priority visual mask. */
class SanareiUssdAccessibilityService : USSDServiceKT() {
    private lateinit var loadingOverlay: UssdLoadingOverlay
    private val handler = Handler(Looper.getMainLooper())
    private val overlayTimeout = Runnable { hideOverlay() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        loadingOverlay = UssdLoadingOverlay(
            this,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        )
        instance = this
    }

    override fun onDestroy() {
        hideOverlay()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun showOverlay() {
        if (!::loadingOverlay.isInitialized) return
        loadingOverlay.show()
        handler.removeCallbacks(overlayTimeout)
        handler.postDelayed(overlayTimeout, OVERLAY_TIMEOUT_MILLIS)
    }

    private fun hideOverlay() {
        handler.removeCallbacks(overlayTimeout)
        if (::loadingOverlay.isInitialized) loadingOverlay.hide()
    }

    companion object {
        private const val OVERLAY_TIMEOUT_MILLIS = 120_000L
        @Volatile
        private var instance: SanareiUssdAccessibilityService? = null

        fun showLoadingOverlay() = instance?.showOverlay()

        fun hideLoadingOverlay() = instance?.hideOverlay()
    }
}
