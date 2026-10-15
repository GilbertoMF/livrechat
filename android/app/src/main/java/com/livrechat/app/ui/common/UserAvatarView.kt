package com.livrechat.app.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Universal Avatar View that flawlessly renders:
 * 1. Direct Bitmap (0ms latency)
 * 2. Base64 data URIs (data:image/jpeg;base64,...) decoded safely in background
 * 3. Local saved avatar files (context.filesDir/avatar_current.jpg)
 * 4. Remote HTTP/HTTPS URLs
 * 5. Fallback Initial letter badge with primary accent
 */
private object AvatarBitmapCache {
    val cache = android.util.LruCache<String, Bitmap>(50)
}

@Composable
fun UserAvatarView(
    avatarBitmap: Bitmap? = null,
    avatarUrl: String? = null,
    displayName: String = "U",
    size: Dp = 48.dp,
    fontSize: TextUnit = 20.sp,
    backgroundColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
    contentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var decodedBitmap by remember(avatarUrl) { 
        val cached = if (!avatarUrl.isNullOrBlank() && avatarUrl.startsWith("data:image/")) {
            AvatarBitmapCache.cache.get(avatarUrl)
        } else null
        mutableStateOf<Bitmap?>(cached) 
    }

    LaunchedEffect(avatarUrl) {
        if (!avatarUrl.isNullOrBlank() && avatarUrl.startsWith("data:image/")) {
            val cached = AvatarBitmapCache.cache.get(avatarUrl)
            if (cached != null) {
                decodedBitmap = cached
            } else {
                withContext(Dispatchers.IO) {
                    try {
                        val cleanBase64 = if (avatarUrl.contains(",")) {
                            avatarUrl.substringAfter(",")
                        } else {
                            avatarUrl
                        }
                        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            AvatarBitmapCache.cache.put(avatarUrl, bmp)
                            withContext(Dispatchers.Main) {
                                decodedBitmap = bmp
                            }
                        }
                    } catch (e: Exception) {
                        decodedBitmap = null
                    }
                }
            }
        } else {
            decodedBitmap = null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        when {
            avatarBitmap != null -> {
                Image(
                    bitmap = avatarBitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }
            decodedBitmap != null -> {
                Image(
                    bitmap = decodedBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }
            !avatarUrl.isNullOrBlank() && (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://") || avatarUrl.startsWith("/") || avatarUrl.startsWith("file:")) -> {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            }
            else -> {
                Text(
                    text = displayName.trim().take(1).uppercase().ifEmpty { "U" },
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    color = contentColor
                )
            }
        }
    }
}
