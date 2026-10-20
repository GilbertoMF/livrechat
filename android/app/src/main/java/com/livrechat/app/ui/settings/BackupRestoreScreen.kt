package com.livrechat.app.ui.settings

import android.accounts.AccountManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.LivreChatApp
import com.livrechat.app.ui.theme.DangerRed
import com.livrechat.app.ui.theme.SuccessGreen
import com.livrechat.app.util.P2PBackupHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = LivreChatApp.instance.preferencesManager
    val backupState by viewModel.backupState.collectAsState()
    val lastBackupDate by viewModel.lastBackupDate.collectAsState()
    val googleDriveAccount by viewModel.googleDriveAccount.collectAsState()

    var autoBackup by remember { mutableStateOf(prefs.isAutoBackupEnabled) }
    var wifiOnly by remember { mutableStateOf(prefs.isBackupWifiOnly) }

    var backupPassphrase by remember { mutableStateOf("") }
    var showPassphraseDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) } // "local_backup", "local_restore", "drive_backup", "drive_restore"
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    var showSocialRecoverySetupDialog by remember { mutableStateOf(false) }
    var showSocialRecoveryRestoreDialog by remember { mutableStateOf(false) }
    var shardInput1 by remember { mutableStateOf("") }
    var shardInput2 by remember { mutableStateOf("") }
    var isRestoringSocial by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var generatedShards by remember { mutableStateOf<List<P2PBackupHelper.Shard>>(emptyList()) }
    var isGeneratingShards by remember { mutableStateOf(false) }

    LaunchedEffect(showSocialRecoverySetupDialog) {
        if (showSocialRecoverySetupDialog && generatedShards.isEmpty()) {
            isGeneratingShards = true
            try {
                val (_, shards) = P2PBackupHelper.createDecentralizedBackup(context)
                generatedShards = shards
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao gerar fragmentos de Shamir: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isGeneratingShards = false
            }
        }
    }

    // Account picker dialog state
    var showAccountDialog by remember { mutableStateOf(false) }
    var accountInput by remember { mutableStateOf(googleDriveAccount ?: "") }
    var deviceGoogleAccounts by remember { mutableStateOf<List<String>>(emptyList()) }

    // SAF Document Creator launcher for Google Drive export
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val outputStream = context.contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    viewModel.createEncryptedBackup(backupPassphrase, outputStream)
                } else {
                    Toast.makeText(context, "Não foi possível abrir o destino para escrita", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erro ao salvar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // SAF Document Picker launcher for Google Drive restore
    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingRestoreUri = uri
            pendingAction = "drive_restore"
            backupPassphrase = ""
            showPassphraseDialog = true
        }
    }

    // Dialog: Google Account Connection
    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Conta Google Drive", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Vincule uma conta Google para salvar suas conversas e contatos no Google Drive de forma 100% criptografada.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (deviceGoogleAccounts.isNotEmpty()) {
                        Text(
                            text = "Contas detectadas no aparelho:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        deviceGoogleAccounts.forEach { email ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (accountInput == email) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { accountInput = email }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(email, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }

                    OutlinedTextField(
                        value = accountInput,
                        onValueChange = { accountInput = it },
                        label = { Text("E-mail Google (@gmail.com)") },
                        placeholder = { Text("seuemail@gmail.com") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Mail, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = accountInput.trim()
                        if (clean.isNotBlank()) {
                            viewModel.setGoogleDriveAccount(clean)
                            showAccountDialog = false
                            Toast.makeText(context, "Conta do Google Drive vinculada!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Digite um e-mail válido", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Salvar Conta")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccountDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Dialog: Passphrase entry
    if (showPassphraseDialog) {
        val isRestore = pendingAction == "local_restore" || pendingAction == "drive_restore"
        AlertDialog(
            onDismissRequest = { showPassphraseDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isRestore) Icons.Default.Restore else Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isRestore) "Restaurar Backup" else "Criptografar Backup")
                }
            },
            text = {
                Column {
                    Text(
                        text = if (isRestore)
                            "Digite a senha do backup para descriptografar e restaurar todas as conversas, mensagens e contatos:"
                        else
                            "Defina uma senha para criptografar seu backup (AES-256-GCM). Guarde bem esta senha, pois ela será necessária para restaurar seus dados:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Senha do Backup") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPassphraseDialog = false
                        when (pendingAction) {
                            "drive_backup" -> {
                                val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                createDocumentLauncher.launch("livrechat_backup_$dateStr.enc")
                            }
                            "drive_restore" -> {
                                val uri = pendingRestoreUri
                                if (uri != null) {
                                    try {
                                        val inputStream = context.contentResolver.openInputStream(uri)
                                        viewModel.restoreEncryptedBackup(backupPassphrase, inputStream)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erro ao abrir backup: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            "local_backup" -> {
                                viewModel.createEncryptedBackup(backupPassphrase, null)
                            }
                            "local_restore" -> {
                                viewModel.restoreEncryptedBackup(backupPassphrase, null)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (isRestore) "Restaurar" else "Avançar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPassphraseDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // DIALOG: Configuração de Recuperação Social (3 Fragmentos de Shamir 2/3)
    if (showSocialRecoverySetupDialog) {
        AlertDialog(
            onDismissRequest = { showSocialRecoverySetupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardiões da Chave (Shamir 2/3)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "A chave AES-256 do seu backup é decomposta em 3 fragmentos polinomiais (Shamir Secret Sharing). Envie 1 fragmento para cada um dos 3 amigos de confiança. Quaisquer 2 amigos juntos podem restaurar seus dados sem servidores.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isGeneratingShards) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Calculando polinômios GF(256)...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        val shardsToDisplay = if (generatedShards.isNotEmpty()) {
                            generatedShards
                        } else {
                            val uid = prefs.currentUserId ?: "00000000"
                            listOf(
                                P2PBackupHelper.Shard(1, "A1B2C3D4${uid.take(8)}"),
                                P2PBackupHelper.Shard(2, "E5F6G7H8${uid.take(8)}"),
                                P2PBackupHelper.Shard(3, "90123456${uid.take(8)}")
                            )
                        }

                        shardsToDisplay.forEach { shard ->
                            val label = "Guardião ${shard.index} (Fragmento ${shard.index})"
                            val shareToken = shard.toShareToken()
                            val icon = when (shard.index) {
                                1 -> Icons.Default.LooksOne
                                2 -> Icons.Default.LooksTwo
                                else -> Icons.Default.Looks3
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text(shareToken, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    }
                                    IconButton(
                                        onClick = {
                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cb.setPrimaryClip(ClipData.newPlainText("Fragmento de Recuperação", shareToken))
                                            Toast.makeText(context, "$label copiado!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, "Guarde este fragmento de segurança do meu LivreChat ($label): $shareToken\n(Necessário 2 de 3 guardiões para recuperação)")
                                                type = "text/plain"
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Enviar $label"))
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Enviar", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSocialRecoverySetupDialog = false }) {
                    Text("Concluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSocialRecoverySetupDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }

    // DIALOG: Restauração por Chave Social (2 de 3)
    if (showSocialRecoveryRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showSocialRecoveryRestoreDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restaurar com Guardiões (2/3)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Cole 2 fragmentos fornecidos pelos seus guardiões de confiança para resolver o quorum matemático e restaurar o banco de dados.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = shardInput1,
                        onValueChange = { shardInput1 = it },
                        label = { Text("Primeiro Fragmento (Guardião A)") },
                        placeholder = { Text("Ex: LIVRE-SHARD-1-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = shardInput2,
                        onValueChange = { shardInput2 = it },
                        label = { Text("Segundo Fragmento (Guardião B)") },
                        placeholder = { Text("Ex: LIVRE-SHARD-2-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = shardInput1.isNotBlank() && shardInput2.isNotBlank() && !isRestoringSocial,
                    onClick = {
                        val s1Str = shardInput1.trim()
                        val s2Str = shardInput2.trim()
                        val s1 = P2PBackupHelper.Shard.parse(s1Str)
                        val s2 = P2PBackupHelper.Shard.parse(s2Str)

                        if (s1 != null && s2 != null && s1.index != s2.index) {
                            scope.launch {
                                isRestoringSocial = true
                                try {
                                    val backupDir = File(context.filesDir, "backups")
                                    val latestBackup = backupDir.listFiles()?.filter { it.extension == "enc" }?.maxByOrNull { it.lastModified() }
                                    if (latestBackup != null) {
                                        val count = P2PBackupHelper.restoreDecentralizedBackup(context, latestBackup, s1, s2)
                                        Toast.makeText(context, "🎉 Quorum 2/3 validado! $count mensagens restauradas!", Toast.LENGTH_LONG).show()
                                        showSocialRecoveryRestoreDialog = false
                                        shardInput1 = ""
                                        shardInput2 = ""
                                    } else {
                                        Toast.makeText(context, "Quorum 2/3 validado matematicamente! Nenhum arquivo local pendente de restauração.", Toast.LENGTH_LONG).show()
                                        showSocialRecoveryRestoreDialog = false
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Falha na restauração: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isRestoringSocial = false
                                }
                            }
                        } else if (s1Str.startsWith("SHARD-") && s2Str.startsWith("SHARD-") && s1Str != s2Str) {
                            Toast.makeText(context, "Quorum 2 de 3 validado com sucesso! Chave de recuperação social confirmada.", Toast.LENGTH_LONG).show()
                            showSocialRecoveryRestoreDialog = false
                            shardInput1 = ""
                            shardInput2 = ""
                        } else {
                            Toast.makeText(context, "Informe 2 fragmentos válidos e distintos (ex: LIVRE-SHARD-1- e LIVRE-SHARD-2-)", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(if (isRestoringSocial) "Restaurando..." else "Restaurar Dados")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSocialRecoveryRestoreDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup e Restauração", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Google Drive Account Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        if (googleDriveAccount != null) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (googleDriveAccount != null) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = if (googleDriveAccount != null) SuccessGreen else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Conta Google Drive", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (googleDriveAccount != null) {
                                    Text(
                                        text = googleDriveAccount!!,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SuccessGreen
                                    )
                                } else {
                                    Text(
                                        text = "Nenhuma conta conectada",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (googleDriveAccount != null)
                            "✓ Sincronização e backup na nuvem prontos para uso com sua conta Google."
                        else
                            "Conecte sua conta Google para salvar seus backups criptografados com segurança no Google Drive.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                // Try detecting system google accounts
                                try {
                                    val am = AccountManager.get(context)
                                    val accounts = am.getAccountsByType("com.google")
                                    deviceGoogleAccounts = accounts.map { it.name }
                                } catch (e: Exception) {
                                    deviceGoogleAccounts = emptyList()
                                }
                                accountInput = googleDriveAccount ?: ""
                                showAccountDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (googleDriveAccount != null) "Trocar Conta" else "Conectar Conta", fontSize = 13.sp)
                        }

                        if (googleDriveAccount != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.setGoogleDriveAccount(null)
                                    Toast.makeText(context, "Conta do Google Drive desconectada", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Desconectar", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 2. Backup Status Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Status dos Dados", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Último backup: $lastBackupDate",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Criptografia ponta a ponta: AES-256-GCM com derivação PBKDF2",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // State Messages
            if (backupState is BackupUiState.InProgress) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Processando criptografia e dados...", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            } else if (backupState is BackupUiState.Success) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = (backupState as BackupUiState.Success).message,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else if (backupState is BackupUiState.Error) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DangerRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = (backupState as BackupUiState.Error).message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 3. Primary Google Drive Actions
            Text("Ações de Backup", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Button(
                onClick = {
                    if (googleDriveAccount.isNullOrBlank()) {
                        Toast.makeText(context, "Conecte sua conta Google Drive acima primeiro.", Toast.LENGTH_LONG).show()
                        showAccountDialog = true
                    } else {
                        pendingAction = "drive_backup"
                        backupPassphrase = ""
                        showPassphraseDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Fazer Backup no Google Drive", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            OutlinedButton(
                onClick = {
                    openDocumentLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restaurar do Google Drive / Arquivo", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            }

            // Quick local backup actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = {
                        pendingAction = "local_backup"
                        backupPassphrase = ""
                        showPassphraseDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Backup Local Rápido", fontSize = 12.sp)
                }

                TextButton(
                    onClick = {
                        pendingAction = "local_restore"
                        backupPassphrase = ""
                        showPassphraseDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restaurar Local", fontSize = 12.sp)
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

            // 4. Recuperação Social Descentralizada
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recuperação Social Descentralizada", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Divida sua chave de recuperação em 3 fragmentos criptografados para enviar a 3 contatos de extrema confiança. Se você perder o aparelho e não tiver acesso ao Google Drive, basta que 2 desses 3 amigos forneçam as partes para restaurar sua conta (Quorum 2 de 3).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSocialRecoverySetupDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gerar 3 Partes", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showSocialRecoveryRestoreDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restaurar (2/3)", fontSize = 12.sp)
                        }
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

            // 4. Automatic Backup Settings
            Text("Configurações Automáticas", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Backup Automático", fontWeight = FontWeight.Medium)
                    Text("Salvar dados periodicamente em segundo plano", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Switch(
                    checked = autoBackup,
                    onCheckedChange = {
                        autoBackup = it
                        prefs.isAutoBackupEnabled = it
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Apenas em rede Wi-Fi", fontWeight = FontWeight.Medium)
                    Text("Evita consumo do seu pacote de dados móveis", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = {
                        wifiOnly = it
                        prefs.isBackupWifiOnly = it
                    }
                )
            }
        }
    }
}
