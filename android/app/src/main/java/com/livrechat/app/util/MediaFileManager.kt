package com.livrechat.app.util

import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
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
        } catch (e: Throwable) {
            null
        }
    }

    fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return bitmap
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return rotated
    }

    fun decodeFileWithExif(file: File): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val original = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return null
            if (rotation != 0f) {
                val rotated = rotateBitmap(original, rotation)
                if (rotated != original) original.recycle()
                rotated
            } else {
                original
            }
        } catch (e: Exception) {
            try {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } catch (ex: Exception) {
                null
            }
        }
    }

    fun loadGalleryImageCorrected(context: Context, uri: Uri, maxDim: Int = 1280): Bitmap? {
        return try {
            var rotation = 0f
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotation = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }
            } catch (e: Exception) {}

            val original = context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            } ?: return null

            val upright = if (rotation != 0f) {
                val r = rotateBitmap(original, rotation)
                if (r != original) original.recycle()
                r
            } else {
                original
            }

            val w = upright.width
            val h = upright.height
            val scale = if (w > h) maxDim.toFloat() / w else maxDim.toFloat() / h
            if (scale < 1f) {
                val scaled = Bitmap.createScaledBitmap(upright, (w * scale).toInt(), (h * scale).toInt(), true)
                if (scaled != upright) upright.recycle()
                scaled
            } else {
                upright
            }
        } catch (e: Exception) {
            null
        }
    }

    fun decodeAndCorrectBitmap(context: Context, content: String): Bitmap? {
        if (content.isBlank()) return null
        return try {
            if (content.startsWith("http://") || content.startsWith("https://")) {
                val cacheKey = "cache_" + kotlin.math.abs(content.hashCode()).toString() + ".jpg"
                val cachedFile = File(getMediaDir(context), cacheKey)
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    return decodeFileWithExif(cachedFile)
                }
                try {
                    val url = java.net.URL(content)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    conn.doInput = true
                    conn.connect()
                    conn.inputStream.use { input ->
                        cachedFile.outputStream().use { out ->
                            input.copyTo(out)
                        }
                    }
                    decodeFileWithExif(cachedFile)
                } catch (e: Throwable) {
                    null
                }
            } else if (isLocalFilePath(content)) {
                val local = getLocalFile(content)
                if (local != null) {
                    decodeFileWithExif(local)
                } else null
            } else {
                val clean = if (content.contains(",")) content.substringAfter(",") else content
                val bytes = Base64.decode(clean, Base64.DEFAULT)
                var rotation = 0f
                try {
                    val exif = ExifInterface(java.io.ByteArrayInputStream(bytes))
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotation = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } catch (e: Throwable) {}

                val opts = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                var sampleSize = 1
                val maxTarget = 1280
                while ((opts.outWidth / sampleSize) > maxTarget || (opts.outHeight / sampleSize) > maxTarget) {
                    sampleSize *= 2
                }
                val decodeOpts = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
                if (rotation != 0f) {
                    val rotated = rotateBitmap(bmp, rotation)
                    if (rotated != bmp) bmp.recycle()
                    rotated
                } else {
                    bmp
                }
            }
        } catch (e: Throwable) {
            null
        }
    }

    fun createStickerFromContent(context: Context, content: String): File? {
        return try {
            val original = decodeAndCorrectBitmap(context, content) ?: return null
            val size = minOf(original.width, original.height)
            val x = (original.width - size) / 2
            val y = (original.height - size) / 2
            val square = Bitmap.createBitmap(original, x, y, size, size)
            val scaled = Bitmap.createScaledBitmap(square, 512, 512, true)
            val stickersDir = File(context.filesDir, "stickers").apply { mkdirs() }
            val stickerFile = File(stickersDir, "sticker_${System.currentTimeMillis()}.png")
            java.io.FileOutputStream(stickerFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            stickerFile
        } catch (e: Exception) {
            null
        }
    }
}

