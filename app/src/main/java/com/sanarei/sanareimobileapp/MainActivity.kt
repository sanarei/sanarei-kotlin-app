package com.sanarei.sanareimobileapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.romellfudi.ussdlibrary.USSDController
import com.sanarei.sanareimobileapp.ui.theme.SanareiMobileAppTheme


class MainActivity : ComponentActivity() {
    private val map: HashMap<String, List<String>> = HashMap<String, List<String>>().apply {
        put("KEY_LOGIN", listOf("espere", "waiting", "loading", "esperando", "Espere por favor"))
        put("KEY_ERROR", listOf("problema", "problem", "error", "null", "invalid", "failed"))
    }

    // State for USSD code input and response
    private val website = mutableStateOf("https://sanarei-sample-app.onrender.com") // Default or empty
    private val ussdResponse = mutableStateOf("The website will be loaded below.")
    private val isSending = mutableStateOf(false)
    private val capturedUssdMessages = mutableListOf<String>()
    private val loadingOverlay by lazy { UssdLoadingOverlay(applicationContext) }
    private var pendingUssdCode: String? = null

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val code = pendingUssdCode
            pendingUssdCode = null
            if (Settings.canDrawOverlays(this) && code != null) {
                sendUSSD(code)
            } else {
                isSending.value = false
                ussdResponse.value = getString(R.string.overlay_permission_required)
                Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
            }
        }

    // Permission Launcher to handle multiple permissions
    private val requestMultiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allPermissionsGranted = true
            permissions.entries.forEach {
                if (!it.value) {
                    allPermissionsGranted = false
                    Toast.makeText(this, "${it.key} permission denied.", Toast.LENGTH_SHORT).show()
                }
            }

            if (allPermissionsGranted) {
                Toast.makeText(this, "All required permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Some permissions were denied. The app might not function correctly.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions when the activity is created or when needed
        checkAndRequestPermissions()

        setContent {
            SanareiMobileAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    USSDScreen(
                        website = website.value,
                        onUssdCodeChange = { website.value = it },
                        response = "",
                        isSending = isSending.value,
                        onSendUSSD = { code ->
                            if (isAccessibilityServiceEnabled(this@MainActivity)) {
                                sendUSSDWithOverlayPermission(code)
                            } else {
                                ussdResponse.value =
                                    "Accessibility Service is not enabled. Please enable it in settings."
                                Toast.makeText(
                                    this@MainActivity,
                                    "Please enable the USSD Accessibility Service",
                                    Toast.LENGTH_LONG
                                ).show()
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                startActivity(intent)
                            }
                        },
                        html = ussdResponse.value
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.CALL_PHONE)
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun sendUSSD(code: String) {
        if (code.isBlank()) {
            ussdResponse.value = "USSD code cannot be empty."
            return
        }

        capturedUssdMessages.clear()
        isSending.value = true
        ussdResponse.value = "Sending USSD: $code..."
        loadingOverlay.show()

        try {
            USSDController.callUSSDInvoke(
                this, Uri.encode(code), 0, map, object : USSDController.CallbackInvoke {
                override fun responseInvoke(message: String) {
                    ussdResponse.value = "Initial Response: $message"

                    if (message.contains("Enter App domain", ignoreCase = true)) {
                        sendNextUSSDInput(website.value)
                    } else {
                        isSending.value = false
                        loadingOverlay.hide()
                        // Session might be over or no clear prompt for next step from this initial response
                        Toast.makeText(
                            this@MainActivity,
                            "Session might be complete or no clear next step.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun over(message: String) {
                    isSending.value = false
                    loadingOverlay.hide()
                    ussdResponse.value = if (capturedUssdMessages.isEmpty()) {
                        message
                    } else {
                        try {
                            SanareiDepacketizer.depacketize(capturedUssdMessages)
                        } catch (error: RuntimeException) {
                            "Unable to reconstruct the page: ${error.message}"
                        }
                    }
                }
            })
        } catch (error: RuntimeException) {
            loadingOverlay.hide()
            isSending.value = false
            ussdResponse.value = "Unable to start USSD session: ${error.message}"
        }
    }

    private fun sendUSSDWithOverlayPermission(code: String) {
        if (Settings.canDrawOverlays(this)) {
            sendUSSD(code)
            return
        }

        pendingUssdCode = code
        ussdResponse.value = getString(R.string.overlay_permission_required)
        overlayPermissionLauncher.launch(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    // New function to handle sending subsequent inputs
    private fun sendNextUSSDInput(input: String) {
        isSending.value = true
        ussdResponse.value = "Sending input: $input..."

        USSDController.send(input) { responseMessage ->
            // This is the lambda callback for the response to USSDController.send(input)
            ussdResponse.value = "Next Response: $responseMessage"

            if (responseMessage.contains("DOMAIN SET", ignoreCase = true)) {
                sendNextUSSDInput("FETCH") // Send store number
            } else if (responseMessage.contains("PACKETS READY", ignoreCase = true)) {
                sendNextUSSDInput("SEND PACKETS") // Select Account Services
            } else if (responseMessage.contains("ALL PACKETS SENT", ignoreCase = true)) {
                sendNextUSSDInput("END SESSION") // Select Account Services
                // Compile all packets received
            } else {
                val mainReponseMessage = responseMessage.removePrefix("[")
                    .removeSuffix("]").split(",").first().trim()
                capturedUssdMessages.add(mainReponseMessage)
                sendNextUSSDInput("SEND NEXT PACKETS") // Fetch all the packets
            }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = packageName + "/com.romellfudi.ussdlibrary.USSDServiceKT"
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServicesSetting?.contains(expectedComponentName, ignoreCase = true) ?: false
    }

    override fun onDestroy() {
        loadingOverlay.hide()
        super.onDestroy()
    }
}

@Composable
fun HtmlScreen(html: String) {
    Box(
        modifier = Modifier
            .padding(bottom = 60.dp) // reserve space at bottom
            .fillMaxSize()
            .background(Color.White)
            .padding( top = 8.dp) // top padding
            .padding(horizontal = 16.dp) // internal padding
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                TextView(context).apply {
                    movementMethod = LinkMovementMethod.getInstance() // make links clickable
                    setTextIsSelectable(true)
                }
            },
            update = { textView ->
                textView.text = HtmlCompat.fromHtml(
                    html,
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                )
            }
        )
    }
}


@Composable
fun USSDScreen(
    website: String,
    onUssdCodeChange: (String) -> Unit,
    response: String,
    isSending: Boolean,
    onSendUSSD: (String) -> Unit,
    html: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Sanarei Offline Web Browsing",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp, top = 24.dp)
        )

        OutlinedTextField(
            value = website,
            onValueChange = onUssdCodeChange,
            label = { Text("Enter website") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { onSendUSSD("*619*11#") },
            enabled = !isSending,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary
                )
                Text("Processing...")
            } else {
                Text("Fetch Website")
            }
        }
        Text(
            text = response,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(5.dp)
                .padding(8.dp) // For better text visibility
        )
        HtmlScreen(html)
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AppScreenPreview() {
    SanareiMobileAppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            USSDScreen(
                website = "*619*11#",
                onUssdCodeChange = {},
                response = "The website will be loaded below",
                isSending = false,
                onSendUSSD = {},
                html = "")
        }
    }
}
