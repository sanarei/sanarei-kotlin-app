package com.sanarei.sanareimobileapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import android.util.Log
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.sanarei.sanareimobileapp.ui.theme.SanareiMobileAppTheme
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge layout
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            SanareiMobileAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo), // Replace with your logo
                        contentDescription = "Logo",
                        modifier = Modifier
                            .height(32.dp)
                            .padding(end = 8.dp)
                    )
                    Text("Sanarei") // Optional title next to logo
                }
            })
    }, content = { innerPadding ->
        UrlInputScreen(modifier = Modifier.padding(innerPadding))
    })
}

@Composable
fun UrlInputScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(TextFieldValue("*234#")) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Enter the URL of the app") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            val ussdCode = "tel:" + url.text.replace("#", "%23")
            val uriUssdCode = ussdCode.toUri()
            Log.d("ussdCode", "Attempting to dial: $uriUssdCode")
            val intent = Intent(Intent.ACTION_CALL, uriUssdCode)

            val intent = Intent(context, UssdService::class.java).apply {
                putExtra(UssdService.USSD_CODE, uriUssdCode)
            }
            context.start
            ForegroundService(intent)

            // Check permission
            val permission = Manifest.permission.CALL_PHONE
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                context.startActivity(intent)
            } else {
                ActivityCompat.requestPermissions(
                    context as ComponentActivity,
                    arrayOf(permission),
                    1
                )
            }
        }) {
            Text("Submit", fontSize = 16.sp)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AppScreenPreview() {
    SanareiMobileAppTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppScreen()
        }
    }
}
