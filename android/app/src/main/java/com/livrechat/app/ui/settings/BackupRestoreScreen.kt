package com.livrechat.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.LivreChatApp
import com.livrechat.app.ui.theme.BluePrimary
import com.livrechat.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val prefs = LivreChatApp.instance.preferencesManager
    val backupState by viewModel.backupState.collectAsState()
    val lastBackupDate by viewModel.lastBackupDate.collectAsState()

    var autoBackup by remember { mutableStateOf(prefs.isAutoBackupEnabled) }
    var wifiOnly by remember { mutableStateOf(prefs.isBackupWifiOnly) }

    var backupPassphrase by remember { mutableStateOf("") }
    var showPassphraseDialog by remember { mutableStateOf(false) }
    var isRestoreAction by remember { mutableStateOf(false) }

    if (showPassphraseDialog) {
        AlertDialog(
            onDismissRequest = { showPassphraseDialog = false },
            title = {
                Text(if (isRestoreAction) "Restaurar Backup" else "Criar Backup Criptografado")
            },
            text = {
                Column {
                    Text(
                        text = if (isRestoreAction)
                            "Digite a senha definida no momento da criação do backup:"
                        else
                            "Defina uma senha forte para criptografar seu backup antes do envio. Sem esta senha, os dados NÃO poderão ser recuperados.",
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
                        if (isRestoreAction) {
                            viewModel.restoreEncryptedBackup(backupPassphrase)
                        } else {
                            viewModel.createEncryptedBackup(backupPassphrase)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
                ) {
                    Text(if (isRestoreAction) "Restaurar" else "Criptografar e Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPassphraseDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup e Restauração") },
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
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Google Drive e Criptografia Local", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Último backup: $lastBackupDate",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Tamanho estimado: ~45 KB (Criptografado AES-256-GCM)",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // State Messages
            if (backupState is BackupUiState.InProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BluePrimary)
            } else if (backupState is BackupUiState.Success) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = (backupState as BackupUiState.Success).message,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else if (backupState is BackupUiState.Error) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = (backupState as BackupUiState.Error).message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Action Buttons
            Button(
                onClick = {
                    isRestoreAction = false
                    backupPassphrase = ""
                    showPassphraseDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BluePrimary)
            ) {
                Icon(Icons.Default.Backup, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Fazer Backup Agora", fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = {
                    isRestoreAction = true
                    backupPassphrase = ""
                    showPassphraseDialog = true
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restaurar Dados do Backup", fontWeight = FontWeight.SemiBold)
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Settings toggles
            Text("Configurações Automáticas", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Backup Automático", fontWeight = FontWeight.Medium)
                    Text("Salvar periodicamente em segundo plano", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
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
