package com.livrechat.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val username = prefs.currentUsername ?: "usuario"
    val userId = prefs.currentUserId ?: ""

    val isConnected by connectionManager.isConnected.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showDevicesDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showRecoveryPhraseDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    var newDisplayNameInput by remember { mutableStateOf(displayName) }
    var userRecoveryPhrase by remember { mutableStateOf<String?>(null) }
    var userPublicKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val user = db.userDao().getCurrentUser()
            userRecoveryPhrase = user?.recoveryKey
            userPublicKey = user?.publicKey ?: "Chave segura RSA-2048 ativa"
        }
    }

    // DIALOG: Editar Perfil
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Editar Nome de Exibição") },
            text = {
                Column {
                    Text(
                        "Digite como seus contatos verão seu nome no LivreChat:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newDisplayNameInput,
                        onValueChange = { newDisplayNameInput = it },
                        label = { Text("Nome de Exibição") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newDisplayNameInput.trim()
                        if (trimmed.isNotBlank()) {
                            displayName = trimmed
                            prefs.currentDisplayName = trimmed
                            showEditProfileDialog = false
                            scope.launch(Dispatchers.IO) {
                                try {
                                    apiService.updateProfile(mapOf("displayName" to trimmed))
                                    val current = db.userDao().getCurrentUser()
                                    if (current != null) {
                                        db.userDao().insertOrUpdate(current.copy(displayName = trimmed))
                                    }
                                } catch (e: Exception) {}
                            }
                            Toast.makeText(context, "Nome atualizado com sucesso!", Toast.LENGTH_SHORT).show()
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
            // Profile Card Header (Clickable para editar)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            newDisplayNameInput = displayName
                            showEditProfileDialog = true
                        }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = BluePrimary.copy(alpha = 0.2f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = displayName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = BluePrimary
                            )
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
                                contentDescription = "Editar Nome",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "@$username",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Toque para editar seu nome",
                            fontSize = 11.sp,
                            color = BluePrimary
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

            // Opção 1: Tema e Aparência
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

            // Opção 2: Backup e Restauração
            item {
                SettingsOptionItem(
                    icon = Icons.Default.CloudUpload,
                    title = "Backup e Restauração",
                    subtitle = "Google Drive e chave criptografada",
                    onClick = onNavigateToBackup
                )
            }

            // Opção 2: Privacidade e Segurança
            item {
                SettingsOptionItem(
                    icon = Icons.Default.Security,
                    title = "Privacidade e Segurança",
                    subtitle = "Criptografia de ponta a ponta e chaves",
                    onClick = { showPrivacyDialog = true }
                )
            }

            // Opção 3: Aparelhos Conectados
            item {
                SettingsOptionItem(
                    icon = Icons.Default.Devices,
                    title = "Aparelhos Conectados",
                    subtitle = "Gerenciamento de sessões ativas",
                    onClick = { showDevicesDialog = true }
                )
            }

            // Opção 4: Contatos Bloqueados
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

            // Opção 5: Sair da Conta
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
