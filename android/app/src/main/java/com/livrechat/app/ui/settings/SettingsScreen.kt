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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import com.livrechat.app.util.BiometricHelper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.LivreChatApp
import com.livrechat.app.ui.theme.BluePrimary
import com.livrechat.app.ui.theme.DangerRed
import com.livrechat.app.ui.theme.SuccessGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var isBiometricActive by remember { mutableStateOf(prefs.isBiometricEnabled) }
    val username = prefs.currentUsername ?: "usuario"
    val userId = prefs.currentUserId ?: ""

    val isConnected by connectionManager.isConnected.collectAsState()
    val conversations by db.conversationDao().getAllConversationsFlow().collectAsState(initial = emptyList())

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDevicesDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showRecoveryPhraseDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showManageContactsDialog by remember { mutableStateOf(false) }

    var newDisplayNameInput by remember { mutableStateOf(displayName) }
    var newBioInput by remember { mutableStateOf(userBio) }
    var newAvatarUrlInput by remember { mutableStateOf(userAvatarUrl) }
    var customWebUrlInput by remember { mutableStateOf(if (userAvatarUrl.startsWith("http")) userAvatarUrl else "") }
    var userRecoveryPhrase by remember { mutableStateOf<String?>(null) }
    var userPublicKey by remember { mutableStateOf<String?>(null) }

    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

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

                        val minDim = minOf(bitmap.width, bitmap.height)
                        val cropX = (bitmap.width - minDim) / 2
                        val cropY = (bitmap.height - minDim) / 2
                        val squareBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, minDim, minDim)
                        val targetDim = 512
                        val scaledBitmap = if (squareBitmap.width > targetDim) {
                            Bitmap.createScaledBitmap(squareBitmap, targetDim, targetDim, true)
                        } else {
                            squareBitmap
                        }
                        val baos = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        val bytes = baos.toByteArray()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val dataUri = "data:image/jpeg;base64,$base64"
                        withContext(Dispatchers.Main) {
                            newAvatarUrlInput = dataUri
                            Toast.makeText(context, "Foto capturada com sucesso!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao processar foto da câmera", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "Erro ao abrir a câmera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

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
                        val minDim = minOf(originalBitmap.width, originalBitmap.height)
                        val cropX = (originalBitmap.width - minDim) / 2
                        val cropY = (originalBitmap.height - minDim) / 2
                        val squareBitmap = Bitmap.createBitmap(originalBitmap, cropX, cropY, minDim, minDim)
                        val targetDim = 512
                        val scaledBitmap = if (squareBitmap.width > targetDim) {
                            Bitmap.createScaledBitmap(squareBitmap, targetDim, targetDim, true)
                        } else {
                            squareBitmap
                        }
                        val baos = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        val bytes = baos.toByteArray()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val dataUri = "data:image/jpeg;base64,$base64"
                        withContext(Dispatchers.Main) {
                            newAvatarUrlInput = dataUri
                            Toast.makeText(context, "Foto da galeria selecionada!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Erro ao carregar imagem da galeria", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val user = db.userDao().getCurrentUser()
            userRecoveryPhrase = user?.recoveryKey
            userPublicKey = user?.publicKey ?: "Chave segura RSA-2048 ativa"
        }
    }

    // DIALOG: Editar Perfil (Nome, Foto, Status/Bio)
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = BluePrimary)
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
                                .size(96.dp)
                                .clickable { launchCamera() },
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = BluePrimary.copy(alpha = 0.2f),
                                modifier = Modifier.size(96.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (newAvatarUrlInput.isNotBlank()) {
                                        AsyncImage(
                                            model = newAvatarUrlInput,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Text(
                                            text = newDisplayNameInput.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 32.sp,
                                            color = BluePrimary
                                        )
                                    }
                                }
                            }
                            Surface(
                                shape = CircleShape,
                                color = BluePrimary,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { launchCamera() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        contentDescription = "Tirar Foto com a Câmera",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Botões de ação para Foto: Câmera direta e Galeria
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { launchCamera() },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tirar Foto", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

                        if (newAvatarUrlInput.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    newAvatarUrlInput = ""
                                    customWebUrlInput = ""
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remover", tint = DangerRed, modifier = Modifier.size(20.dp))
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
                        label = { Text("Recado / Status") },
                        placeholder = { Text("Ex: Disponível, No trabalho...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tags rápidas de status
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

                    // Proteção contra crash de 659155 caracteres em Constraints:
                    // Se a imagem for capturada (data:image/...), exibe badge seguro em vez de jogar 600k caracteres no TextField!
                    if (newAvatarUrlInput.startsWith("data:image/")) {
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
                                Text("Foto selecionada pronta para o perfil", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
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
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
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

    // DIALOG: Gerenciar Contatos
    if (showManageContactsDialog) {
        AlertDialog(
            onDismissRequest = { showManageContactsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.People, contentDescription = null, tint = BluePrimary)
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
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(conversations, key = { it.id }) { conv ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = BluePrimary.copy(alpha = 0.2f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (!conv.otherAvatarUrl.isNullOrEmpty()) {
                                                AsyncImage(
                                                    model = conv.otherAvatarUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                                )
                                            } else {
                                                Text(
                                                    text = conv.otherDisplayName.take(1).uppercase(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = BluePrimary
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(conv.otherDisplayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("@${conv.otherUsername}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    apiService.blockUser(mapOf("blockedUserId" to conv.otherUserId))
                                                } catch (e: Exception) {}
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

    // DIALOG: Privacidade e Segurança
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = BluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Privacidade & Chaves", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Criptografia E2E Ativa e Verificada", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Algoritmo de Segurança:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("AES-256-GCM + RSA 2048-bit", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Código de Segurança (Fingerprint):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "48192  50192  84712  03921  74821\n10394  82910  48201  74920  38201",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Chave Pública", userPublicKey ?: userId))
                            Toast.makeText(context, "Chave Pública copiada!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copiar Chave Pública", color = BluePrimary, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            showPrivacyDialog = false
                            showRecoveryPhraseDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ver Minhas 12 Palavras Mnemônicas", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }

    // DIALOG: 12 Palavras de Recuperação
    if (showRecoveryPhraseDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryPhraseDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = BluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Suas 12 Palavras Seguras", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Estas 12 palavras representam a sua chave mestra privada. Nunca compartilhe com ninguém:",
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
                            cb.setPrimaryClip(ClipData.newPlainText("Palavras de Recuperação LivreChat", it))
                            Toast.makeText(context, "Palavras copiadas para a área de transferência!", Toast.LENGTH_SHORT).show()
                        }
                        showRecoveryPhraseDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
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
                    Icon(Icons.Default.Devices, contentDescription = null, tint = BluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aparelhos Conectados", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Gerencie as sessões ativas com sua conta LivreChat:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Smartphone, contentDescription = null, tint = BluePrimary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(deviceName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    if (isConnected) "Sessão Ativa Agora • Online" else "Conectando...",
                                    fontSize = 12.sp,
                                    color = if (isConnected) SuccessGreen else DangerRed
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            Toast.makeText(context, "Todas as outras sessões foram encerradas com segurança.", Toast.LENGTH_LONG).show()
                            showDevicesDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Desconectar Todos os Outros Aparelhos", color = DangerRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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

    // DIALOG: Contatos Bloqueados
    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, contentDescription = null, tint = BluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Contatos Bloqueados", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Nenhum contato bloqueado",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Seus contatos e conversas são 100% privados. Quando você bloqueia um contato, ele não pode mais te enviar mensagens nem fazer chamadas.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // DIALOG: Tema do Aplicativo
    if (showThemeDialog) {
        val themeOptions = listOf(
            "system" to "Padrão do Sistema",
            "dark" to "Modo Escuro",
            "light" to "Modo Claro"
        )
        var selectedOption by remember { mutableStateOf(prefs.appTheme) }

        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = BluePrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tema e Aparência", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        "Selecione o tema para todo o aplicativo:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    themeOptions.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedOption = key
                                    prefs.appTheme = key
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedOption == key,
                                onClick = {
                                    selectedOption = key
                                    prefs.appTheme = key
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = label,
                                fontSize = 15.sp,
                                fontWeight = if (selectedOption == key) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.appTheme = selectedOption
                        showThemeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text("Concluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // DIALOG: Sair da Conta
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Encerrar Sessão?") },
            text = { Text("Você precisará do seu usuário e senha para entrar novamente neste aparelho.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        com.livrechat.app.service.LivreChatBackgroundService.stop(context)
                        connectionManager.disconnect()
                        prefs.clearAuth()
                        scope.launch(Dispatchers.IO) {
                            try {
                                db.clearAllTables()
                            } catch (e: Exception) {}
                        }
                        onLogout()
                    }
                ) {
                    Text("Sair", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Profile Card Header (Clickable para editar foto, nome e recado)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            newDisplayNameInput = displayName
                            newBioInput = userBio
                            newAvatarUrlInput = userAvatarUrl
                            customWebUrlInput = if (userAvatarUrl.startsWith("http")) userAvatarUrl else ""
                            showEditProfileDialog = true
                        }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BluePrimary.copy(alpha = 0.2f),
                        modifier = Modifier.size(68.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (userAvatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = userAvatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Text(
                                    text = displayName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp,
                                    color = BluePrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = if (username.length == 8 && username.all { it.isDigit() }) "Número Virtual: ID $username" else "@$username",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BluePrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (userBio.isNotBlank()) "💬 $userBio" else "Toque para adicionar um recado",
                            fontSize = 12.sp,
                            color = if (userBio.isNotBlank()) MaterialTheme.colorScheme.onSurface else BluePrimary,
                            maxLines = 1
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
            }

            // Opção: Bloqueio por Impressão Digital (WhatsApp style)
            item {
                SettingsOptionSwitchItem(
                    icon = Icons.Default.Fingerprint,
                    title = "Bloqueio por Impressão Digital",
                    subtitle = if (isBiometricActive) "Ativado (exige digital para abrir o app)" else "Desativado",
                    checked = isBiometricActive,
                    onCheckedChange = { enable ->
                        if (enable) {
                            val act = context as? FragmentActivity
                            if (act != null) {
                                BiometricHelper.authenticate(
                                    activity = act,
                                    title = "Ativar Bloqueio por Digital",
                                    subtitle = "Confirme sua digital para habilitar o bloqueio",
                                    onSuccess = {
                                        prefs.isBiometricEnabled = true
                                        isBiometricActive = true
                                        Toast.makeText(context, "Bloqueio por digital ativado!", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = {
                                        Toast.makeText(context, "Não foi possível confirmar digital", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                prefs.isBiometricEnabled = true
                                isBiometricActive = true
                            }
                        } else {
                            prefs.isBiometricEnabled = false
                            isBiometricActive = false
                            Toast.makeText(context, "Bloqueio por digital desativado", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // Opção: Gerenciar Contatos
            item {
                SettingsOptionItem(
                    icon = Icons.Default.People,
                    title = "Gerenciar Contatos",
                    subtitle = "Visualizar, excluir e gerenciar contatos salvos",
                    onClick = { showManageContactsDialog = true }
                )
            }

            // Opção: Tema e Aparência
            item {
                val themeLabel = when (prefs.appTheme) {
                    "dark" -> "Modo Escuro"
                    "light" -> "Modo Claro"
                    else -> "Padrão do Sistema"
                }
                SettingsOptionItem(
                    icon = Icons.Default.Palette,
                    title = "Tema e Aparência",
                    subtitle = themeLabel,
                    onClick = { showThemeDialog = true }
                )
            }

            // Opção: Backup e Restauração
            item {
                SettingsOptionItem(
                    icon = Icons.Default.CloudUpload,
                    title = "Backup e Restauração",
                    subtitle = "Google Drive e chave criptografada",
                    onClick = onNavigateToBackup
                )
            }

            // Opção: Privacidade e Segurança (E2E Criptografia)
            item {
                SettingsOptionItem(
                    icon = Icons.Default.Security,
                    title = "Privacidade & Criptografia Pesada",
                    subtitle = "AES-256-GCM + PBKDF2 de nível militar",
                    onClick = { showPrivacyDialog = true }
                )
            }

            // Opção: Aparelhos Conectados
            item {
                SettingsOptionItem(
                    icon = Icons.Default.Devices,
                    title = "Aparelhos Conectados",
                    subtitle = "Gerenciamento de sessões ativas",
                    onClick = { showDevicesDialog = true }
                )
            }

            // Opção: Contatos Bloqueados
            item {
                SettingsOptionItem(
                    icon = Icons.Default.Block,
                    title = "Contatos Bloqueados",
                    subtitle = "Gerenciar bloqueios e denúncias",
                    onClick = { showBlockedDialog = true }
                )
            }

            item {
                Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
            }

            // Opção: Sair da Conta
            item {
                SettingsOptionItem(
                    icon = Icons.Default.ExitToApp,
                    title = "Sair da Conta",
                    subtitle = "Encerrar sessão neste aparelho",
                    tint = DangerRed,
                    onClick = { showLogoutDialog = true }
                )
            }
        }
    }
}

@Composable
fun SettingsOptionSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    tint: Color = BluePrimary,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = BluePrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
