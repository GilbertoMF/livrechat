package com.livrechat.app.offline.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.livrechat.app.offline.data.PeerLocation
import com.livrechat.app.offline.nearby.NearbyManager
import com.livrechat.app.offline.nearby.TacticalCompassHelper
import kotlin.math.*

/**
 * Radar Tático de Amigos & Bússola P2P Offline
 * Funciona 100% sem internet, usando sensores do aparelho e telemetria GPS transmitida via malha mesh.
 */
@Composable
fun TacticalCompassRadarView(
    nearbyManager: NearbyManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val compassHelper = remember { TacticalCompassHelper(context) }
    val azimuth by compassHelper.azimuth.collectAsState()
    val peerLocations by nearbyManager.peerLocations.collectAsState()

    var myLocation by remember { mutableStateOf(compassHelper.getCurrentLocation()) }
    var isBroadcastingLocation by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val loc = compassHelper.getCurrentLocation()
            myLocation = loc
            if (loc != null) {
                nearbyManager.broadcastLocationBeacon(loc.latitude, loc.longitude, loc.accuracy)
                Toast.makeText(context, "Posição GPS transmitida na malha!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Aguardando sinal GPS...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    DisposableEffect(Unit) {
        compassHelper.startListening()
        myLocation = compassHelper.getCurrentLocation()
        onDispose {
            compassHelper.stopListening()
        }
    }

    val cardinal = remember(azimuth) {
        when (((azimuth + 22.5f) % 360) / 45) {
            0f -> "N (Norte)"
            1f -> "NE (Nordeste)"
            2f -> "L (Leste)"
            3f -> "SE (Sudeste)"
            4f -> "S (Sul)"
            5f -> "SO (Sudoeste)"
            6f -> "O (Oeste)"
            else -> "NO (Noroeste)"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // CABEÇALHO TÁTICO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BÚSSOLA & RADAR TÁTICO P2P",
                    color = Color(0xFF38BDF8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Rumo: ${azimuth.roundToInt()}° • $cardinal",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    } else {
                        val loc = compassHelper.getCurrentLocation()
                        myLocation = loc
                        if (loc != null) {
                            nearbyManager.broadcastLocationBeacon(loc.latitude, loc.longitude, loc.accuracy)
                            Toast.makeText(context, "📡 Baliza GPS enviada para a malha!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Sinal GPS indisponível no momento. Vá a céu aberto.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Transmitir GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ROSA DOS VENTOS / RADAR TÁTICO (CANVAS 220dp)
        Box(
            modifier = Modifier
                .size(240.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
            val sweepAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "sweepAngle"
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width.coerceAtMost(size.height) / 2f - 12f

                // Fundo circular do radar
                drawCircle(color = Color(0xFF0E1726), radius = radius, center = center)

                // Círculos concêntricos de alcance tático (Distâncias relativas)
                val ringStroke = Stroke(width = 1.2f)
                drawCircle(color = Color(0xFF1E293B), radius = radius * 0.33f, center = center, style = ringStroke)
                drawCircle(color = Color(0xFF1E293B), radius = radius * 0.66f, center = center, style = ringStroke)
                drawCircle(color = Color(0xFF38BDF8).copy(alpha = 0.5f), radius = radius, center = center, style = Stroke(width = 2f))

                // Linhas dos eixos cruzados (Norte/Sul, Leste/Oeste)
                drawLine(color = Color(0xFF1E293B), start = Offset(center.x, center.y - radius), end = Offset(center.x, center.y + radius), strokeWidth = 1f)
                drawLine(color = Color(0xFF1E293B), start = Offset(center.x - radius, center.y), end = Offset(center.x + radius, center.y), strokeWidth = 1f)

                // Feixe giratório do radar
                val sweepRad = Math.toRadians((sweepAngle - 90).toDouble())
                val sweepEnd = Offset(
                    (center.x + radius * cos(sweepRad)).toFloat(),
                    (center.y + radius * sin(sweepRad)).toFloat()
                )
                drawLine(
                    color = Color(0xFF38BDF8).copy(alpha = 0.4f),
                    start = center,
                    end = sweepEnd,
                    strokeWidth = 2f
                )

                // Marcações dos pontos cardeais relativas ao Azimute atual
                val nativePaint = Paint().apply {
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    textSize = 28f
                    isFakeBoldText = true
                }

                val cardinalPoints = listOf(
                    "N" to 0f,
                    "L" to 90f,
                    "S" to 180f,
                    "O" to 270f
                )

                cardinalPoints.forEach { (label, angleDeg) ->
                    // Ajusta o ângulo pela orientação do celular
                    val relativeAngle = (angleDeg - azimuth + 360f) % 360f
                    val rad = Math.toRadians((relativeAngle - 90).toDouble())
                    val textRadius = radius - 16f
                    val tx = (center.x + textRadius * cos(rad)).toFloat()
                    val ty = (center.y + textRadius * sin(rad) + 10f).toFloat()

                    nativePaint.color = if (label == "N") android.graphics.Color.RED else android.graphics.Color.WHITE
                    drawContext.canvas.nativeCanvas.drawText(label, tx, ty, nativePaint)
                }

                // Desenha o triângulo indicador do rumo à frente
                val arrowHead = Path().apply {
                    moveTo(center.x, center.y - radius - 8f)
                    lineTo(center.x - 7f, center.y - radius + 5f)
                    lineTo(center.x + 7f, center.y - radius + 5f)
                    close()
                }
                drawPath(path = arrowHead, color = Color(0xFF38BDF8))

                // Desenha os nós amigos (Blips de telemetria)
                val myLoc = myLocation
                peerLocations.values.forEach { peerLoc ->
                    if (myLoc != null) {
                        val dist = TacticalCompassHelper.calculateDistanceMeters(
                            myLoc.latitude, myLoc.longitude,
                            peerLoc.latitude, peerLoc.longitude
                        )
                        val bearing = TacticalCompassHelper.calculateBearing(
                            myLoc.latitude, myLoc.longitude,
                            peerLoc.latitude, peerLoc.longitude
                        )
                        // Ângulo na tela = bearing relativo à orientação do telefone
                        val relBearing = (bearing - azimuth + 360f) % 360f
                        val relRad = Math.toRadians((relBearing - 90).toDouble())

                        // Escala de distância (alcance de até 2km no radar)
                        val distFactor = (dist / 2000.0).coerceIn(0.2, 0.88).toFloat()
                        val blipDist = radius * distFactor
                        val bx = (center.x + blipDist * cos(relRad)).toFloat()
                        val by = (center.y + blipDist * sin(relRad)).toFloat()

                        // Ponto no radar
                        drawCircle(color = Color(0xFF00E676), radius = 7f, center = Offset(bx, by))
                        drawCircle(color = Color.White, radius = 3f, center = Offset(bx, by))

                        // Nome do nó
                        val peerPaint = Paint().apply {
                            color = android.graphics.Color.CYAN
                            textSize = 22f
                            textAlign = Paint.Align.CENTER
                        }
                        drawContext.canvas.nativeCanvas.drawText(peerLoc.peerName.take(12), bx, by - 12f, peerPaint)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // STATUS DA MINHA LOCALIZAÇÃO GPS
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.GpsFixed, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sua Posição GPS", fontSize = 11.sp, color = Color.Gray)
                    if (myLocation != null) {
                        val loc = myLocation!!
                        Text(
                            text = String.format("%.5f, %.5f (±%.0fm)", loc.latitude, loc.longitude, loc.accuracy),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Text("Aguardando satélites GPS...", fontSize = 12.sp, color = Color(0xFFFFB74D))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // LISTA DE DISPOSITIVOS COM COORDENADAS
        Text(
            text = "AMIGOS LOCALIZADOS VIA MALHA (${peerLocations.size})",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (peerLocations.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Explore, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Nenhuma baliza GPS recebida na malha",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Peça para outros aparelhos tocarem em 'Transmitir GPS'",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(peerLocations.values.toList(), key = { it.peerName }) { peerLoc ->
                    val myLoc = myLocation
                    val distanceText = if (myLoc != null) {
                        val d = TacticalCompassHelper.calculateDistanceMeters(myLoc.latitude, myLoc.longitude, peerLoc.latitude, peerLoc.longitude)
                        TacticalCompassHelper.formatDistance(d)
                    } else "--"

                    val bearingDeg = if (myLoc != null) {
                        TacticalCompassHelper.calculateBearing(myLoc.latitude, myLoc.longitude, peerLoc.latitude, peerLoc.longitude).roundToInt()
                    } else 0

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF0284C7).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.NearMe, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peerLoc.peerName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                Text("Rumo ${bearingDeg}° • Baliza offline", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF00E676).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = distanceText,
                                    color = Color(0xFF00E676),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
