package com.sanarei.sanareimobileapp

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat

/** An interactive browser surface shown above the system USSD UI. */
class UssdLoadingOverlay(
    private val context: Context,
    private val windowType: Int,
    private val onClose: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var loadingView: View? = null
    private var browserView: View? = null
    private var webView: WebView? = null

    fun showLoading() = onMainThread {
        ensureAttached()
        browserView?.visibility = View.GONE
        loadingView?.visibility = View.VISIBLE
    }

    fun showPage(html: String, baseUrl: String?) = onMainThread {
        ensureAttached()
        loadingView?.visibility = View.GONE
        browserView?.visibility = View.VISIBLE
        webView?.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
    }

    fun hide() = onMainThread {
        webView?.stopLoading()
        webView?.destroy()
        webView = null
        overlayView?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: IllegalArgumentException) {
                // It was already detached by the system.
            }
        }
        overlayView = null
        loadingView = null
        browserView = null
    }

    private fun ensureAttached() {
        if (overlayView != null) return

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.WHITE)
        }
        loadingView = createLoadingView().also(root::addView)
        browserView = createBrowserView().also {
            it.visibility = View.GONE
            root.addView(it)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )

        try {
            windowManager.addView(root, params)
            overlayView = root
        } catch (_: SecurityException) {
            clearDetachedViews()
        } catch (_: WindowManager.BadTokenException) {
            clearDetachedViews()
        }
    }

    private fun createLoadingView(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(32), dp(32), dp(32), dp(32))
        setBackgroundColor(Color.rgb(250, 250, 250))

        addView(ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_logo))
            contentDescription = context.getString(R.string.app_name)
        }, LinearLayout.LayoutParams(dp(96), dp(96)).apply {
            bottomMargin = dp(28)
        })

        addView(ProgressBar(context), LinearLayout.LayoutParams(dp(48), dp(48)).apply {
            bottomMargin = dp(24)
        })

        addView(TextView(context).apply {
            text = context.getString(R.string.ussd_loading_message)
            textSize = 18f
            setTextColor(Color.rgb(35, 35, 35))
            gravity = Gravity.CENTER
        })
    }

    private fun createBrowserView(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)

        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(245, 245, 245))

            addView(TextView(context).apply {
                text = context.getString(R.string.overlay_browser_title)
                textSize = 18f
                setTextColor(Color.rgb(25, 25, 25))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(Button(context).apply {
                text = context.getString(R.string.overlay_refresh)
                setOnClickListener { webView?.reload() }
            })
            addView(Button(context).apply {
                text = context.getString(R.string.overlay_close)
                setOnClickListener { onClose() }
            })
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        webView = WebView(context).apply {
            setBackgroundColor(Color.WHITE)
            webViewClient = WebViewClient()
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.domStorageEnabled = true
        }
        addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
    }

    private fun clearDetachedViews() {
        webView?.destroy()
        webView = null
        overlayView = null
        loadingView = null
        browserView = null
    }

    private fun onMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
