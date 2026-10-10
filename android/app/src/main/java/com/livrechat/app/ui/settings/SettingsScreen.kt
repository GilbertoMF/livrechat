package com.livrechat.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrechat.app.LivreChatApp
import com.livrechat.app.ui.theme.BluePrimary
import com.livrechat.app.ui.theme.DangerRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onLogout: () -> Unit
) {
    val prefs = LivreChatApp.instance.preferencesManager
    val displayName = prefs.currentDisplayName ?: "Usuário"
    val username = prefs.currentUsername ?: "usuario"

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Encerrar Sessão?") },
            text = { Text("Você precisará da sua senha para entrar novamente.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
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
            // Profile Card Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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

                    Column {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "@$username",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
            }

            item {
                SettingsOptionItem(
                    icon = Icons.Default.CloudUpload,
                    title = "Backup e Restauração",
                    subtitle = "Google Drive e chave criptografada",
                    onClick = onNavigateToBackup
                )
            }

            item {
                SettingsOptionItem(
                    icon = Icons.Default.Security,
                    title = "Privacidade e Segurança",
                    subtitle = "Criptografia de ponta a ponta e chaves",
                    onClick = { /* Privacy details */ }
                )
            }

            item {
                SettingsOptionItem(
                    icon = Icons.Default.Devices,
                    title = "Aparelhos Conectados",
                    subtitle = "Gerenciamento de sessões ativas",
                    onClick = { /* Devices */ }
                )
            }

            item {
                SettingsOptionItem(
                    icon = Icons.Default.Block,
                    title = "Contatos Bloqueados",
                    subtitle = "Gerenciar bloqueios e denúncias",
                    onClick = { /* Blocked */ }
                )
            }

            item {
                Divider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
            }

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
    tint: androidx.compose.ui.graphics.Color = BluePrimary,
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
