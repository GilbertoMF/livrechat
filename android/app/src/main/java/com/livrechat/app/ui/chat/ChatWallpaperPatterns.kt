package com.livrechat.app.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

data class WallpaperPreset(
    val id: String,
    val name: String,
    val category: String, // "Desenhos", "Cores"
    val previewColor: Color,
    val description: String
)

val WALLPAPER_PATTERNS = listOf(
    WallpaperPreset("pattern:doodle", "Doodles Clássicos", "Desenhos", Color(0xFF0B141A), "Desenhos de café, notas musicais, smiles e balões"),
    WallpaperPreset("pattern:stars", "Constelações & Galáxia", "Desenhos", Color(0xFF0A0E1A), "Estrelas cósmicas, luas e órbitas celestes"),
    WallpaperPreset("pattern:matrix", "Circuitos Cyber Tech", "Desenhos", Color(0xFF051119), "Linhas de circuitos, processador e dados futuristas"),
    WallpaperPreset("pattern:geom", "Geométrico & Hexágonos", "Desenhos", Color(0xFF14121F), "Triângulos e malha isométrica moderna"),
    WallpaperPreset("pattern:nature", "Folhagens da Natureza", "Desenhos", Color(0xFF091A14), "Silhuetas de folhas botânicas e ramos"),
    WallpaperPreset("pattern:bubbles", "Bolhas Flutuantes", "Desenhos", Color(0xFF0F172A), "Balões de bate-papo e conversas dinâmicas")
)

@Composable
fun ChatWallpaperCanvas(
    patternKey: String,
    modifier: Modifier = Modifier
) {
    when (patternKey) {
        "pattern:doodle" -> DoodleWallpaper(modifier)
        "pattern:stars" -> StarsWallpaper(modifier)
        "pattern:matrix" -> MatrixWallpaper(modifier)
        "pattern:geom" -> GeometricWallpaper(modifier)
        "pattern:nature" -> NatureWallpaper(modifier)
        "pattern:bubbles" -> BubblesWallpaper(modifier)
        else -> Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B141A)))
    }
}

@Composable
fun DoodleWallpaper(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0B141A))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepX = 140f
            val stepY = 140f
            val strokeColor = Color.White.copy(alpha = 0.07f)
            val stroke = Stroke(width = 2.2f)

            var y = 30f
            var row = 0
            while (y < size.height + 60f) {
                var x = if (row % 2 == 0) 30f else 100f
                var col = 0
                while (x < size.width + 60f) {
                    val patternType = (row * 3 + col) % 6
                    when (patternType) {
                        0 -> {
                            // Xícara de café / caneca
                            drawRoundRect(
                                color = strokeColor,
                                topLeft = Offset(x - 14f, y - 10f),
                                size = Size(28f, 22f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                                style = stroke
                            )
                            // Alça da xícara
                            drawArc(
                                color = strokeColor,
                                startAngle = -90f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(x + 12f, y - 8f),
                                size = Size(10f, 16f),
                                style = stroke
                            )
                        }
                        1 -> {
                            // Balão de conversa
                            val bubblePath = Path().apply {
                                moveTo(x - 16f, y - 12f)
                                lineTo(x + 16f, y - 12f)
                                lineTo(x + 16f, y + 8f)
                                lineTo(x, y + 8f)
                                lineTo(x - 6f, y + 16f)
                                lineTo(x - 6f, y + 8f)
                                lineTo(x - 16f, y + 8f)
                                close()
                            }
                            drawPath(bubblePath, strokeColor, style = stroke)
                        }
                        2 -> {
                            // Nota musical
                            drawCircle(strokeColor, radius = 5f, center = Offset(x - 8f, y + 6f))
                            drawCircle(strokeColor, radius = 5f, center = Offset(x + 8f, y + 2f))
                            drawLine(strokeColor, Offset(x - 3f, y + 6f), Offset(x - 3f, y - 12f), strokeWidth = 2.2f)
                            drawLine(strokeColor, Offset(x + 13f, y + 2f), Offset(x + 13f, y - 16f), strokeWidth = 2.2f)
                            drawLine(strokeColor, Offset(x - 3f, y - 12f), Offset(x + 13f, y - 16f), strokeWidth = 2.6f)
                        }
                        3 -> {
                            // Coração
                            val heartPath = Path().apply {
                                moveTo(x, y + 10f)
                                cubicTo(x - 18f, y - 2f, x - 12f, y - 16f, x, y - 8f)
                                cubicTo(x + 12f, y - 16f, x + 18f, y - 2f, x, y + 10f)
                            }
                            drawPath(heartPath, strokeColor, style = stroke)
                        }
                        4 -> {
                            // Fone de ouvido
                            drawArc(
                                color = strokeColor,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                topLeft = Offset(x - 15f, y - 16f),
                                size = Size(30f, 28f),
                                style = stroke
                            )
                            drawRoundRect(strokeColor, Offset(x - 18f, y - 2f), Size(7f, 14f), androidx.compose.ui.geometry.CornerRadius(3f, 3f), stroke)
                            drawRoundRect(strokeColor, Offset(x + 11f, y - 2f), Size(7f, 14f), androidx.compose.ui.geometry.CornerRadius(3f, 3f), stroke)
                        }
                        5 -> {
                            // Smile / Rosto sorridente
                            drawCircle(strokeColor, radius = 15f, center = Offset(x, y), style = stroke)
                            drawCircle(strokeColor, radius = 1.8f, center = Offset(x - 5f, y - 4f))
                            drawCircle(strokeColor, radius = 1.8f, center = Offset(x + 5f, y - 4f))
                            drawArc(
                                color = strokeColor,
                                startAngle = 20f,
                                sweepAngle = 140f,
                                useCenter = false,
                                topLeft = Offset(x - 8f, y - 4f),
                                size = Size(16f, 12f),
                                style = stroke
                            )
                        }
                    }
                    x += stepX
                    col++
                }
                y += stepY
                row++
            }
        }
    }
}

