package com.livrechat.app.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null

    fun startRecording(): File? {
        try {
            val cacheDir = context.cacheDir
            val audioFile = File(cacheDir, "audio_record_${System.currentTimeMillis()}.m4a")
            currentOutputFile = audioFile

            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(32000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            return audioFile
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "Erro ao iniciar gravação", e)
            cancelRecording()
            return null
        }
    }

    fun stopRecording(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            currentOutputFile
        } catch (e: Exception) {
            Log.e("AudioRecorderHelper", "Erro ao finalizar gravação", e)
            cancelRecording()
            null
        }
    }

    fun cancelRecording() {
        try {
            recorder?.apply {
                reset()
                release()
            }
        } catch (e: Exception) {}
        recorder = null
        currentOutputFile?.delete()
        currentOutputFile = null
    }
}
