package com.sanarei.sanareimobileapp

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
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
class UssdLoadingOverlay(
    private val context: Context,
    private val windowType: Int
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null

    fun show() = onMainThread {
        if (overlayView != null) return@onMainThread

        val content = LinearLayout(context).apply {
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
        (value * context.resources.displayMetrics.density).toInt()
}
