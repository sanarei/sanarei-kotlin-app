package com.sanarei.sanareimobileapp

import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.romellfudi.ussdlibrary.USSDServiceKT
import com.romellfudi.ussdlibrary.USSDController

/** Runs the library's USSD automation and owns the high-priority visual mask. */
class SanareiUssdAccessibilityService : USSDServiceKT() {
    private lateinit var loadingOverlay: UssdLoadingOverlay
    private val handler = Handler(Looper.getMainLooper())
    private val overlayTimeout = Runnable { cancelSession() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        loadingOverlay = UssdLoadingOverlay(
            this,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            onClose = { hideOverlay() },
            onCancel = { cancelSession() },
            onNavigate = { url -> navigationHandler?.invoke(url) ?: false }
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
        loadingOverlay.showLoading()
        handler.removeCallbacks(overlayTimeout)
        handler.postDelayed(overlayTimeout, OVERLAY_TIMEOUT_MILLIS)
    }

    private fun hideOverlay() {
        handler.removeCallbacks(overlayTimeout)
        if (::loadingOverlay.isInitialized) loadingOverlay.hide()
    }

    private fun showPage(html: String, baseUrl: String?) {
        handler.removeCallbacks(overlayTimeout)
        if (::loadingOverlay.isInitialized) loadingOverlay.showPage(html, baseUrl)
    }

    private fun showError(message: String) {
        handler.removeCallbacks(overlayTimeout)
        if (::loadingOverlay.isInitialized) loadingOverlay.showError(message)
    }

    private fun cancelSession() {
        handler.removeCallbacks(overlayTimeout)
        if (USSDController.isRunning == true) {
            try {
                USSDController.cancel()
            } catch (_: Exception) {
                // Some phone dialogs disappear before their accessibility node can be clicked.
            } finally {
                USSDController.stopRunning()
            }
        }
        cancellationHandler?.invoke()
        hideOverlay()
    }

    companion object {
        private const val OVERLAY_TIMEOUT_MILLIS = 120_000L
        @Volatile
        private var instance: SanareiUssdAccessibilityService? = null
        private var navigationHandler: ((String) -> Boolean)? = null
        private var cancellationHandler: (() -> Unit)? = null

        fun showLoadingOverlay() = instance?.showOverlay()

        fun hideLoadingOverlay() = instance?.hideOverlay()

        fun showPageOverlay(html: String, baseUrl: String?) =
            instance?.showPage(html, baseUrl)

        fun showErrorOverlay(message: String) = instance?.showError(message)

        fun setNavigationHandler(handler: (String) -> Boolean) {
            navigationHandler = handler
        }

        fun clearNavigationHandler() {
            navigationHandler = null
        }

        fun setCancellationHandler(handler: () -> Unit) {
            cancellationHandler = handler
        }

        fun clearCancellationHandler() {
            cancellationHandler = null
        }
    }
}
