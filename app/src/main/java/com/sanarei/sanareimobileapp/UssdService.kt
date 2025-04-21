import android.Manifest
import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat

class UssdService : Service() {
    companion object {
        const val CHANNEL_ID = "UssdServiceChannel"
        const val NOTIFICATION_ID = 1
        const val USSD_CODE = "ussd_code"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @RequiresPermission(Manifest.permission.CALL_PHONE)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ussdCode = intent?.getStringExtra(USSD_CODE) ?: return START_NOT_STICKY

        // Start foreground service with a notification
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USSD Service")
            .setContentText("Running USSD code in background...")
            .setSmallIcon(R.drawable.ic_dialog_info)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // Execute USSD code
        executeUssd(ussdCode)

        return START_STICKY
    }

    @RequiresPermission(Manifest.permission.CALL_PHONE)
    private fun executeUssd(ussdCode: String) {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            telephonyManager.sendUssdRequest(
                ussdCode,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        response: CharSequence
                    ) {
                        Log.d("UssdService", "USSD Response: $response")
                        // Handle the response (e.g., parse and respond to prompts)
                        handleUssdResponse(response.toString())
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int
                    ) {
                        Log.e("UssdService", "USSD Failed: $failureCode")
                        stopSelf()
                    }
                },
                Handler(mainLooper)
            )
        } else {
            Log.e("UssdService", "USSD not supported on this Android version")
            stopSelf()
        }
    }

    private fun handleUssdResponse(response: String) {
        // Example: If the response contains a prompt like "Enter 1 to continue", automate the response
        if (response.contains("Enter 1 to continue", ignoreCase = true)) {
            // Simulate sending "1" as a response (this is not directly supported by TelephonyManager)
            Log.d("UssdService", "Detected prompt, attempting to send '1'")
            // Note: Automating USSD responses typically requires AccessibilityService or third-party libraries
            // For now, log the action
        }
        // Stop the service after handling the response
        stopSelf()
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
}