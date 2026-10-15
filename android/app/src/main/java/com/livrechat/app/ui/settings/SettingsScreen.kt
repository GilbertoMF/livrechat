package com.livrechat.app.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.livrechat.app.LivreChatApp
import com.livrechat.app.ui.common.PhotoAdjustDialog
import com.livrechat.app.ui.common.UserAvatarView
import com.livrechat.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = LivreChatApp.instance.preferencesManager
    val db = LivreChatApp.instance.database
    val apiService = LivreChatApp.instance.apiClient.apiService
    val connectionManager = LivreChatApp.instance.connectionManager

    var displayName by remember { mutableStateOf(prefs.currentDisplayName ?: "Usuário") }
    var userBio by remember { mutableStateOf(prefs.userBio) }
    var userAvatarUrl by remember { mutableStateOf(prefs.userAvatarUrl ?: "") }
    val username = prefs.currentUsername ?: "usuario"
    val userId = prefs.currentUserId ?: ""

    val isConnected by connectionManager.isConnected.collectAsState()
    val conversations by db.conversationDao().getAllConversationsFlow().collectAsState(initial = emptyList())
    val activePalette by prefs.paletteFlow.collectAsState()
    val activeTheme by prefs.themeFlow.collectAsState()

    // Dialogs state
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showPhotoAdjustDialog by remember { mutableStateOf(false) }
    var bitmapToAdjust by remember { mutableStateOf<Bitmap?>(null) }
    var currentAvatarBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showSecurityDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showThemeAndColorsDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showDevicesDialog by remember { mutableStateOf(false) }
    var showManageContactsDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showRecoveryPhraseDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Edit profile state
    var newDisplayNameInput by remember { mutableStateOf(displayName) }
    var newBioInput by remember { mutableStateOf(userBio) }
    var newAvatarUrlInput by remember { mutableStateOf(userAvatarUrl) }
    var customWebUrlInput by remember { mutableStateOf(if (userAvatarUrl.startsWith("http")) userAvatarUrl else "") }
    var userRecoveryPhrase by remember { mutableStateOf<String?>(null) }
    var userPublicKey by remember { mutableStateOf<String?>(null) }

    // Temporary photo file for camera capture
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Load initial avatar bitmap if stored locally or base64
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val user = db.userDao().getCurrentUser()
            userRecoveryPhrase = user?.recoveryKey
            userPublicKey = user?.publicKey ?: "Chave segura RSA-2048 ativa"

            // 1. Sync with server profile if online
            if (userId.isNotBlank()) {
                try {
                    val profileRes = apiService.getUserProfile(userId)
                    if (profileRes.isSuccessful && profileRes.body() != null) {
                        val profile = profileRes.body()!!
                        withContext(Dispatchers.Main) {
                            if (!profile.displayName.isNullOrBlank()) {
                                displayName = profile.displayName
                                prefs.currentDisplayName = profile.displayName
                            }
                            if (!profile.bio.isNullOrBlank()) {
                                userBio = profile.bio
                                prefs.userBio = profile.bio
                            }
                            if (!profile.avatarUrl.isNullOrBlank()) {
                                userAvatarUrl = profile.avatarUrl
                                prefs.userAvatarUrl = profile.avatarUrl
                            } else {
                                userAvatarUrl = ""
                                prefs.userAvatarUrl = null
                                currentAvatarBitmap = null
                                File(context.filesDir, "avatar_current.jpg").delete()
                                File(context.filesDir, "avatar_${userId}.jpg").delete()
                            }
                        }
                    }
                } catch (e: Exception) {}
            }

            // 2. Load avatar bitmap if user has an avatar
            val effectiveAvatar = prefs.userAvatarUrl ?: userAvatarUrl
            if (!effectiveAvatar.isNullOrBlank()) {
                val userFile = File(context.filesDir, "avatar_${userId}.jpg")
                val legacyFile = File(context.filesDir, "avatar_current.jpg")
                if (userFile.exists()) {
                    val bmp = BitmapFactory.decodeFile(userFile.absolutePath)
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            currentAvatarBitmap = bmp
                        }
                    }
                } else if (legacyFile.exists()) {
                    val bmp = BitmapFactory.decodeFile(legacyFile.absolutePath)
                    if (bmp != null) {
                        withContext(Dispatchers.Main) {
                            currentAvatarBitmap = bmp
                        }
                    }
                } else if (effectiveAvatar.startsWith("data:image/")) {
                    try {
                        val cleanBase64 = if (effectiveAvatar.contains(",")) effectiveAvatar.substringAfter(",") else effectiveAvatar
                        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        withContext(Dispatchers.Main) {
                            currentAvatarBitmap = bmp
                        }
                    } catch (e: Exception) {}
                }
            } else {
                withContext(Dispatchers.Main) {
                    currentAvatarBitmap = null
                }
                try {
                    File(context.filesDir, "avatar_current.jpg").delete()
                    File(context.filesDir, "avatar_${userId}.jpg").delete()
                } catch (e: Exception) {}
            }
        }
    }

    // Camera launcher with FileProvider
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists()) {
            val fileToProcess = tempCameraFile!!
            scope.launch(Dispatchers.IO) {
                try {
                    var bitmap = BitmapFactory.decodeFile(fileToProcess.absolutePath)
                    if (bitmap != null) {
                        try {
                            val exif = ExifInterface(fileToProcess.absolutePath)
                            val orientation = exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                            )
                            val matrix = Matrix()
                            when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            }
                            if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                            }
                        } catch (e: Exception) {}

                        withContext(Dispatchers.Main) {
                            bitmapToAdjust = bitmap
                            showPhotoAdjustDialog = true
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao abrir foto da câmera", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    fileToProcess.delete()
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            tempCameraUri?.let { takePictureLauncher.launch(it) }
        } else {
            Toast.makeText(context, "Permissão de câmera é necessária para tirar foto", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCamera() {
        try {
            val file = File(context.cacheDir, "avatar_cam_${System.currentTimeMillis()}.jpg")
            tempCameraFile = file
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            tempCameraUri = uri
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                takePictureLauncher.launch(uri)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao iniciar câmera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (originalBitmap != null) {
                        withContext(Dispatchers.Main) {
                            bitmapToAdjust = originalBitmap
                            showPhotoAdjustDialog = true
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao selecionar imagem", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // DIALOG: WhatsApp-style Photo Adjust & Crop
    if (showPhotoAdjustDialog && bitmapToAdjust != null) {
        PhotoAdjustDialog(
            sourceBitmap = bitmapToAdjust!!,
            onDismiss = {
                showPhotoAdjustDialog = false
                bitmapToAdjust = null
            },
            onCropFinished = { croppedBitmap ->
                scope.launch(Dispatchers.IO) {
                    try {
                        // 1. Save local file
                        val localUserFile = File(context.filesDir, "avatar_${userId}.jpg")
                        val localFile = File(context.filesDir, "avatar_current.jpg")
                        FileOutputStream(localUserFile).use { fos ->
                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                        }
                        FileOutputStream(localFile).use { fos ->
                            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                        }

                        // 2. Encode to Base64 data URI
                        val baos = ByteArrayOutputStream()
                        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        val bytes = baos.toByteArray()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val dataUri = "data:image/jpeg;base64,$base64"

                        withContext(Dispatchers.Main) {
                            currentAvatarBitmap = croppedBitmap
                            newAvatarUrlInput = dataUri
                            showPhotoAdjustDialog = false
                            bitmapToAdjust = null
                            Toast.makeText(context, "Foto ajustada e pronta!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Erro ao salvar recorte: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        )
    }

    // DIALOG: Editar Perfil
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Editar Perfil", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Preview do Avatar com botão da CÂMERA
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clickable {
                                    if (currentAvatarBitmap != null) {
                                        bitmapToAdjust = currentAvatarBitmap
                                        showPhotoAdjustDialog = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            UserAvatarView(
                                avatarBitmap = currentAvatarBitmap,
                                avatarUrl = newAvatarUrlInput,
                                displayName = newDisplayNameInput,
                                size = 100.dp,
                                fontSize = 36.sp
                            )
                        }
                    }

                    // Botões de ação para Foto
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentAvatarBitmap != null) {
                            FilledTonalButton(
                                onClick = {
                                    bitmapToAdjust = currentAvatarBitmap
                                    showPhotoAdjustDialog = true
                                },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ajustar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            OutlinedButton(
                                onClick = { launchCamera() },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Câmera", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Galeria", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    currentAvatarBitmap = null
                                    newAvatarUrlInput = ""
                                    customWebUrlInput = ""
                                    val f = File(context.filesDir, "avatar_current.jpg")
                                    if (f.exists()) f.delete()
                                    val userF = File(context.filesDir, "avatar_${userId}.jpg")
                                    if (userF.exists()) userF.delete()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remover Foto", tint = DangerRed, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            Button(
                                onClick = { launchCamera() },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Câmera", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Galeria", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = newDisplayNameInput,
                        onValueChange = { newDisplayNameInput = it },
                        label = { Text("Nome de Exibição") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newBioInput,
                        onValueChange = { newBioInput = it },
                        label = { Text("Bio / Recado") },
                        placeholder = { Text("Ex: Disponível no LivreChat...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Disponível", "Ocupado", "Em chamada").forEach { tag ->
                            SuggestionChip(
                                onClick = { newBioInput = tag },
                                label = { Text(tag, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (newAvatarUrlInput.startsWith("data:image/") || currentAvatarBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Foto ajustada e pronta para o perfil", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = customWebUrlInput,
                            onValueChange = { input ->
                                if (input.length <= 500) {
                                    customWebUrlInput = input
                                    newAvatarUrlInput = input
                                }
                            },
                            label = { Text("Link da Foto de Perfil (URL Opcional)") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedName = newDisplayNameInput.trim()
                        val trimmedBio = newBioInput.trim()
                        val trimmedAvatar = newAvatarUrlInput.trim()

                        if (trimmedName.isNotBlank()) {
                            displayName = trimmedName
                            prefs.currentDisplayName = trimmedName
                            userBio = trimmedBio
                            prefs.userBio = trimmedBio
                            userAvatarUrl = trimmedAvatar
                            prefs.userAvatarUrl = if (trimmedAvatar.isNotBlank()) trimmedAvatar else null
                            if (trimmedAvatar.isBlank()) {
                                currentAvatarBitmap = null
                                try {
                                    File(context.filesDir, "avatar_current.jpg").delete()
                                    File(context.filesDir, "avatar_${userId}.jpg").delete()
                                } catch (e: Exception) {}
                            }
                            showEditProfileDialog = false

                            scope.launch(Dispatchers.IO) {
                                try {
                                    apiService.updateProfile(
                                        mapOf(
                                            "displayName" to trimmedName,
                                            "bio" to trimmedBio,
                                            "avatarUrl" to trimmedAvatar
                                        )
                                    )
                                    val current = db.userDao().getCurrentUser()
                                    if (current != null) {
                                        db.userDao().insertOrUpdate(
                                            current.copy(
                                                displayName = trimmedName,
                                                avatarUrl = trimmedAvatar
                                            )
                                        )
                                    }
                                } catch (e: Exception) {}
                            }
                            Toast.makeText(context, "Perfil atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // DIALOG: Cores e Aparência
    if (showThemeAndColorsDialog) {
        val palettes = listOf(
            "default" to Pair("Azul Safira (Padrão)", Color(0xFF0066FF)),
            "emerald" to Pair("Esmeralda Cyber", Color(0xFF10B981)),
            "purple" to Pair("Roxo Ametista", Color(0xFF8B5CF6)),
            "amber" to Pair("Laranja Solar", Color(0xFFF59E0B)),
            "ruby" to Pair("Rosa Rubi", Color(0xFFEC4899)),
            "midnight" to Pair("Meia-Noite OLED", Color(0xFF06B6D4))
        )
        val themes = listOf(
            "system" to "Padrão do Sistema",
            "dark" to "Modo Escuro",
            "light" to "Modo Claro"
        )
        var selectedTheme by remember { mutableStateOf(prefs.appTheme) }
        var selectedPalette by remember { mutableStateOf(prefs.colorPalette) }

        AlertDialog(
            onDismissRequest = { showThemeAndColorsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aparência & Paletas de Cores", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Modo de Exibição:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "Sistema", "dark" to "Escuro", "light" to "Claro").forEach { (key, label) ->
                            FilterChip(
                                selected = selectedTheme == key,
                                onClick = {
                                    selectedTheme = key
                                    prefs.appTheme = key
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    Text("Paleta de Cores Exclusiva:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Muda as cores de todo o app em tempo real:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(10.dp))

                    palettes.forEach { (key, pair) ->
                        val (label, color) = pair
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedPalette == key) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = if (selectedPalette == key) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedPalette = key
                                    prefs.colorPalette = key
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (selectedPalette == key) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selectedPalette == key) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showThemeAndColorsDialog = false }) {
                    Text("Concluir")
                }
            }
        )
    }

    // DIALOG: Conta e Segurança Avançada
    if (showSecurityDialog) {
        var isBioActive by remember { mutableStateOf(prefs.isBiometricEnabled) }
        var isPinActive by remember { mutableStateOf(prefs.isPinLockEnabled) }
        var selfDestruct by remember { mutableStateOf(prefs.selfDestructTimer) }
        var blockPrints by remember { mutableStateOf(prefs.blockScreenshots) }

        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Segurança & Criptografia", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Biometria
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bloqueio por Biometria", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Exigir digital ao abrir o LivreChat", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isBioActive,
                            onCheckedChange = {
                                isBioActive = it
                                prefs.isBiometricEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    // Bloquear Prints
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bloquear Captura de Tela", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Impede prints e fotos de conversas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = blockPrints,
                            onCheckedChange = {
                                blockPrints = it
                                prefs.blockScreenshots = it
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    // Mensagens Autodestrutivas
                    Text("Autodestruição de Mensagens:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selfDestruct == "off",
                            onClick = { selfDestruct = "off"; prefs.selfDestructTimer = "off" },
                            label = { Text("Desligado", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selfDestruct == "24h",
                            onClick = { selfDestruct = "24h"; prefs.selfDestructTimer = "24h" },
                            label = { Text("24 Horas", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selfDestruct == "7d",
                            onClick = { selfDestruct = "7d"; prefs.selfDestructTimer = "7d" },
                            label = { Text("7 Dias", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selfDestruct == "30d",
                            onClick = { selfDestruct = "30d"; prefs.selfDestructTimer = "30d" },
                            label = { Text("30 Dias", fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            showSecurityDialog = false
                            showRecoveryPhraseDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ver 12 Palavras Mnemônicas", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSecurityDialog = false }) {
                    Text("Salvar")
                }
            }
        )
    }

    // DIALOG: Privacidade & Visibilidade
    if (showPrivacyDialog) {
        var readReceipts by remember { mutableStateOf(prefs.readReceiptsEnabled) }
        var lastSeen by remember { mutableStateOf(prefs.lastSeenVisibility) }

        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacidade & Ticks", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Confirmação de Leitura (Ticks Azuis)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Exibir 2 ticks azuis quando mensagens forem lidas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = readReceipts,
                            onCheckedChange = {
                                readReceipts = it
                                prefs.readReceiptsEnabled = it
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text("Visto por Último:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("everyone" to "Todos", "contacts" to "Contatos", "nobody" to "Ninguém").forEach { (key, label) ->
                            FilterChip(
                                selected = lastSeen == key,
                                onClick = {
                                    lastSeen = key
                                    prefs.lastSeenVisibility = key
                                },
                                label = { Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            showPrivacyDialog = false
                            showBlockedDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ver Contatos Bloqueados", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) {
                    Text("Salvar")
                }
            }
        )
    }

    // DIALOG: Notificações e Sons
    if (showNotificationsDialog) {
        var highPriority by remember { mutableStateOf(prefs.highPriorityNotifications) }
        var sounds by remember { mutableStateOf(prefs.conversationSounds) }

        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Notificações & Sons", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notificações Heads-Up no Topo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Exibir banner prioritário no topo da tela", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = highPriority,
                            onCheckedChange = {
                                highPriority = it
                                prefs.highPriorityNotifications = it
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sons de Mensagem", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Tocar áudio ao enviar e receber mensagens", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = sounds,
                            onCheckedChange = {
                                sounds = it
                                prefs.conversationSounds = it
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showNotificationsDialog = false }) {
                    Text("Salvar")
                }
            }
        )
    }

    // DIALOG: Armazenamento e Dados
    if (showStorageDialog) {
        var wifiOnly by remember { mutableStateOf(prefs.mediaAutoDownloadWifiOnly) }
        var hdQuality by remember { mutableStateOf(prefs.hdMediaQuality) }
        var cacheSizeMb by remember {
            val cacheSize = context.cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
            mutableStateOf(String.format("%.1f MB", cacheSize / (1024f * 1024f)))
        }

        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Armazenamento & Mídia", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Cache do Aplicativo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Espaço ocupado temporário: $cacheSizeMb", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        context.cacheDir.deleteRecursively()
                                        context.cacheDir.mkdirs()
                                        withContext(Dispatchers.Main) {
                                            cacheSizeMb = "0.0 MB"
                                            Toast.makeText(context, "Cache limpo com sucesso!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Limpar", fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Download Apenas no Wi-Fi", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Economiza dados móveis em fotos e áudios", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = {
                                wifiOnly = it
                                prefs.mediaAutoDownloadWifiOnly = it
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mídia em Alta Definição (HD)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Preserva qualidade máxima ao enviar fotos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = hdQuality,
                            onCheckedChange = {
                                hdQuality = it
                                prefs.hdMediaQuality = it
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showStorageDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }

    // DIALOG: 12 Palavras Mnemônicas
    if (showRecoveryPhraseDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryPhraseDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Suas 12 Palavras Seguras", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Estas 12 palavras representam a sua chave mestra privada. Guarde-as com segurança:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = userRecoveryPhrase ?: "Chave de recuperação armazenada de forma segura na criação da conta.",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        userRecoveryPhrase?.let {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Palavras de Recuperação", it))
                            Toast.makeText(context, "Palavras copiadas!", Toast.LENGTH_SHORT).show()
                        }
                        showRecoveryPhraseDialog = false
                    }
                ) {
                    Text("Copiar Palavras")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryPhraseDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }

    // DIALOG: Aparelhos Conectados
    if (showDevicesDialog) {
        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        AlertDialog(
            onDismissRequest = { showDevicesDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aparelhos Conectados", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("Sessões ativas vinculadas ao seu ID:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Smartphone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(deviceName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(if (isConnected) "Sessão Ativa Agora • Online" else "Conectando...", fontSize = 12.sp, color = if (isConnected) SuccessGreen else DangerRed)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            Toast.makeText(context, "Todas as outras sessões foram encerradas.", Toast.LENGTH_LONG).show()
                            showDevicesDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Desconectar Todos os Outros Aparelhos", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevicesDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }

    // DIALOG: Gerenciar Contatos
    if (showManageContactsDialog) {
        AlertDialog(
            onDismissRequest = { showManageContactsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gerenciar Contatos", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                if (conversations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Nenhum contato salvo ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(conversations, key = { it.id }) { conv ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    UserAvatarView(
                                        avatarUrl = conv.otherAvatarUrl,
                                        displayName = conv.otherDisplayName,
                                        size = 40.dp,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(conv.otherDisplayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("@${conv.otherUsername}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                try { apiService.blockUser(mapOf("blockedUserId" to conv.otherUserId)) } catch (e: Exception) {}
                                            }
                                            Toast.makeText(context, "Contato bloqueado", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.Block, contentDescription = "Bloquear", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    apiService.removeContact(conv.otherUserId)
                                                    apiService.deleteConversation(conv.id)
                                                    apiService.clearConversation(conv.id)
                                                } catch (e: Exception) {}
                                                try {
                                                    db.contactDao().deleteContact(conv.otherUserId)
                                                    db.conversationDao().deleteConversation(conv.id)
                                                    db.messageDao().clearConversationMessages(conv.id)
                                                } catch (e: Exception) {}
                                            }
                                            Toast.makeText(context, "Contato excluído", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remover", tint = DangerRed, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageContactsDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }

    // DIALOG: Contatos Bloqueados
    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Contatos Bloqueados", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Nenhum contato bloqueado", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Quando você bloqueia um contato, ele não pode te enviar mensagens nem ligar para o seu ID Virtual.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedDialog = false }) { Text("OK") }
            }
        )
    }

    // DIALOG: Sobre o LivreChat
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sobre o LivreChat", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("LivreChat Decentralized v2.6 Pro", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Comunicação privada ponto-a-ponto sem censura.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("• Criptografia AES-256-GCM + RSA-2048", fontSize = 12.sp)
                            Text("• Identificador Virtual de 8 Dígitos", fontSize = 12.sp)
                            Text("• Chamadas de Áudio P2P WebRTC", fontSize = 12.sp)
                            Text("• Zero logs em servidores centrais", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) { Text("Fechar") }
            }
        )
    }

    // DIALOG: Sair da Conta
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Encerrar Sessão?") },
            text = { Text("Você precisará do seu ID ou usuário para entrar novamente neste aparelho.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        com.livrechat.app.service.LivreChatBackgroundService.stop(context)
                        connectionManager.disconnect()
                        prefs.clearAuth()
                        scope.launch(Dispatchers.IO) {
                            try { db.clearAllTables() } catch (e: Exception) {}
                        }
                        onLogout()
                    }
                ) {
                    Text("Sair", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // MAIN UI: Modern Futuristic Settings Design
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Configurações", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // HERO PROFILE BANNER (Design Único Cyber Stealth)
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar com indicador de edição e status
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clickable {
                                        newDisplayNameInput = displayName
                                        newBioInput = userBio
                                        newAvatarUrlInput = userAvatarUrl
                                        customWebUrlInput = if (userAvatarUrl.startsWith("http")) userAvatarUrl else ""
                                        showEditProfileDialog = true
                                    },
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                UserAvatarView(
                                    avatarBitmap = currentAvatarBitmap,
                                    avatarUrl = userAvatarUrl,
                                    displayName = displayName,
                                    size = 76.dp,
                                    fontSize = 28.sp
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = userBio,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // ID Virtual de 8 Dígitos (Badge Destacado)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.clickable {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("ID Virtual", username))
                                        Toast.makeText(context, "ID Virtual copiado!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "ID: $username",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Botão Editar Perfil em destaque
                        Button(
                            onClick = {
                                newDisplayNameInput = displayName
                                newBioInput = userBio
                                newAvatarUrlInput = userAvatarUrl
                                customWebUrlInput = if (userAvatarUrl.startsWith("http")) userAvatarUrl else ""
                                showEditProfileDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Editar Perfil & Foto", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // SEÇÃO 1: SEGURANÇA & PRIVACIDADE
            item {
                SettingsSectionHeader("Segurança & Criptografia P2P")
            }

            item {
                SettingsCard {
                    SettingsRowItem(
                        icon = Icons.Default.Shield,
                        title = "Segurança & Chaves Criptográficas",
                        subtitle = "Biometria, 12 palavras, bloqueio de prints e autodestruição",
                        onClick = { showSecurityDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsRowItem(
                        icon = Icons.Default.Lock,
                        title = "Privacidade & Ticks Azuis",
                        subtitle = "Confirmação de leitura, visto por último e contatos bloqueados",
                        onClick = { showPrivacyDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsRowItem(
                        icon = Icons.Default.Devices,
                        title = "Aparelhos Conectados",
                        subtitle = "Gerenciar sessões ativas com o seu ID",
                        onClick = { showDevicesDialog = true }
                    )
                }
            }

            // SEÇÃO 2: PERSONALIZAÇÃO & CORES
            item {
                SettingsSectionHeader("Personalização & Estilo Visual")
            }

            item {
                SettingsCard {
                    SettingsRowItem(
                        icon = Icons.Default.Palette,
                        title = "Aparência & Paleta de Cores",
                        subtitle = "Selecione entre 6 temas exclusivos (Safira, Esmeralda, Ametista...)",
                        onClick = { showThemeAndColorsDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsRowItem(
                        icon = Icons.Default.Notifications,
                        title = "Notificações & Sons",
                        subtitle = "Notificações heads-up no topo, toques e vibração",
                        onClick = { showNotificationsDialog = true }
                    )
                }
            }

            // SEÇÃO 3: DADOS & ARMAZENAMENTO
            item {
                SettingsSectionHeader("Mídia, Dados & Contatos")
            }

            item {
                SettingsCard {
                    SettingsRowItem(
                        icon = Icons.Default.Storage,
                        title = "Armazenamento & Mídia",
                        subtitle = "Limpar cache, qualidade de fotos em HD e controle de dados",
                        onClick = { showStorageDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsRowItem(
                        icon = Icons.Default.People,
                        title = "Gerenciar Contatos",
                        subtitle = "Bloquear ou excluir contatos da lista",
                        onClick = { showManageContactsDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsRowItem(
                        icon = Icons.Default.CloudSync,
                        title = "Backup das Conversas",
                        subtitle = "Backup criptografado local e restauração",
                        onClick = onNavigateToBackup
                    )
                }
            }

            // SEÇÃO 4: SOBRE & SAIR
            item {
                SettingsSectionHeader("Rede LivreChat & Conta")
            }

            item {
                SettingsCard {
                    SettingsRowItem(
                        icon = Icons.Default.Info,
                        title = "Sobre o LivreChat",
                        subtitle = "Versão v2.6 Pro Decentralized e licença",
                        onClick = { showAboutDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    SettingsRowItem(
                        icon = Icons.AutoMirrored.Filled.ExitToApp,
                        title = "Sair da Conta",
                        subtitle = "Encerrar sessão de forma segura neste aparelho",
                        iconColor = DangerRed,
                        titleColor = DangerRed,
                        onClick = { showLogoutDialog = true }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = iconColor.copy(alpha = 0.15f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = titleColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(20.dp))
    }
}
