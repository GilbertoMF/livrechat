package com.livrechat.app.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Base64
import android.util.Log
import com.livrechat.app.util.ProximitySensorHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class AudioPlayerHelper(private val context: Context) {
    private val TAG = "AudioPlayerHelper"
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingMessageId: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val proximityHelper = ProximitySensorHelper(context)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    var onAudioCompleted: ((messageId: String) -> Unit)? = null

    init {
        proximityHelper.onProximityChanged = { isNear ->
            if (_isPlaying.value) {
                routeAudio(isNear)
            }
        }
    }

    private fun routeAudio(isNearEar: Boolean) {
        try {
            if (isNearEar) {
                // Route to EARPIECE (auricular de cima)
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val earpiece = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpiece != null) {
                        audioManager.setCommunicationDevice(earpiece)
                    }
                }
            } else {
                // Route to LOUDSPEAKER (alto-falante principal)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) {
                        audioManager.setCommunicationDevice(speaker)
                    } else {
                        audioManager.clearCommunicationDevice()
                    }
                }
                if (audioManager.mode != AudioManager.MODE_NORMAL) {
                    audioManager.mode = AudioManager.MODE_NORMAL
                }
                audioManager.isSpeakerphoneOn = true
            }

            // CRITICAL: Ensure playback continues without pause when transitioning routes
            mediaPlayer?.let { mp ->
                if (_isPlaying.value && !mp.isPlaying) {
                    try {
                        mp.start()
                    } catch (e: Exception) {
                        Log.w(TAG, "Falha ao retomar MediaPlayer na transição de áudio: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao rotear áudio: ${e.message}")
        }
    }

    fun playAudio(messageId: String, content: String) {
        if (currentPlayingMessageId == messageId && _isPlaying.value) {
            pause()
            return
        }

        stop()

        try {
            val audioFile = getOrCreateAudioFile(messageId, content) ?: return

            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            routeAudio(proximityHelper.isNearEar)
            proximityHelper.startListening()

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(audioFile.absolutePath)
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    stop()
                    true
                }
                prepare()
                start()
            }

            mediaPlayer = mp
            currentPlayingMessageId = messageId
            _playingMessageId.value = messageId
            _isPlaying.value = true

            mp.setOnCompletionListener {
                val finishedId = currentPlayingMessageId
                stop()
                if (finishedId != null) {
                    onAudioCompleted?.invoke(finishedId)
                }
            }

            startProgressTracker()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reproduzir áudio", e)
            stop()
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_isPlaying.value && mediaPlayer != null) {
                try {
                    val current = mediaPlayer?.currentPosition ?: 0
                    val total = mediaPlayer?.duration ?: 1
                    if (total > 0) {
                        _progress.value = current.toFloat() / total.toFloat()
                    }
                } catch (e: Exception) {}
                delay(60)
            }
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
        } catch (e: Exception) {}
        proximityHelper.stopListening()
        resetAudioRouting()
    }

    fun stop() {
        progressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
        currentPlayingMessageId = null
        _playingMessageId.value = null
        _isPlaying.value = false
        _progress.value = 0f
        proximityHelper.stopListening()
        resetAudioRouting()
    }

    private fun resetAudioRouting() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            if (audioManager.mode != AudioManager.MODE_NORMAL) {
                audioManager.mode = AudioManager.MODE_NORMAL
            }
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {}
    }

    private fun getOrCreateAudioFile(messageId: String, content: String): File? {
        if (content.startsWith("/") || content.startsWith("file://")) {
            val local = File(content.removePrefix("file://"))
            if (local.exists() && local.length() > 0) return local
        }
        val file = File(context.cacheDir, "audio_msg_$messageId.m4a")
        if (file.exists() && file.length() > 0) return file

        if (content.startsWith("http://") || content.startsWith("https://")) {
            return try {
                val url = java.net.URL(content)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.doInput = true
                conn.connect()
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                file
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao baixar áudio R2: ${e.message}")
                null
            }
        }

        return try {
            val base64Data = if (content.contains(",")) {
                content.substringAfter(",")
            } else {
                content
            }
            val decoded = Base64.decode(base64Data, Base64.DEFAULT)
            FileOutputStream(file).use { it.write(decoded) }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao decodificar base64", e)
            null
        }
    }
}
