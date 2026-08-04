package com.sanarei.sanareimobileapp

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat

/** A non-interactive mask shown above the system USSD UI while it is automated. */
class UssdLoadingOverlay(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null

    fun show() = onMainThread {
        if (overlayView != null) return@onMainThread

        val content = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(32), dp(32), dp(32))
            setBackgroundColor(Color.rgb(250, 250, 250))

            addView(ImageView(appContext).apply {
                setImageDrawable(ContextCompat.getDrawable(appContext, R.drawable.ic_logo))
                contentDescription = appContext.getString(R.string.app_name)
            }, LinearLayout.LayoutParams(dp(96), dp(96)).apply {
                bottomMargin = dp(28)
            })

            addView(ProgressBar(appContext), LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                bottomMargin = dp(24)
            })

            addView(TextView(appContext).apply {
                text = appContext.getString(R.string.ussd_loading_message)
                textSize = 18f
                setTextColor(Color.rgb(35, 35, 35))
                gravity = Gravity.CENTER
            })
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        )

        try {
            windowManager.addView(content, params)
            overlayView = content
        } catch (_: SecurityException) {
            // The caller checks overlay permission; keep USSD functional if it was revoked.
        } catch (_: WindowManager.BadTokenException) {
            // Do not make a failed visual mask break the USSD session.
        }
    }

    fun hide() = onMainThread {
        overlayView?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: IllegalArgumentException) {
                // It was already detached by the system.
            }
        }
        overlayView = null
    }

    private fun onMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).toInt()
}