@Composable
fun StarsWallpaper(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF090D18))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepX = 110f
            val stepY = 110f
            val starColor = Color(0xFF81D4FA).copy(alpha = 0.10f)
            val glowColor = Color(0xFFE1BEE7).copy(alpha = 0.08f)
            val stroke = Stroke(width = 1.8f)

            var y = 25f
            var row = 0
            while (y < size.height + 50f) {
                var x = if (row % 2 == 0) 25f else 80f
                var col = 0
                while (x < size.width + 50f) {
                    val pType = (row * 2 + col) % 4
                    when (pType) {
                        0 -> {
                            // Estrela 4 pontas
                            val p = Path().apply {
                                moveTo(x, y - 14f)
                                quadraticBezierTo(x, y, x + 14f, y)
                                quadraticBezierTo(x, y, x, y + 14f)
                                quadraticBezierTo(x, y, x - 14f, y)
                                quadraticBezierTo(x, y, x, y - 14f)
                            }
                            drawPath(p, starColor, style = stroke)
                        }
                        1 -> {
                            // Lua crescente
                            val moon = Path().apply {
                                moveTo(x, y - 12f)
                                cubicTo(x + 12f, y - 6f, x + 12f, y + 6f, x, y + 12f)
                                cubicTo(x + 6f, y + 4f, x + 6f, y - 4f, x, y - 12f)
                            }
                            drawPath(moon, glowColor, style = stroke)
                        }
                        2 -> {
                            // Pequena constelação / pontos interligados
                            drawCircle(starColor, radius = 3f, center = Offset(x - 8f, y - 8f))
                            drawCircle(starColor, radius = 2.5f, center = Offset(x + 10f, y - 4f))
                            drawCircle(starColor, radius = 3f, center = Offset(x + 4f, y + 10f))
                            drawLine(starColor, Offset(x - 8f, y - 8f), Offset(x + 10f, y - 4f), strokeWidth = 1f)
                            drawLine(starColor, Offset(x + 10f, y - 4f), Offset(x + 4f, y + 10f), strokeWidth = 1f)
                        }
                        3 -> {
                            // Planeta com anel
                            drawCircle(glowColor, radius = 7f, center = Offset(x, y), style = stroke)
                            drawArc(
                                color = starColor,
                                startAngle = -20f,
                                sweepAngle = 220f,
                                useCenter = false,
                                topLeft = Offset(x - 14f, y - 5f),
                                size = Size(28f, 10f),
                                style = stroke
                            )
                        }
                    }
                    x += stepX
                    col++
                }
                y += stepY
                row++
            }
        }
    }
}

