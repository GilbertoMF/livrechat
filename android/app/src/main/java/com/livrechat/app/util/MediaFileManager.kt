package com.livrechat.app.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.util.UUID

object MediaFileManager {

    fun getMediaDir(context: Context): File {
        val dir = File(context.filesDir, "media")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun isLocalFilePath(content: String): Boolean {
        if (content.isBlank()) return false
        return content.startsWith("/") || content.startsWith("file://")
    }

    fun getLocalFile(content: String): File? {
        if (!isLocalFilePath(content)) return null
        val cleanPath = content.removePrefix("file://")
        val file = File(cleanPath)
        return if (file.exists()) file else null
    }

    fun saveStreamToLocalFile(context: Context, inputStream: InputStream, originalFileName: String): File {
        val ext = originalFileName.substringAfterLast(".", "bin")
        val safeName = "file_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$ext"
        val target = File(getMediaDir(context), safeName)
        target.outputStream().use { out ->
            inputStream.copyTo(out)
        }
        return target
    }

    fun saveBytesToLocalFile(context: Context, bytes: ByteArray, originalFileName: String): File {
        val ext = originalFileName.substringAfterLast(".", "bin")
        val safeName = "file_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$ext"
        val target = File(getMediaDir(context), safeName)
        target.writeBytes(bytes)
        return target
    }

    fun saveBase64ToLocalFile(context: Context, base64Data: String, originalFileName: String): File? {
        return try {
            val clean = if (base64Data.contains(",")) base64Data.substringAfter(",") else base64Data
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            saveBytesToLocalFile(context, bytes, originalFileName)
        } catch (e: Exception) {
            null
        }
    }

    fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
