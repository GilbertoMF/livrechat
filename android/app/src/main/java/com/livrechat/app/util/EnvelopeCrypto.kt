package com.livrechat.app.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EnvelopeCrypto {

    fun encrypt(pin: String, plaintext: String, hint: String = ""): String {
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Hash for pin verification:
        val pinHash = MessageDigest.getInstance("SHA-256")
            .digest((pin + "livre_salt").toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it) }

        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val hintSanitized = hint.replace(":", " ").trim().take(30)
        return "[ENVELOPE:$hintSanitized:$pinHash:$ivB64:$cipherB64]"
    }

    fun isEnveloped(content: String): Boolean {
        return content.startsWith("[ENVELOPE:") && content.endsWith("]")
    }

    fun getHint(envelopeContent: String): String? {
        val parts = envelopeContent.removePrefix("[ENVELOPE:").removeSuffix("]").split(":")
        if (parts.size >= 4) {
            val h = parts[0].trim()
            return if (h.isNotEmpty()) h else null
        }
        return null
    }

    fun decrypt(pin: String, envelopeContent: String): String? {
        return try {
            val parts = envelopeContent.removePrefix("[ENVELOPE:").removeSuffix("]").split(":")
            if (parts.size < 4) return null
            val pinHash = parts[1]
            val ivB64 = parts[2]
            val cipherB64 = parts[3]

            val testHash = MessageDigest.getInstance("SHA-256")
                .digest((pin + "livre_salt").toByteArray(Charsets.UTF_8))
                .take(8)
                .joinToString("") { "%02x".format(it) }

            if (pinHash != testHash) return null

            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val cipherBytes = Base64.decode(cipherB64, Base64.NO_WRAP)
            val keyBytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            val decrypted = cipher.doFinal(cipherBytes)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