@Composable
fun MatrixWallpaper(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF040E14))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 90f
            val traceColor = Color(0xFF00E676).copy(alpha = 0.08f)
            val nodeColor = Color(0xFF00E676).copy(alpha = 0.15f)
            val stroke = Stroke(width = 1.8f)

            var y = 20f
            var row = 0
            while (y < size.height + 40f) {
                var x = if (row % 2 == 0) 20f else 65f
                while (x < size.width + 40f) {
                    // Traço de circuito em L
                    drawLine(traceColor, Offset(x - 20f, y), Offset(x, y), strokeWidth = 1.8f)
                    drawLine(traceColor, Offset(x, y), Offset(x, y + 25f), strokeWidth = 1.8f)
                    drawLine(traceColor, Offset(x, y + 25f), Offset(x + 25f, y + 25f), strokeWidth = 1.8f)
                    drawCircle(nodeColor, radius = 3.5f, center = Offset(x, y))
                    drawCircle(nodeColor, radius = 2.5f, center = Offset(x + 25f, y + 25f))

                    // Chip quadrado
                    drawRoundRect(
                        color = traceColor,
                        topLeft = Offset(x + 30f, y - 10f),
                        size = Size(16f, 16f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
                        style = stroke
                    )
                    x += step
                }
                y += step
                row++
            }
        }
    }
}

@Composable
fun GeometricWallpaper(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF100E1C))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepX = 100f
            val stepY = 100f
            val geomColor = Color(0xFFB388FF).copy(alpha = 0.09f)
            val stroke = Stroke(width = 1.6f)

            var y = 30f
            var row = 0
            while (y < size.height + 60f) {
                var x = if (row % 2 == 0) 30f else 80f
                while (x < size.width + 60f) {
                    // Hexágono
                    val hex = Path().apply {
                        val r = 16f
                        for (i in 0 until 6) {
                            val angle = Math.toRadians((i * 60 - 30).toDouble())
                            val px = x + (r * cos(angle)).toFloat()
                            val py = y + (r * sin(angle)).toFloat()
                            if (i == 0) moveTo(px, py) else lineTo(px, py)
                        }
                        close()
                    }
                    drawPath(hex, geomColor, style = stroke)

                    // Triângulo invertido interior
                    val tri = Path().apply {
                        moveTo(x, y + 10f)
                        lineTo(x - 9f, y - 6f)
                        lineTo(x + 9f, y - 6f)
                        close()
                    }
                    drawPath(tri, geomColor.copy(alpha = 0.05f), style = stroke)

                    x += stepX
                }
                y += stepY
                row++
            }
        }
    }
}

@Composable
fun NatureWallpaper(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF071710))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepX = 120f
            val stepY = 120f
            val leafColor = Color(0xFFA5D6A7).copy(alpha = 0.09f)
            val stroke = Stroke(width = 1.8f)

            var y = 30f
            var row = 0
            while (y < size.height + 60f) {
                var x = if (row % 2 == 0) 30f else 90f
                while (x < size.width + 60f) {
                    // Folha natural estilizada
                    val leaf = Path().apply {
                        moveTo(x - 15f, y + 15f)
                        quadraticBezierTo(x - 18f, y - 8f, x + 15f, y - 15f)
                        quadraticBezierTo(x + 18f, y + 8f, x - 15f, y + 15f)
                    }
                    drawPath(leaf, leafColor, style = stroke)
                    // Nervura central da folha
                    drawLine(leafColor, Offset(x - 15f, y + 15f), Offset(x + 15f, y - 15f), strokeWidth = 1.4f)

                    // Pequeno trevo / flor
                    drawCircle(leafColor, radius = 3.5f, center = Offset(x + 25f, y + 18f), style = stroke)
                    drawCircle(leafColor, radius = 3.5f, center = Offset(x + 32f, y + 18f), style = stroke)
                    drawCircle(leafColor, radius = 3.5f, center = Offset(x + 28f, y + 13f), style = stroke)

                    x += stepX
                }
                y += stepY
                row++
            }
        }
    }
}

@Composable
fun BubblesWallpaper(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stepX = 100f
            val stepY = 100f
            val bubbleColor = Color(0xFF60A5FA).copy(alpha = 0.09f)
            val stroke = Stroke(width = 1.8f)

            var y = 25f
            var row = 0
            while (y < size.height + 50f) {
                var x = if (row % 2 == 0) 25f else 75f
                while (x < size.width + 50f) {
                    drawCircle(bubbleColor, radius = 14f, center = Offset(x, y), style = stroke)
                    drawCircle(bubbleColor.copy(alpha = 0.15f), radius = 3.5f, center = Offset(x - 5f, y - 5f))
                    drawCircle(bubbleColor, radius = 6f, center = Offset(x + 18f, y + 14f), style = stroke)
                    x += stepX
                }
                y += stepY
                row++
            }
        }
    }
}
