package com.livrechat.app.ui.games

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DownloadState {
    IDLE,
    DOWNLOADING,
    COMPLETED,
    ERROR
}

data class GameDownloadProgress(
    val gameId: String,
    val state: DownloadState = DownloadState.IDLE,
    val progress: Float = 0f, // 0.0 to 1.0
    val progressPercent: Int = 0, // 0 to 100
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null
)

class GameDownloadManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _downloadStates = MutableStateFlow<Map<String, GameDownloadProgress>>(emptyMap())
    val downloadStates: StateFlow<Map<String, GameDownloadProgress>> = _downloadStates.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun getGamesDirectory(): File {
        val dir = File(context.getExternalFilesDir(null), "games")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getGameFile(fileName: String): File {
        return File(getGamesDirectory(), fileName)
    }

    fun isGameDownloaded(fileName: String, expectedSizeBytes: Long = 0L): Boolean {
        val file = getGameFile(fileName)
        if (!file.exists()) return false
        if (expectedSizeBytes > 0L) {
            // Check if file is at least 95% of expected size (accounting for small headers/compression)
            return file.length() >= (expectedSizeBytes * 0.90)
        }
        return file.length() > 1024 * 1024 // At least 1MB
    }

    fun startDownload(gameId: String, downloadUrl: String, fileName: String, expectedSizeBytes: Long) {
        if (activeJobs[gameId]?.isActive == true) return

        val targetFile = getGameFile(fileName)
        val tempFile = File(getGamesDirectory(), "$fileName.tmp")

        updateProgress(gameId, GameDownloadProgress(
            gameId = gameId,
            state = DownloadState.DOWNLOADING,
            progress = 0f,
            progressPercent = 0,
            bytesDownloaded = 0L,
            totalBytes = expectedSizeBytes
        ))

        val job = scope.launch {
            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            try {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .addHeader("User-Agent", "LivreChat-Games/1.1.19")
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("Falha no download: Código HTTP ${response.code}")
                }

                val body = response.body ?: throw Exception("Resposta vazia do servidor")
                val totalLength = if (body.contentLength() > 0) body.contentLength() else expectedSizeBytes

                inputStream = body.byteStream()
                outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(128 * 1024) // 128KB high-speed buffer
                var bytesRead: Int
                var downloaded: Long = 0L
                var lastProgressUpdate = System.currentTimeMillis()

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (!isActive) {
                        tempFile.delete()
                        return@launch
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    downloaded += bytesRead

                    val now = System.currentTimeMillis()
                    // Throttle updates to UI at most every 200ms
                    if (now - lastProgressUpdate > 200 || downloaded == totalLength) {
                        lastProgressUpdate = now
                        val ratio = if (totalLength > 0) (downloaded.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f) else 0f
                        val percent = (ratio * 100).toInt()

                        updateProgress(gameId, GameDownloadProgress(
                            gameId = gameId,
                            state = DownloadState.DOWNLOADING,
                            progress = ratio,
                            progressPercent = percent,
                            bytesDownloaded = downloaded,
                            totalBytes = totalLength
                        ))
                    }
                }

                outputStream.flush()
                outputStream.close()
                outputStream = null
                inputStream.close()
                inputStream = null

                // Rename temp file to target file
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)

                updateProgress(gameId, GameDownloadProgress(
                    gameId = gameId,
                    state = DownloadState.COMPLETED,
                    progress = 1f,
                    progressPercent = 100,
                    bytesDownloaded = targetFile.length(),
                    totalBytes = targetFile.length()
                ))

            } catch (e: CancellationException) {
                tempFile.delete()
                updateProgress(gameId, GameDownloadProgress(gameId = gameId, state = DownloadState.IDLE))
            } catch (e: Exception) {
                tempFile.delete()
                updateProgress(gameId, GameDownloadProgress(
                    gameId = gameId,
                    state = DownloadState.ERROR,
                    errorMessage = e.message ?: "Erro desconhecido no download"
                ))
            } finally {
                try { outputStream?.close() } catch (_: Exception) {}
                try { inputStream?.close() } catch (_: Exception) {}
                activeJobs.remove(gameId)
            }
        }

        activeJobs[gameId] = job
    }

    fun cancelDownload(gameId: String) {
        activeJobs[gameId]?.cancel()
        activeJobs.remove(gameId)
        updateProgress(gameId, GameDownloadProgress(gameId = gameId, state = DownloadState.IDLE))
    }

    fun deleteGame(gameId: String, fileName: String): Boolean {
        cancelDownload(gameId)
        val file = getGameFile(fileName)
        val deleted = if (file.exists()) file.delete() else true
        updateProgress(gameId, GameDownloadProgress(gameId = gameId, state = DownloadState.IDLE))
        return deleted
    }

    private fun updateProgress(gameId: String, progress: GameDownloadProgress) {
        val current = _downloadStates.value.toMutableMap()
        current[gameId] = progress
        _downloadStates.value = current
    }

    companion object {
        @Volatile
        private var instance: GameDownloadManager? = null

        fun getInstance(context: Context): GameDownloadManager {
            return instance ?: synchronized(this) {
                instance ?: GameDownloadManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
