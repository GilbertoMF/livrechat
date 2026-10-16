package com.livrechat.app

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.livrechat.app.ui.navigation.LivreChatNavGraph
import com.livrechat.app.ui.theme.LivreChatTheme
import com.livrechat.app.util.BiometricHelper
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : FragmentActivity() {

    private val pendingIntentFlow = MutableStateFlow<Intent?>(null)
    private val isAppUnlockedState = mutableStateOf(true)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val prefs = LivreChatApp.instance.preferencesManager
        if (prefs.isBiometricEnabled && prefs.accessToken != null && !prefs.currentUserId.isNullOrBlank()) {
            isAppUnlockedState.value = false
            triggerBiometricUnlock()
        } else {
            isAppUnlockedState.value = true
        }

        processIncomingIntent(intent)
        pendingIntentFlow.value = intent
        checkAndRequestPermissions()
        com.livrechat.app.sync.ContactSyncHelper.getOrCreateAccount(this)

        if (prefs.isLoggedIn()) {
            com.livrechat.app.service.LivreChatFirebaseService.syncToken(this)
        }

        if (prefs.blockScreenshots) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        setContent {
            val blockScreenshots by LivreChatApp.instance.preferencesManager.blockScreenshotsFlow.collectAsState()
            LaunchedEffect(blockScreenshots) {
                if (blockScreenshots) {
                    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            val themeMode by LivreChatApp.instance.preferencesManager.themeFlow.collectAsState()
            val colorPalette by LivreChatApp.instance.preferencesManager.paletteFlow.collectAsState()
            val isDarkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            LivreChatTheme(darkTheme = isDarkTheme, palette = colorPalette) {
                val isUnlocked by remember { isAppUnlockedState }

                if (!isUnlocked) {
                    // WhatsApp-style Biometric Lock Screen
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Digital",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(96.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "LivreChat Bloqueado",
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Toque no sensor de impressão digital para desbloquear o aplicativo",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { triggerBiometricUnlock() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Desbloquear com Biometria", color = Color.White)
                            }
                        }
                    }
                } else {
                    val navController = rememberNavController()
                    val currentIntent by pendingIntentFlow.collectAsState()

                    LivreChatNavGraph(
                        navController = navController,
                        pendingIntent = currentIntent,
                        onIntentHandled = { pendingIntentFlow.value = null }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = LivreChatApp.instance.preferencesManager
        if (prefs.blockScreenshots) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (prefs.isBiometricEnabled && prefs.accessToken != null && !prefs.currentUserId.isNullOrBlank() && !isAppUnlockedState.value) {
            triggerBiometricUnlock()
        }

        if (prefs.isLoggedIn()) {
            if (!LivreChatApp.instance.connectionManager.isConnected.value) {
                LivreChatApp.instance.connectionManager.connect()
            }
        }
    }

    private fun triggerBiometricUnlock() {
        BiometricHelper.authenticate(
            activity = this,
            title = "LivreChat Bloqueado",
            subtitle = "Confirme sua identidade com biometria",
            onSuccess = {
                isAppUnlockedState.value = true
            },
            onError = {
                // User cancelled or failed
            }
        )
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIncomingIntent(intent)
        pendingIntentFlow.value = intent
    }

    private fun processIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val uri = intent.data
        if (intent.action == Intent.ACTION_VIEW && uri != null) {
            val type = intent.type ?: try { contentResolver.getType(uri) } catch (e: Exception) { null }
            val isChat = type == com.livrechat.app.sync.ContactSyncHelper.MIME_CHAT
            val isCall = type == com.livrechat.app.sync.ContactSyncHelper.MIME_CALL
            if (isChat || isCall) {
                try {
                    contentResolver.query(
                        uri,
                        arrayOf(
                            android.provider.ContactsContract.Data.DATA1,
                            android.provider.ContactsContract.Data.DATA2
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val userId = cursor.getString(0)
                            val summary = cursor.getString(1) ?: "Contato"
                            val name = summary.removePrefix("Mensagem para ").trim()
                            if (!userId.isNullOrBlank()) {
                                intent.putExtra("targetUserId", userId)
                                intent.putExtra("targetDisplayName", name)
                                if (isCall) {
                                    intent.putExtra("isVoiceCall", true)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Erro lendo contato de intent: ${e.message}")
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_CONTACTS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.WRITE_CONTACTS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        requestBatteryOptimizationExemption()
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = getSystemService(PowerManager::class.java)
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                // Ignore if device restricts direct exemption prompt
            }
        }
    }
}
