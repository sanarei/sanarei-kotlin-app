package com.sanarei.ussdkit

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi

// Data class to define a USSD request with steps
data class UssdRequest(
    val ussdCode: String, // e.g., "*123#"
    val steps: List<UssdStep> = emptyList(), // Steps to automate responses
    val timeoutSeconds: Int = 30
)

data class UssdStep(
    val promptPattern: String, // Regex to match the prompt (e.g., "Enter 1 to continue")
    val response: String // Response to send (e.g., "1")
)

// Sealed class for USSD results
sealed class UssdResult {
    data class Success(val response: String) : UssdResult()
    data class Failure(val error: String) : UssdResult()
    data class Intermediate(val response: String) : UssdResult()
}

// Main SDK class
@SuppressLint("StaticFieldLeak")
object UssdKit {
    private lateinit var context: Context

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun execute(
        request: UssdRequest,
        callback: (UssdResult) -> Unit
    ) {
        if (!::context.isInitialized) {
            callback(UssdResult.Failure("UssdKit not initialized"))
            return
        }

        val intent = Intent(context, UssdService::class.java).apply {
            putExtra(UssdService.USSD_REQUEST, request)
        }
        context.startForegroundService(intent)
        // Note: Results will be delivered via a BroadcastReceiver (implemented later)
    }
}
