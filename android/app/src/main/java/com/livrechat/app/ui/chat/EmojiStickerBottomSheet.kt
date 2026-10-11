package com.livrechat.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.livrechat.app.ui.theme.BluePrimary
import com.livrechat.app.util.MediaFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiStickerBottomSheet(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit,
    onSendSticker: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("stickers") } // "stickers", "emojis", "gifs"
    var searchQuery by remember { mutableStateOf("") }

    // Launcher for "Criar figurinha"
    val createStickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val input = context.contentResolver.openInputStream(uri) ?: return@launch
                    val stickerFile = MediaFileManager.saveStreamToLocalFile(context, input, "sticker_${System.currentTimeMillis()}.png")
                    input.close()
                    launch(Dispatchers.Main) {
                        onSendSticker(stickerFile.absolutePath)
                        onDismiss()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EmojiStickerSheet", "Erro ao criar figurinha", e)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .navigationBarsPadding()
        ) {
            // Top Navigation Tabs: Search, Emojis, GIF, Stickers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stickers Tab
                    FilterChip(
                        selected = selectedTab == "stickers",
                        onClick = { selectedTab = "stickers" },
                        label = { Text("Figurinhas", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluePrimary.copy(alpha = 0.2f),
                            selectedLabelColor = BluePrimary
                        )
                    )

                    // Emojis Tab
                    FilterChip(
                        selected = selectedTab == "emojis",
                        onClick = { selectedTab = "emojis" },
                        label = { Text("Emojis", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.SentimentSatisfied, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluePrimary.copy(alpha = 0.2f),
                            selectedLabelColor = BluePrimary
                        )
                    )

                    // GIFs Tab
                    FilterChip(
                        selected = selectedTab == "gifs",
                        onClick = { selectedTab = "gifs" },
                        label = { Text("GIFs", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Icon(Icons.Default.Gif, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluePrimary.copy(alpha = 0.2f),
                            selectedLabelColor = BluePrimary
                        )
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", modifier = Modifier.size(18.dp))
                }
            }

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            when (selectedTab) {
                "stickers" -> {
                    // STICKERS GRID
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // "Criar figurinha" Button
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF25D366).copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF25D366)),
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable { createStickerLauncher.launch("image/*") }
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Criar figurinha",
                                        tint = Color(0xFF25D366),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Criar\nfigurinha",
                                        color = Color(0xFF25D366),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }

                        // Built-in Meme Stickers Collection
                        val sampleStickers = listOf(
                            "https://api.iconify.design/fluent-emoji:dog-face.svg",
                            "https://api.iconify.design/fluent-emoji:grinning-cat-with-smiling-eyes.svg",
                            "https://api.iconify.design/fluent-emoji:partying-face.svg",
                            "https://api.iconify.design/fluent-emoji:rolling-on-the-floor-laughing.svg",
                            "https://api.iconify.design/fluent-emoji:zany-face.svg",
                            "https://api.iconify.design/fluent-emoji:exploding-head.svg",
                            "https://api.iconify.design/fluent-emoji:smiling-face-with-sunglasses.svg",
                            "https://api.iconify.design/fluent-emoji:face-with-tears-of-joy.svg",
                            "https://api.iconify.design/fluent-emoji:clown-face.svg",
                            "https://api.iconify.design/fluent-emoji:ghost.svg",
                            "https://api.iconify.design/fluent-emoji:robot.svg",
                            "https://api.iconify.design/fluent-emoji:alien-monster.svg",
                            "https://api.iconify.design/fluent-emoji:sparkling-heart.svg",
                            "https://api.iconify.design/fluent-emoji:fire.svg",
                            "https://api.iconify.design/fluent-emoji:rocket.svg"
                        )

                        items(sampleStickers) { stickerUrl ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable {
                                        onSendSticker(stickerUrl)
                                        onDismiss()
                                    }
                            ) {
                                AsyncImage(
                                    model = stickerUrl,
                                    contentDescription = "Figurinha",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }

                "emojis" -> {
                    // EMOJIS GRID
                    val emojiList = remember {
                        listOf(
                            "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
                            "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨",
                            "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕",
                            "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "🥸", "😎", "🤓", "🧐", "😕", "😟",
                            "🙁", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣",
                            "👍", "👎", "👊", "✊", "🤛", "🤜", "🤞", "✌️", "🤟", "🤘", "👌", "🤌", "🤏", "👈", "👉", "👆",
                            "👇", "☝️", "👋", "🤚", "🖐️", "✋", "🖖", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳",
                            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
                            "🔥", "✨", "🌟", "⭐", "💫", "💥", "💯", "💢", "💬", "💤", "🎵", "🎶", "☕", "🍕", "🍔", "🍟"
                        )
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(emojiList) { emoji ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .clickable { onEmojiSelected(emoji) }
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 26.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                "gifs" -> {
                    // GIFS GRID
                    val sampleGifs = listOf(
                        "https://media.giphy.com/media/3o7TKSjRrfIPjeiVyM/giphy.gif",
                        "https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif",
                        "https://media.giphy.com/media/26AHONQ79FdWZhAI0/giphy.gif",
                        "https://media.giphy.com/media/3oEjI6SIIHBdRxXI40/giphy.gif",
                        "https://media.giphy.com/media/xT9IgG50Fb7Mi0prBC/giphy.gif",
                        "https://media.giphy.com/media/l41lI4bYmcsPJX9Go/giphy.gif"
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(sampleGifs) { gifUrl ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clickable {
                                        onSendSticker(gifUrl)
                                        onDismiss()
                                    }
                            ) {
                                AsyncImage(
                                    model = gifUrl,
                                    contentDescription = "GIF",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
