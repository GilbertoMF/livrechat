package com.livrechat.app.offline.nearby

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object MeshMediaHelper {

    private const val TAG = "MeshMediaHelper"
    private var pttRecorder: MediaRecorder? = null
    private var pttTempFile: File? = null
    private var pttStartTime: Long = 0L

    private var voiceRecorder: MediaRecorder? = null
    private var voiceTempFile: File? = null

    private var activePlayer: MediaPlayer? = null
    private var toneGen: ToneGenerator? = null

    // -------------------------------------------------------------------------
    // 1. WALKIE-TALKIE PTT (PUSH-TO-TALK)
    // -------------------------------------------------------------------------

    fun playChirp(isStart: Boolean) {
        try {
            if (toneGen == null) {
                toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            }
            val tone = if (isStart) ToneGenerator.TONE_PROP_BEEP else ToneGenerator.TONE_PROP_ACK
            toneGen?.startTone(tone, 100)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao emitir bip de walkie-talkie: ${e.message}")
        }
    }

    fun startPttRecording(context: Context): Boolean {
        return try {
            stopPttRecording()
            playChirp(isStart = true)

            val dir = File(context.cacheDir, "ptt_mesh").apply { mkdirs() }
            val file = File(dir, "ptt_out_${System.currentTimeMillis()}.amr")
            pttTempFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setAudioEncodingBitRate(12200)
                setAudioSamplingRate(8000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            pttRecorder = recorder
            pttStartTime = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar gravação PTT: ${e.message}")
            pttRecorder = null
            pttTempFile = null
            false
        }
    }

    fun stopPttRecording(): String? {
        val recorder = pttRecorder ?: return null
        val file = pttTempFile
        return try {
            recorder.stop()
            recorder.release()
            pttRecorder = null

            val elapsed = System.currentTimeMillis() - pttStartTime
            if (elapsed < 400 || file == null || !file.exists() || file.length() < 100) {
                file?.delete()
                return null
            }

            playChirp(isStart = false)
            val bytes = file.readBytes()
            file.delete()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar gravação PTT: ${e.message}")
            pttRecorder = null
            file?.delete()
            null
        }
    }

    fun playAudioBytes(context: Context, base64Audio: String, onFinished: (() -> Unit)? = null) {
        try {
            activePlayer?.stop()
            activePlayer?.release()
            activePlayer = null

            playChirp(isStart = true)

            val bytes = Base64.decode(base64Audio, Base64.DEFAULT)
            val tempFile = File.createTempFile("ptt_in_", ".amr", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(bytes) }

            val player = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                setOnCompletionListener {
                    try {
                        playChirp(isStart = false)
                        it.release()
                        tempFile.delete()
                        if (activePlayer == it) activePlayer = null
                        onFinished?.invoke()
                    } catch (e: Exception) {}
                }
                start()
            }
            activePlayer = player
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao reproduzir áudio PTT recebido: ${e.message}")
            onFinished?.invoke()
        }
    }

    // -------------------------------------------------------------------------
    // 2. ÁUDIOS DE VOZ NA MALHA
    // -------------------------------------------------------------------------

    fun startVoiceRecording(context: Context): Boolean {
        return try {
            stopVoiceRecording()
            val dir = File(context.cacheDir, "voice_mesh").apply { mkdirs() }
            val file = File(dir, "voice_${System.currentTimeMillis()}.amr")
            voiceTempFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AMR_NB)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setAudioEncodingBitRate(12200)
                setAudioSamplingRate(8000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            voiceRecorder = recorder
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar gravador de voz: ${e.message}")
            voiceRecorder = null
            voiceTempFile = null
            false
        }
    }

    fun stopVoiceRecording(): String? {
        val recorder = voiceRecorder ?: return null
        val file = voiceTempFile
        return try {
            recorder.stop()
            recorder.release()
            voiceRecorder = null

            if (file == null || !file.exists() || file.length() < 100) {
                file?.delete()
                return null
            }
            val bytes = file.readBytes()
            file.delete()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao finalizar gravação de voz: ${e.message}")
            voiceRecorder = null
            file?.delete()
            null
        }
    }

    // -------------------------------------------------------------------------
    // 3. COMPRESSÃO DE IMAGENS LEVES PARA A MALHA (< 22 KB Base64)
    // -------------------------------------------------------------------------

    fun compressBitmapForMesh(bitmap: Bitmap): String {
        val maxDimension = 320
        val width = bitmap.width
        val height = bitmap.height

        val scaledBitmap = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val newW: Int
            val newH: Int
            if (ratio > 1) {
                newW = maxDimension
                newH = (maxDimension / ratio).toInt()
            } else {
                newH = maxDimension
                newW = (maxDimension * ratio).toInt()
            }
            Bitmap.createScaledBitmap(bitmap, newW.coerceAtLeast(1), newH.coerceAtLeast(1), true)
        } else {
            bitmap
        }

        var quality = 60
        var byteArray: ByteArray
        do {
            val out = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            byteArray = out.toByteArray()
            quality -= 10
        } while (byteArray.size > 18000 && quality >= 20)

        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun saveBase64ImageToCache(context: Context, base64Str: String, id: String): File? {
        return try {
            val cleanBase64 = base64Str.substringAfter("base64,")
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val dir = File(context.filesDir, "mesh_media").apply { mkdirs() }
            val file = File(dir, "mesh_img_$id.jpg")
            FileOutputStream(file).use { it.write(bytes) }
            file
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao salvar imagem da malha em cache: ${e.message}")
            null
        }
    }

    // -------------------------------------------------------------------------
    // 4. VERIFICAÇÃO DE GATEWAY DE INTERNET
    // -------------------------------------------------------------------------

    fun isInternetAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            false
        }
    }
}
