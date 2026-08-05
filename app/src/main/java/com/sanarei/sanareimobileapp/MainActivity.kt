package com.sanarei.sanareimobileapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

        SanareiUssdAccessibilityService.setNavigationHandler { url ->
            if (isSending.value) {
                false
            } else {
                website.value = url
                sendUSSD("*619*11#")
                true
            }
        }

        // Request permissions when the activity is created or when needed
        checkAndRequestPermissions()

        setContent {
            SanareiMobileAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    USSDScreen(
                        website = website.value,
                        onUssdCodeChange = { website.value = it },
                        isSending = isSending.value,
                        onSendUSSD = { code ->
                            if (isAccessibilityServiceEnabled(this@MainActivity)) {
                                sendUSSD(code)
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
                        }
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
        SanareiUssdAccessibilityService.showLoadingOverlay()

        try {
            UssdSessionLauncher.start(
                this, Uri.encode(code), 0, map, object : USSDController.CallbackInvoke {
                override fun responseInvoke(message: String) {
                    ussdResponse.value = "Initial Response: $message"

                    if (message.contains("Enter App domain", ignoreCase = true)) {
                        sendNextUSSDInput(website.value)
                    } else {
                        isSending.value = false
                        SanareiUssdAccessibilityService.showErrorOverlay(message)
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
                    if (capturedUssdMessages.isEmpty()) {
                        ussdResponse.value = message
                        SanareiUssdAccessibilityService.showErrorOverlay(message)
                        return
                    }

                    try {
                        val page = SanareiDepacketizer.depacketize(capturedUssdMessages)
                        ussdResponse.value = page
                        SanareiUssdAccessibilityService.showPageOverlay(page, website.value)
                    } catch (error: Exception) {
                        val errorMessage =
                            "Unable to reconstruct the page. Please try again."
                        ussdResponse.value = "$errorMessage ${error.message.orEmpty()}".trim()
                        SanareiUssdAccessibilityService.showErrorOverlay(errorMessage)
                    }
                }
            })
        } catch (error: RuntimeException) {
            SanareiUssdAccessibilityService.showErrorOverlay(
                "Unable to start USSD session: ${error.message}"
            )
            isSending.value = false
            ussdResponse.value = "Unable to start USSD session: ${error.message}"
        }
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
        val expectedComponentName = packageName + "/" +
            SanareiUssdAccessibilityService::class.java.name
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServicesSetting?.contains(expectedComponentName, ignoreCase = true) ?: false
    }

    override fun onDestroy() {
        SanareiUssdAccessibilityService.clearNavigationHandler()
        super.onDestroy()
    }
}

@Composable
fun USSDScreen(
    website: String,
    onUssdCodeChange: (String) -> Unit,
    isSending: Boolean,
    onSendUSSD: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(92.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "Sanarei",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Browse without internet",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Enter a website and Sanarei will retrieve it over USSD.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, bottom = 30.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Text(
                        text = "Website address",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Include https:// for the best results.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                    )

                    OutlinedTextField(
                        value = website,
                        onValueChange = onUssdCodeChange,
                        placeholder = { Text("https://example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = { onSendUSSD("*619*11#") },
                        enabled = !isSending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Fetching website…")
                        } else {
                            Text("Fetch website", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "How it works",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "The page opens in the Sanarei browser when retrieval is complete. Links are cached as you browse.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AppScreenPreview() {
    SanareiMobileAppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            USSDScreen(
                website = "https://sanarei-sample-app.onrender.com",
                onUssdCodeChange = {},
                isSending = false,
                onSendUSSD = {}
            )
        }
    }
}
