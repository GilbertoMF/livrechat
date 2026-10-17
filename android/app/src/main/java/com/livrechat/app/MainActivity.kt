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
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
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

        val isIncomingCall = intent.getBooleanExtra("incomingCall", false)
        val prefs = LivreChatApp.instance.preferencesManager
        val hasPin = prefs.isPinLockEnabled && !prefs.appLockPin.isNullOrBlank()
        if ((prefs.isBiometricEnabled || hasPin) && prefs.accessToken != null && !prefs.currentUserId.isNullOrBlank() && !isIncomingCall) {
            isAppUnlockedState.value = false
            if (prefs.isBiometricEnabled && !hasPin) {
                triggerBiometricUnlock()
            }
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
                val isCamouflageEnabled by prefs.camouflageModeFlow.collectAsState()
                var isCamouflagePassed by remember { mutableStateOf(!isCamouflageEnabled) }

                if (isCamouflageEnabled && !isCamouflagePassed) {
                    com.livrechat.app.ui.calculator.CalculatorScreen(
                        onUnlock = { isDuress ->
                            LivreChatApp.instance.isDuressModeActive.value = isDuress
                            isCamouflagePassed = true
                            isAppUnlockedState.value = true
                        }
                    )
                } else if (!isUnlocked) {
                    val hasPin = prefs.isPinLockEnabled && !prefs.appLockPin.isNullOrBlank()
                    if (hasPin) {
                        PinLockScreen(
                            prefs = prefs,
                            onUnlocked = { isDuress ->
                                LivreChatApp.instance.isDuressModeActive.value = isDuress
                                isAppUnlockedState.value = true
                            },
                            onBiometricClick = if (prefs.isBiometricEnabled) {
                                { triggerBiometricUnlock() }
                            } else null,
                            onWipeTriggered = {
                                prefs.wipeAllLocalData(this@MainActivity)
                                Toast.makeText(this@MainActivity, "Segurança ativada: dados limpos.", Toast.LENGTH_LONG).show()
                                finishAffinity()
                            }
                        )
                    } else {
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
        val hasPin = prefs.isPinLockEnabled && !prefs.appLockPin.isNullOrBlank()
        if ((prefs.isBiometricEnabled || hasPin) && prefs.accessToken != null && !prefs.currentUserId.isNullOrBlank() && !isAppUnlockedState.value) {
            if (prefs.isBiometricEnabled && !hasPin) {
                triggerBiometricUnlock()
            }
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
        if (intent?.getBooleanExtra("incomingCall", false) == true) {
            isAppUnlockedState.value = true
        }
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

@Composable
fun PinLockScreen(
    prefs: com.livrechat.app.data.local.PreferencesManager,
    onUnlocked: (isDuress: Boolean) -> Unit,
    onBiometricClick: (() -> Unit)?,
    onWipeTriggered: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var failedAttempts by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            val realPin = prefs.appLockPin
            val duressPin = prefs.duressPin
            if (enteredPin == realPin) {
                failedAttempts = 0
                errorMessage = null
                onUnlocked(false)
            } else if (!duressPin.isNullOrBlank() && enteredPin == duressPin) {
                // Duress PIN entered - silent trigger of fake mode!
                failedAttempts = 0
                errorMessage = null
                onUnlocked(true)
            } else {
                failedAttempts++
                if (prefs.autoWipeOnFailedPin && failedAttempts >= 5) {
                    onWipeTriggered()
                } else {
                    errorMessage = if (prefs.autoWipeOnFailedPin) {
                        "PIN incorreto ($failedAttempts de 5)"
                    } else {
                        "PIN incorreto"
                    }
                    enteredPin = ""
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Bloqueado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "LivreChat Bloqueado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Digite seu PIN de 4 dígitos",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 4 PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val isFilled = index < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = errorMessage!!,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Keypad
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("bio", "0", "back")
                )

                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            when (key) {
                                "bio" -> {
                                    if (onBiometricClick != null) {
                                        IconButton(
                                            onClick = onBiometricClick,
                                            modifier = Modifier.size(68.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = "Biometria",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(68.dp))
                                    }
                                }
                                "back" -> {
                                    IconButton(
                                        onClick = {
                                            if (enteredPin.isNotEmpty()) {
                                                enteredPin = enteredPin.dropLast(1)
                                                errorMessage = null
                                            }
                                        },
                                        modifier = Modifier.size(68.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Backspace,
                                            contentDescription = "Apagar",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Surface(
                                        onClick = {
                                            if (enteredPin.length < 4) {
                                                enteredPin += key
                                                errorMessage = null
                                            }
                                        },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(68.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = key,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
