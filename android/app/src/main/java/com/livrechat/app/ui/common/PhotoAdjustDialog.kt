package com.livrechat.app.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PhotoAdjustDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onCropFinished: (Bitmap) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var rotationDegrees by remember { mutableStateOf(0f) }

    val viewportSizeDp = 280.dp
    val density = LocalDensity.current
    val viewportSizePx = with(density) { viewportSizeDp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = Color.White)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Ajustar Foto do Perfil",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Arraste e aproxime para enquadrar",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            val cropped = cropAdjustedBitmap(
                                source = sourceBitmap,
                                rotation = rotationDegrees,
                                scale = scale,
                                panX = panOffset.x,
                                panY = panOffset.y,
                                viewportSizePx = viewportSizePx,
                                targetSizePx = 512
                            )
                            onCropFinished(cropped)
                        }
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Concluir",
                            tint = Color(0xFF25D366),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Interactive Crop Viewport with Circular Mask & Grid
                Box(
                    modifier = Modifier
                        .size(viewportSizeDp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E293B))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                val maxPan = viewportSizePx * (scale - 0.5f)
                                panOffset = Offset(
                                    x = (panOffset.x + pan.x).coerceIn(-maxPan, maxPan),
                                    y = (panOffset.y + pan.y).coerceIn(-maxPan, maxPan)
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Source Image Layer
                    androidx.compose.foundation.Image(
                        bitmap = sourceBitmap.asImageBitmap(),
                        contentDescription = "Foto",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = panOffset.x
                                translationY = panOffset.y
                                rotationZ = rotationDegrees
                            }
                    )

                    // WhatsApp 3x3 Grid Overlay
                    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 1.dp.toPx()
                        val gridColor = Color.White.copy(alpha = 0.35f)
                        val w = size.width
                        val h = size.height

                        // Vertical grid lines
                        drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth)
                        drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth)

                        // Horizontal grid lines
                        drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth)
                        drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth)
                    }
                }

                // Bottom Tools: Rotate, Reset, and Action Buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                rotationDegrees = (rotationDegrees + 90f) % 360f
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Girar 90°", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                scale = 1f
                                panOffset = Offset.Zero
                                rotationDegrees = 0f
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restaurar", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Cancelar", color = Color.White)
                        }

                        Button(
                            onClick = {
                                val cropped = cropAdjustedBitmap(
                                    source = sourceBitmap,
                                    rotation = rotationDegrees,
                                    scale = scale,
                                    panX = panOffset.x,
                                    panY = panOffset.y,
                                    viewportSizePx = viewportSizePx,
                                    targetSizePx = 512
                                )
                                onCropFinished(cropped)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Concluir", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders the adjusted, scaled, rotated and panned image into a 512x512 Bitmap.
 */
fun cropAdjustedBitmap(
    source: Bitmap,
    rotation: Float,
    scale: Float,
    panX: Float,
    panY: Float,
    viewportSizePx: Float,
    targetSizePx: Int = 512
): Bitmap {
    val result = Bitmap.createBitmap(targetSizePx, targetSizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)

    val matrix = android.graphics.Matrix()
    // Move center to origin
    matrix.postTranslate(-source.width / 2f, -source.height / 2f)
    // Rotate
    matrix.postRotate(rotation)

    // Base scale to fill the viewport
    val rot = ((rotation % 360f) + 360f) % 360f
    val isSwapped = rot == 90f || rot == 270f
    val effectiveW = if (isSwapped) source.height else source.width
    val effectiveH = if (isSwapped) source.width else source.height

    val baseScale = maxOf(viewportSizePx / effectiveW, viewportSizePx / effectiveH)
    val totalScale = baseScale * scale
    matrix.postScale(totalScale, totalScale)

    // Pan
    matrix.postTranslate(viewportSizePx / 2f + panX, viewportSizePx / 2f + panY)

    // Output target scaling
    val outputScale = targetSizePx.toFloat() / viewportSizePx
    matrix.postScale(outputScale, outputScale)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, matrix, paint)
    return result
}
