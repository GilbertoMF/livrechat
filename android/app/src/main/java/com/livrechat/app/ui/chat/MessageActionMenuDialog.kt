package com.livrechat.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.livrechat.app.data.local.entity.MessageEntity
import com.livrechat.app.ui.theme.DangerRed

@Composable
fun MessageActionMenuDialog(
    message: MessageEntity,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
    onStar: () -> Unit,
    onPin: () -> Unit,
    onInfo: () -> Unit
) {
    val quickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🙏", "👏")
    var showMoreEmojis by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable(enabled = false) {} // Prevent closing when tapping menu
            ) {
                // 1. FLOATING REACTION CAPSULE
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickReactions.forEach { emoji ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        onReact(emoji)
                                        onDismiss()
                                    }
                            ) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }

                        // Plus button for extra emojis
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { showMoreEmojis = !showMoreEmojis }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Mais reações",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Extra emojis expansion row
                if (showMoreEmojis) {
                    val extraEmojis = listOf("🔥", "🎉", "💯", "🚀", "✨", "👀", "🤝", "🥳")
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            extraEmojis.forEach { emoji ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            onReact(emoji)
                                            onDismiss()
                                        }
                                ) {
                                    Text(text = emoji, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }

                // 2. CONTEXT ACTION MENU CARD
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.width(220.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        ActionMenuItem(
                            icon = Icons.AutoMirrored.Filled.Reply,
                            text = "Responder",
                            onClick = {
                                onReply()
                                onDismiss()
                            }
                        )

                        ActionMenuItem(
                            icon = Icons.Default.Share,
                            text = "Encaminhar",
                            onClick = {
                                onForward()
                                onDismiss()
                            }
                        )

                        ActionMenuItem(
                            icon = Icons.Default.StarOutline,
                            text = "Favoritar",
                            onClick = {
                                onStar()
                                onDismiss()
                            }
                        )

                        ActionMenuItem(
                            icon = Icons.Default.PushPin,
                            text = "Fixar",
                            onClick = {
                                onPin()
                                onDismiss()
                            }
                        )

                        ActionMenuItem(
                            icon = Icons.Default.Info,
                            text = "Dados",
                            onClick = {
                                onInfo()
                                onDismiss()
                            }
                        )

                        Divider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        ActionMenuItem(
                            icon = Icons.Default.Delete,
                            text = "Apagar",
                            tint = DangerRed,
                            onClick = {
                                onDelete()
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
