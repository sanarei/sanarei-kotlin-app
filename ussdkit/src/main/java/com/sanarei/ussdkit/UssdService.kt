package com.sanarei.ussdkit

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean

class UssdService : Service() {
    companion object {
        const val CHANNEL_ID = "UssdServiceChannel"
        const val NOTIFICATION_ID = 1
        const val USSD_REQUEST = "ussd_request"
        const val ACTION_RESULT = "com.sanarei.ussdkit.RESULT"
    }

    private val isRunning = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @RequiresPermission(Manifest.permission.CALL_PHONE)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(USSD_REQUEST, UssdRequest::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(USSD_REQUEST)
        } ?: return START_NOT_STICKY

        if (isRunning.get()) {
            broadcastResult(UssdResult.Failure("Another USSD session is in progress"))
            return START_NOT_STICKY
        }

        isRunning.set(true)

        // Start foreground service with a notification
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USSD Service")
            .setContentText("Processing USSD request...")
            .setSmallIcon(com.google.android.material.R.drawable.abc_ic_star_black_16dp)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Execute USSD code
        executeUssd(request)

        return START_STICKY
    }

    @RequiresPermission(Manifest.permission.CALL_PHONE)
    private fun executeUssd(request: UssdRequest) {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            telephonyManager.sendUssdRequest(
                request.ussdCode,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        req: String,
                        response: CharSequence
                    ) {
                        handleUssdResponse(request, response.toString())
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        req: String,
                        failureCode: Int
                    ) {
                        broadcastResult(UssdResult.Failure("USSD Failed: $failureCode"))
                        stopSelf()
                    }
                },
                Handler(mainLooper)
            )
        } else {
            broadcastResult(UssdResult.Failure("USSD not supported on this Android version"))
            stopSelf()
        }
    }

    private fun handleUssdResponse(request: UssdRequest, response: String) {
        // Check if the response matches any step's prompt pattern
        val matchingStep = request.steps.find { step ->
            response.contains(Regex(step.promptPattern))
        }

        if (matchingStep != null) {
            // Simulate sending the response (requires AccessibilityService in a real app)
            // For now, log the action and broadcast an intermediate result
            broadcastResult(UssdResult.Intermediate("Matched prompt: ${matchingStep.promptPattern}, sending: ${matchingStep.response}"))
            // In a real implementation, use AccessibilityService to automate the response
            // For simplicity, we'll assume the session ends here
            stopSelf()
        } else {
            // No matching step, assume the session is complete
            broadcastResult(UssdResult.Success(response))
            stopSelf()
        }
    }

    private fun broadcastResult(result: UssdResult) {
//        val intent = Intent(ACTION_RESULT).apply {
//            putExtra("result", result)
//        }
//        sendBroadcast(intent)
//        isRunning.set(false)


        val intent = Intent(ACTION_RESULT)
        intent.putExtra("result", result) // Use the direct method instead of apply
        sendBroadcast   (intent)
        isRunning.set(false)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "USSD Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
    }
}
