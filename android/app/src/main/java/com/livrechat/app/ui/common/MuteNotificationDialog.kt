package com.livrechat.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MuteNotificationDialog(
    conversationTitle: String = "",
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var selectedOption by remember { mutableIntStateOf(0) } // 0: 8 horas, 1: 1 semana, 2: Sempre

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Silenciar notificações",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (conversationTitle.isNotBlank()) {
                        "Silenciar notificações de mensagens para \"$conversationTitle\"."
                    } else {
                        "Os outros participantes não saberão que você silenciou esta conversa. Você continuará recebendo as mensagens."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val options = listOf(
                    "8 horas" to (8 * 3600 * 1000L),
                    "1 semana" to (7 * 24 * 3600 * 1000L),
                    "Sempre" to -1L
                )

                options.forEachIndexed { index, (label, _) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = index }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedOption == index),
                            onClick = { selectedOption = index },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val duration = when (selectedOption) {
                        0 -> System.currentTimeMillis() + (8 * 3600 * 1000L)
                        1 -> System.currentTimeMillis() + (7 * 24 * 3600 * 1000L)
                        else -> -1L
                    }
                    onConfirm(duration)
                }
            ) {
                Text("OK", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCELAR", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
