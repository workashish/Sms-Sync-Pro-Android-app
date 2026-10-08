package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import android.view.WindowManager
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.preventScreenCapture.collect { secure ->
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }

        setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    PermissionsWrapper(
                        onRequestPermissions = {
                            val perms = mutableListOf(
                                Manifest.permission.RECEIVE_SMS
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            requestPermissionLauncher.launch(perms.toTypedArray())
                        }
                    ) {
                        MainScreen(viewModel = viewModel, initialTab = if (intent.getBooleanExtra("open_settings", false)) 2 else 0)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionsWrapper(onRequestPermissions: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
    
    var permissionsGranted by remember { 
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) 
    }
    var batteryOptimized by remember { 
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm?.isIgnoringBatteryOptimizations(context.packageName) == false)
    }
    val setupPreferences = context.getSharedPreferences("setup", Context.MODE_PRIVATE)
    var skipBatteryOpt by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setupPreferences.getBoolean("battery_prompt_dismissed", false)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        batteryOptimized = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm?.isIgnoringBatteryOptimizations(context.packageName) == false
    }

    var continueWithoutSms by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (!permissionsGranted && !continueWithoutSms) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("Grant SMS receipt permission to capture incoming SMS. Send SMS permission is requested only for SMS targets.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRequestPermissions) {
                    Text("Grant Permissions")
                }
                TextButton(onClick = { continueWithoutSms = true }) { Text("Continue to Settings") }
            }
        }
    } else if (batteryOptimized && !skipBatteryOpt) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Text("To prevent Samsung or Android from putting the app to sleep, please disable Battery Optimizations.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    try {
                        if (android.os.Build.MODEL.contains("Emulator") && android.os.Build.FINGERPRINT.contains("generic")) {
                            android.widget.Toast.makeText(context, "System settings not available in preview.", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    setupPreferences.edit().putBoolean("battery_prompt_dismissed", true).apply()
                    skipBatteryOpt = true
                }) {
                    Text("Disable Battery Optimization")
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { 
                    setupPreferences.edit().putBoolean("battery_prompt_dismissed", true).apply()
                    skipBatteryOpt = true
                }) {
                    Text("Skip for now")
                }
            }
        }
    } else {
        content()
    }
}

