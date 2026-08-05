package com.sanarei.sanareimobileapp

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
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
    private var pageProgress: ProgressBar? = null
    private var cacheView: TextView? = null
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
        pageProgress = null
        cacheView = null
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

    private fun createLoadingView(): View = FrameLayout(context).apply {
        setBackgroundColor(BROWSER_BACKGROUND)

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(34), dp(36), dp(34), dp(34))
            background = roundedBackground(Color.WHITE, 28)
            elevation = dp(8).toFloat()

            addView(ImageView(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_logo))
                contentDescription = context.getString(R.string.app_name)
            }, LinearLayout.LayoutParams(dp(78), dp(78)).apply {
                bottomMargin = dp(22)
            })

            addView(TextView(context).apply {
                text = context.getString(R.string.overlay_loading_title)
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(TEXT_PRIMARY)
                gravity = Gravity.CENTER
            })

            addView(TextView(context).apply {
                text = context.getString(R.string.ussd_loading_message)
                textSize = 14f
                setTextColor(TEXT_SECONDARY)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(24))
            })

            addView(ProgressBar(
                context,
                null,
                android.R.attr.progressBarStyleHorizontal
            ).apply {
                isIndeterminate = true
                progressTintList = android.content.res.ColorStateList.valueOf(BRAND_BLUE)
                indeterminateTintList = android.content.res.ColorStateList.valueOf(BRAND_BLUE)
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(4)
            ))

            addView(TextView(context).apply {
                text = context.getString(R.string.overlay_loading_hint)
                textSize = 12f
                setTextColor(TEXT_MUTED)
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, 0)
            })
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply {
            leftMargin = dp(28)
            rightMargin = dp(28)
        })
    }

    private fun createBrowserView(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(16), dp(14), dp(12))
        setBackgroundColor(BROWSER_BACKGROUND)

        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL

            addView(FrameLayout(context).apply {
                background = roundedBackground(Color.WHITE, 13)
                elevation = dp(2).toFloat()
                addView(ImageView(context).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_logo))
                    contentDescription = context.getString(R.string.app_name)
                    setPadding(dp(7), dp(7), dp(7), dp(7))
                })
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                rightMargin = dp(11)
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = context.getString(R.string.overlay_browser_title)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(TEXT_PRIMARY)
                })
                addView(TextView(context).apply {
                    text = context.getString(R.string.overlay_browser_subtitle)
                    textSize = 11f
                    setTextColor(TEXT_MUTED)
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            addView(browserButton(
                label = "×",
                description = context.getString(R.string.overlay_close),
                emphasized = true,
                onClick = onClose
            ), LinearLayout.LayoutParams(dp(44), dp(44)))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(14)
        })

        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL

            backButton = browserButton(
                label = context.getString(R.string.overlay_back),
                description = context.getString(R.string.overlay_back_description),
                onClick = { moveInHistory(-1) }
            )
            addView(backButton, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                rightMargin = dp(7)
            })

            forwardButton = browserButton(
                label = context.getString(R.string.overlay_forward),
                description = context.getString(R.string.overlay_forward_description),
                onClick = { moveInHistory(1) }
            )
            addView(forwardButton, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                rightMargin = dp(7)
            })

            addView(browserButton(
                label = "↻",
                description = context.getString(R.string.overlay_refresh),
                onClick = { currentUrl?.let(::requestPage) }
            ), LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                rightMargin = dp(9)
            })

            addressView = TextView(context).apply {
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MIDDLE
                setTextColor(TEXT_SECONDARY)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), 0, dp(13), 0)
                background = roundedBackground(Color.WHITE, 14, BORDER_COLOR)
            }
            addView(addressView, LinearLayout.LayoutParams(
                0,
                dp(44),
                1f
            ))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(10)
        })

        pageProgress = ProgressBar(
            context,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(BRAND_BLUE)
        }
        addView(pageProgress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(3)
        ))

        flashView = TextView(context).apply {
            visibility = View.GONE
            background = roundedBackground(ERROR_BACKGROUND, 14)
            setTextColor(ERROR_TEXT)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        addView(flashView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(8)
            bottomMargin = dp(8)
        })

        addView(FrameLayout(context).apply {
            background = bottomRoundedBackground(Color.WHITE, 20, BORDER_COLOR)
            clipToOutline = true
            elevation = dp(2).toFloat()

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
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        pageProgress?.progress = newProgress
                        pageProgress?.visibility =
                            if (newProgress in 0..99) View.VISIBLE else View.GONE
                    }
                }
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.textZoom = 260
            }
            addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        cacheView = TextView(context).apply {
            textSize = 11f
            setTextColor(TEXT_MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(9), 0, 0)
        }
        addView(cacheView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
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
        pageProgress = null
        cacheView = null
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
        addressView?.text = url.toBrowserAddress()
        cacheView?.text = context.resources.getQuantityString(
            R.plurals.overlay_cached_pages,
            pageCache.size,
            pageCache.size
        )
        webView?.loadDataWithBaseURL(url, html, "text/html", "UTF-8", null)
        updateNavigationButtons()
    }

    private fun updateNavigationButtons() {
        backButton?.isEnabled = historyIndex > 0
        forwardButton?.isEnabled = historyIndex in 0 until history.lastIndex
        backButton?.alpha = if (backButton?.isEnabled == true) 1f else 0.35f
        forwardButton?.alpha = if (forwardButton?.isEnabled == true) 1f else 0.35f
    }

    private fun browserButton(
        label: String,
        description: String,
        emphasized: Boolean = false,
        onClick: () -> Unit
    ): Button = Button(context).apply {
        text = label
        contentDescription = description
        isAllCaps = false
        textSize = if (label.length == 1) 25f else 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (emphasized) Color.WHITE else TEXT_PRIMARY)
        minWidth = 0
        minHeight = 0
        minimumWidth = 0
        minimumHeight = 0
        setPadding(0, 0, 0, dp(2))
        background = roundedBackground(
            if (emphasized) BRAND_BLUE else Color.WHITE,
            14,
            if (emphasized) null else BORDER_COLOR
        )
        elevation = dp(if (emphasized) 2 else 0).toFloat()
        setOnClickListener { onClick() }
    }

    private fun roundedBackground(
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }

    private fun bottomRoundedBackground(
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null
    ): GradientDrawable = GradientDrawable().apply {
        val radius = dp(radiusDp).toFloat()
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadii = floatArrayOf(
            0f, 0f,
            0f, 0f,
            radius, radius,
            radius, radius
        )
        strokeColor?.let { setStroke(dp(1), it) }
    }

    private fun String?.toBrowserAddress(): String {
        if (this.isNullOrBlank()) return context.getString(R.string.overlay_no_address)
        return try {
            val uri = Uri.parse(this)
            val location = buildString {
                append(uri.host ?: this@toBrowserAddress)
                val path = uri.path.orEmpty()
                if (path.isNotBlank() && path != "/") append(path)
            }
            context.getString(R.string.overlay_secure_address, location)
        } catch (_: RuntimeException) {
            this
        }
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
        private val BROWSER_BACKGROUND = Color.rgb(244, 247, 252)
        private val BRAND_BLUE = Color.rgb(67, 116, 205)
        private val TEXT_PRIMARY = Color.rgb(24, 36, 58)
        private val TEXT_SECONDARY = Color.rgb(70, 83, 107)
        private val TEXT_MUTED = Color.rgb(112, 126, 150)
        private val BORDER_COLOR = Color.rgb(221, 227, 237)
        private val ERROR_BACKGROUND = Color.rgb(255, 235, 238)
        private val ERROR_TEXT = Color.rgb(153, 27, 27)
    }
}
