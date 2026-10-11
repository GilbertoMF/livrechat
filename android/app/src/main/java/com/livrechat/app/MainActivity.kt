package com.livrechat.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.livrechat.app.ui.theme.BluePrimary
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
        if (prefs.isBiometricEnabled && prefs.accessToken != null) {
            isAppUnlockedState.value = false
            triggerBiometricUnlock()
        } else {
            isAppUnlockedState.value = true
        }

        pendingIntentFlow.value = intent
        checkAndRequestPermissions()

        setContent {
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
                                tint = BluePrimary,
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
                                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
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
        if (prefs.isBiometricEnabled && prefs.accessToken != null && !isAppUnlockedState.value) {
            triggerBiometricUnlock()
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
        pendingIntentFlow.value = intent
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
