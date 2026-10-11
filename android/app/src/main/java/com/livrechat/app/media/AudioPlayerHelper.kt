package com.livrechat.app.media

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
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
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingMessageId: String? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    fun playAudio(messageId: String, content: String) {
        if (currentPlayingMessageId == messageId && _isPlaying.value) {
            pause()
            return
        }

        stop()

        try {
            val audioFile = getOrCreateAudioFile(messageId, content) ?: return
            val mp = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()
                start()
            }

            mediaPlayer = mp
            currentPlayingMessageId = messageId
            _playingMessageId.value = messageId
            _isPlaying.value = true

            mp.setOnCompletionListener {
                stop()
            }

            startProgressTracker()
        } catch (e: Exception) {
            Log.e("AudioPlayerHelper", "Erro ao reproduzir áudio", e)
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
                delay(100)
            }
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
        } catch (e: Exception) {}
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
    }

    private fun getOrCreateAudioFile(messageId: String, content: String): File? {
        val file = File(context.cacheDir, "audio_msg_$messageId.m4a")
        if (file.exists() && file.length() > 0) return file

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
            Log.e("AudioPlayerHelper", "Erro ao decodificar base64", e)
            null
        }
    }
}
