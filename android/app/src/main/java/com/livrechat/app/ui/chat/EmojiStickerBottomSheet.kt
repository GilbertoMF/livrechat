package com.livrechat.app.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
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

@Composable
fun EmojiStickerPanel(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit,
    onSendSticker: (String) -> Unit,
    onBackspace: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("emojis") } // "emojis", "stickers", "gifs"
    var selectedCategoryIndex by remember { mutableStateOf(0) }

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

    val emojiCategories = remember {
        listOf(
            "😀 Rostos" to listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
                "😘", "😗", "😚", "😙", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨",
                "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕",
                "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "🥸", "😎", "🤓", "🧐", "😕", "😟",
                "🙁", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣"
            ),
            "👍 Mãos" to listOf(
                "👍", "👎", "👊", "✊", "🤛", "🤜", "🤞", "✌️", "🤟", "🤘", "👌", "🤌", "🤏", "👈", "👉", "👆",
                "👇", "☝️", "👋", "🤚", "🖐️", "✋", "🖖", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳",
                "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅"
            ),
            "❤️ Amor" to listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
                "💘", "💝", "💟", "💌", "💋", "🫂", "✨", "🌟", "⭐", "💫", "💥", "💯", "💢", "💬", "💭", "💤"
            ),
            "🐶 Animais" to listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔",
                "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞",
                "🐢", "🐍", "🦎", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊"
            ),
            "🍔 Comida" to listOf(
                "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝",
                "🍅", "🥑", "🥦", "🌽", "🥕", "🥔", "🥐", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🥞", "🧇", "🥓",
                "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🥗", "🍝", "🍜", "🍣", "🍱", "🍦", "🍩", "🍪", "🎂",
                "🍫", "🍿", "☕", "🧃", "🧉", "🍺", "🍻", "🥂", "🍷", "🍸", "🍹", "🥤"
            ),
            "💡 Objetos" to listOf(
                "🔥", "🎵", "🎶", "🔔", "🔕", "📣", "📢", "🔑", "🗝️", "🔒", "🔓", "💡", "🔦", "🕯️", "💣", "💰",
                "💵", "💳", "💎", "⚖️", "🛠️", "🔨", "🔧", "🔫", "🛡️", "💊", "💉", "🩺", "🛒", "⚽", "🏀", "🏈",
                "⚾", "🎾", "🏐", "🏉", "🎱", "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐", "🛻"
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Top Navigation Bar (WhatsApp Style Tabs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

            // Backspace / Erase Button (like WhatsApp)
            if (onBackspace != null) {
                IconButton(
                    onClick = onBackspace,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Apagar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        when (selectedTab) {
            "emojis" -> {
                // Category Pills
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(emojiCategories.indices.toList()) { index ->
                        val (title, _) = emojiCategories[index]
                        val isSelected = selectedCategoryIndex == index
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) BluePrimary.copy(alpha = 0.15f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, BluePrimary.copy(alpha = 0.5f)) else null,
                            modifier = Modifier.clickable { selectedCategoryIndex = index }
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) BluePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                val currentEmojis = emojiCategories[selectedCategoryIndex].second

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentEmojis) { emoji ->
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

            "stickers" -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // "Criar figurinha" Button
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF25D366).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF25D366)),
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

            "gifs" -> {
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
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiStickerBottomSheet(
    onDismiss: () -> Unit,
    onEmojiSelected: (String) -> Unit,
    onSendSticker: (String) -> Unit,
    onBackspace: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        EmojiStickerPanel(
            onDismiss = onDismiss,
            onEmojiSelected = onEmojiSelected,
            onSendSticker = onSendSticker,
            onBackspace = onBackspace
        )
    }
}
