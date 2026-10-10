package com.livrechat.app.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ciphertext: String,
    val iv: String
)

class CryptoManager {

    private val secureRandom = SecureRandom()

    // Generate random 128-bit IV for AES-GCM
    fun generateIv(): ByteArray {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        return iv
    }

    // Encrypt message with symmetric AES-256-GCM
    fun encrypt(plainText: String, secretKeyBytes: ByteArray): EncryptedPayload {
        val iv = generateIv()
        val keySpec = SecretKeySpec(secretKeyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec)

        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ciphertext = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    // Decrypt message
    fun decrypt(cipherTextBase64: String, ivBase64: String, secretKeyBytes: ByteArray): String {
        val cipherBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val keySpec = SecretKeySpec(secretKeyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)

        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    // Derive 256-bit key from user passphrase and salt (PBKDF2)
    fun deriveKeyFromPassphrase(passphrase: String, salt: ByteArray, iterations: Int = 100000): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    // Compute SHA-256 checksum for backup integrity verification
    fun computeSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    // Generate session key between two users using shared secret hash
    fun generateSharedConversationKey(userAId: String, userBId: String, appMasterSecret: String): ByteArray {
        val combined = if (userAId < userBId) "$userAId:$userBId:$appMasterSecret" else "$userBId:$userAId:$appMasterSecret"
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(combined.toByteArray(Charsets.UTF_8))
    }
}
