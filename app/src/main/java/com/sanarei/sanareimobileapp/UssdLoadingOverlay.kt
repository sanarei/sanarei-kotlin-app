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
    private val onClose: () -> Unit,
    private val onNavigate: (String) -> Boolean
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var loadingView: View? = null
    private var browserView: View? = null
    private var webView: WebView? = null
    private var backButton: Button? = null
    private var forwardButton: Button? = null
    private var addressView: TextView? = null
    private var flashView: TextView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private val pageCache = mutableMapOf<String, String>()
    private val history = mutableListOf<String>()
    private var historyIndex = -1
    private var currentUrl: String? = null
    private val dismissFlash = Runnable { flashView?.visibility = View.GONE }

    fun showLoading() = onMainThread {
        ensureAttached()
        setPageFocusable(false)
        browserView?.visibility = View.GONE
        loadingView?.visibility = View.VISIBLE
    }

    fun showPage(html: String, baseUrl: String?) = onMainThread {
        ensureAttached()
        val normalizedUrl = baseUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedUrl != null) {
            pageCache[normalizedUrl] = html
            if (history.getOrNull(historyIndex) != normalizedUrl) {
                if (historyIndex < history.lastIndex) {
                    history.subList(historyIndex + 1, history.size).clear()
                }
                history.add(normalizedUrl)
                historyIndex = history.lastIndex
            }
            currentUrl = normalizedUrl
        }
        loadingView?.visibility = View.GONE
        browserView?.visibility = View.VISIBLE
        setPageFocusable(true)
        renderPage(normalizedUrl, html)
    }

    fun showError(message: String) = onMainThread {
        if (currentUrl == null || webView == null) {
            hide()
            return@onMainThread
        }

        loadingView?.visibility = View.GONE
        browserView?.visibility = View.VISIBLE
        setPageFocusable(true)
        flashView?.apply {
            text = message.toFlashMessage()
            visibility = View.VISIBLE
        }
        mainHandler.removeCallbacks(dismissFlash)
        mainHandler.postDelayed(dismissFlash, FLASH_DURATION_MILLIS)
    }

    fun hide() = onMainThread {
        mainHandler.removeCallbacks(dismissFlash)
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
        backButton = null
        forwardButton = null
        addressView = null
        flashView = null
        pageCache.clear()
        history.clear()
        historyIndex = -1
        currentUrl = null
        windowParams = null
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
            windowParams = params
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
                text = context.getString(R.string.overlay_close)
                setOnClickListener { onClose() }
            })
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))

            backButton = Button(context).apply {
                text = context.getString(R.string.overlay_back)
                contentDescription = context.getString(R.string.overlay_back_description)
                setOnClickListener { moveInHistory(-1) }
            }
            addView(backButton)

            forwardButton = Button(context).apply {
                text = context.getString(R.string.overlay_forward)
                contentDescription = context.getString(R.string.overlay_forward_description)
                setOnClickListener { moveInHistory(1) }
            }
            addView(forwardButton)

            addView(Button(context).apply {
                text = context.getString(R.string.overlay_refresh)
                setOnClickListener { currentUrl?.let(::requestPage) }
            })

            addressView = TextView(context).apply {
                textSize = 12f
                maxLines = 1
                setTextColor(Color.DKGRAY)
                setPadding(dp(8), 0, 0, 0)
            }
            addView(addressView, LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        flashView = TextView(context).apply {
            visibility = View.GONE
            setBackgroundColor(Color.rgb(183, 28, 28))
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        addView(flashView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        webView = WebView(context).apply {
            setBackgroundColor(Color.WHITE)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                    interceptNavigation(url)

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ): Boolean = interceptNavigation(request.url.toString())
            }
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
        backButton = null
        forwardButton = null
        addressView = null
        flashView = null
        windowParams = null
    }

    private fun interceptNavigation(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (url.substringBefore('#') == currentUrl?.substringBefore('#') && url.contains('#')) {
            return false
        }

        pageCache[url]?.let { html ->
            if (historyIndex < history.lastIndex) {
                history.subList(historyIndex + 1, history.size).clear()
            }
            history.add(url)
            historyIndex = history.lastIndex
            currentUrl = url
            renderPage(url, html)
        } ?: requestPage(url)
        return true
    }

    private fun requestPage(url: String) {
        if (onNavigate(url)) {
            loadingView?.visibility = View.VISIBLE
            browserView?.visibility = View.GONE
        }
    }

    private fun moveInHistory(offset: Int) {
        val targetIndex = historyIndex + offset
        val url = history.getOrNull(targetIndex) ?: return
        val html = pageCache[url] ?: return
        historyIndex = targetIndex
        currentUrl = url
        renderPage(url, html)
    }

    private fun renderPage(url: String?, html: String) {
        addressView?.text = url.orEmpty()
        webView?.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
        updateNavigationButtons()
    }

    private fun updateNavigationButtons() {
        backButton?.isEnabled = historyIndex > 0
        forwardButton?.isEnabled = historyIndex in 0 until history.lastIndex
    }

    private fun setPageFocusable(focusable: Boolean) {
        val view = overlayView ?: return
        val params = windowParams ?: return
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: IllegalArgumentException) {
            // The accessibility window was detached while its state was changing.
        }
    }

    private fun String.toFlashMessage(): String =
        removePrefix("[")
            .removeSuffix("]")
            .substringBeforeLast(", OK")
            .trim()

    private fun onMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val FLASH_DURATION_MILLIS = 6_000L
    }
}
