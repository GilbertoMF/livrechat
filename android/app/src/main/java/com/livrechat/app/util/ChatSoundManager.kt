package com.livrechat.app.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.livrechat.app.LivreChatApp
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object ChatSoundManager {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val typingJobs = ConcurrentHashMap<String, Job>()

    // Synthesized sound buffers cached in memory
    private val messageChimeBuffer: ShortArray by lazy { generateMessageChime() }
    private val typingTickBuffer: ShortArray by lazy { generateTypingTick() }

    private const val SAMPLE_RATE = 22050

    private fun generateMessageChime(): ShortArray {
        // Two-tone pleasant notification chime (880 Hz -> 1320 Hz)
        val durationSec = 0.16
        val numSamples = (durationSec * SAMPLE_RATE).toInt()
        val buffer = ShortArray(numSamples)

        val splitSample = (0.06 * SAMPLE_RATE).toInt()

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val sample = if (i < splitSample) {
                val decay = exp(-t * 20.0)
                sin(2.0 * PI * 880.0 * t) * decay * 0.45
            } else {
                val t2 = (i - splitSample).toDouble() / SAMPLE_RATE
                val decay = exp(-t2 * 25.0)
                sin(2.0 * PI * 1320.0 * t2) * decay * 0.4
            }
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun generateTypingTick(): ShortArray {
        // Soft, subtle keyboard screen tap click
        val durationSec = 0.015
        val numSamples = (durationSec * SAMPLE_RATE).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t * 220.0)
            val freq = 2200.0 - (t * 40000.0)
            val sample = sin(2.0 * PI * freq * t) * decay * 0.22
            buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    private fun playPcm(buffer: ShortArray) {
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            scope.launch {
                delay(250)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
    }

    /**
     * Toca o som agradável de mensagem recebida dentro da conversa.
     */
    fun playIncomingMessageSound() {
        if (!LivreChatApp.instance.preferencesManager.conversationSounds) return
        scope.launch {
            playPcm(messageChimeBuffer)
        }
    }

    /**
     * Inicia a reprodução do som de digitação do amigo com pausas humanas naturais
     * ("mais que nao seja um som constante tenha pausas de acordo como ele vai escrevendo").
     */
    fun startTypingRhythm(conversationId: String) {
        if (!LivreChatApp.instance.preferencesManager.conversationSounds) return
        stopTypingRhythm(conversationId)

        val job = scope.launch {
            try {
                var cycles = 0
                // Máximo de 8 ciclos para não tocar indefinidamente
                while (isActive && cycles < 8) {
                    cycles++
                    // Dispara uma sequência de 2 a 4 toques rápidos de teclado
                    val taps = Random.nextInt(2, 5)
                    for (i in 0 until taps) {
                        if (!isActive) break
                        playPcm(typingTickBuffer)
                        // Pausa curta entre teclas na mesma palavra (80ms a 160ms)
                        delay(Random.nextLong(80, 160))
                    }

                    // Pausa realista entre palavras/pensamentos (450ms a 850ms)
                    if (!isActive) break
                    delay(Random.nextLong(450, 850))
                }
            } catch (e: CancellationException) {
                // Cancelado normalmente
            }
        }
        typingJobs[conversationId] = job
    }

    /**
     * Para os sons de digitação imediatamente.
     */
    fun stopTypingRhythm(conversationId: String) {
        typingJobs.remove(conversationId)?.cancel()
    }

    fun stopAll() {
        typingJobs.values.forEach { it.cancel() }
        typingJobs.clear()
    }
}
