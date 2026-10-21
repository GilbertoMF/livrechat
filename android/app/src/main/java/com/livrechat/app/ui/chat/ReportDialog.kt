package com.livrechat.app.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.livrechat.app.LivreChatApp
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.ui.theme.DangerRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReportDialog(
    conversationId: String,
    otherUserId: String,
    otherDisplayName: String,
    messages: List<MessageEntity>,
    reportedMessage: MessageEntity? = null,
    onDismiss: () -> Unit,
    onReportSubmitted: (blocked: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = LivreChatApp.instance

    val reasons = listOf(
        "Spam ou mensagens indesejadas",
        "Golpe, fraude ou falsidade ideológica",
        "Assédio, ameaça ou ofensas",
        "Conteúdo explícito ou violento",
        "Outro motivo"
    )

    var selectedReason by remember { mutableStateOf(reasons[0]) }
    var detailsText by remember { mutableStateOf("") }
    var blockAndClearChat by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 20.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header com Ícone de Alerta
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DangerRed.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Report,
                                contentDescription = null,
                                tint = DangerRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = if (reportedMessage != null) "Denunciar mensagem?" else "Denunciar $otherDisplayName?",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Ajude-nos a manter a comunidade segura",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Aviso Idêntico ao WhatsApp
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "As últimas 5 mensagens desta conversa serão encaminhadas ao LivreChat para verificação. O contato não saberá quem realizou a denúncia.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Seletor de Motivos
                Text(
                    text = "Selecione o motivo:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    reasons.forEach { reason ->
                        val isSelected = selectedReason == reason
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = reason }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedReason = reason },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = reason,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Campo opcional de detalhes
                OutlinedTextField(
                    value = detailsText,
                    onValueChange = { if (it.length <= 300) detailsText = it },
                    placeholder = { Text("Descreva o que aconteceu (opcional)...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Checkbox Estilo WhatsApp: Bloquear e apagar conversa
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { blockAndClearChat = !blockAndClearChat }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = blockAndClearChat,
                        onCheckedChange = { blockAndClearChat = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = DangerRed
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Bloquear contato e apagar as mensagens da conversa",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Botões de Ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting
                    ) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            isSubmitting = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    // 1. Preparar snapshot das últimas 5 mensagens
                                    val last5 = messages.takeLast(5).map { m ->
                                        val displayContent = when (m.messageType) {
                                            "image" -> "[Foto / Imagem]"
                                            "video" -> "[Vídeo]"
                                            "audio" -> "[Mensagem de Voz]"
                                            "file" -> "[Arquivo / Documento]"
                                            else -> m.content
                                        }
                                        mapOf(
                                            "id" to m.id,
                                            "senderId" to m.senderId,
                                            "isOutgoing" to m.isOutgoing,
                                            "messageType" to m.messageType,
                                            "content" to displayContent,
                                            "timestamp" to m.createdAt
                                        )
                                    }

                                    // 2. Enviar denúncia para o servidor
                                    val reportPayload = mapOf<String, Any?>(
                                        "reportedUserId" to otherUserId,
                                        "conversationId" to conversationId,
                                        "messageId" to reportedMessage?.id,
                                        "reason" to selectedReason,
                                        "details" to detailsText.trim().ifBlank { null },
                                        "recentMessages" to last5,
                                        "blockUser" to blockAndClearChat
                                    )

                                    val response = app.apiClient.apiService.createReport(reportPayload)

                                    // 3. Se selecionou bloquear
                                    if (blockAndClearChat) {
                                        try {
                                            app.apiClient.apiService.blockUser(mapOf("userId" to otherUserId))
                                        } catch (_: Exception) {}
                                        try {
                                            app.database.messageDao().clearConversationMessages(conversationId)
                                            app.database.conversationDao().deleteById(conversationId)
                                        } catch (_: Exception) {}
                                    }

                                    withContext(Dispatchers.Main) {
                                        isSubmitting = false
                                        if (response.isSuccessful) {
                                            Toast.makeText(
                                                context,
                                                "Denúncia enviada. Obrigado por ajudar a proteger a comunidade.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            onReportSubmitted(blockAndClearChat)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Falha ao enviar denúncia (${response.code()})",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isSubmitting = false
                                        Toast.makeText(
                                            context,
                                            "Erro ao conectar com o servidor: ${e.localizedMessage ?: "Tente novamente"}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DangerRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Denunciar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
