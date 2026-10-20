package com.livrechat.app.util

import android.content.Context
import android.util.Base64
import com.livrechat.app.offline.data.OfflineDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Shamir Secret Sharing (2 of 3) & Decentralized P2P Backup for LivreChat.
 * Splits an AES-256 backup encryption key into 3 mathematical polynomial shares over GF(256).
 * Any 2 shares are sufficient to recover the full master key with zero knowledge leaked by a single share.
 */
object P2PBackupHelper {

    private val secureRandom = SecureRandom()

    // GF(256) with irreducible polynomial 0x11B (AES/Rijndael)
    private val exp = IntArray(512)
    private val log = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            log[x] = i
            x = x shl 1
            if (x and 0x100 != 0) {
                x = x xor 0x11b
            }
        }
        for (i in 255 until 512) {
            exp[i] = exp[i - 255]
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return exp[log[a] + log[b]]
    }

    private fun gfDiv(a: Int, b: Int): Int {
        if (a == 0) return 0
        if (b == 0) throw ArithmeticException("Division by zero in GF(256)")
        var diff = log[a] - log[b]
        if (diff < 0) diff += 255
        return exp[diff]
    }

    data class Shard(val index: Int, val payloadHex: String) {
        fun toShareToken(): String = "LIVRE-SHARD-$index-$payloadHex"

        companion object {
            fun parse(token: String): Shard? {
                val clean = token.trim()
                val parts = clean.split("-")
                if (parts.size >= 4 && parts[0] == "LIVRE" && parts[1] == "SHARD") {
                    val idx = parts[2].toIntOrNull() ?: return null
                    val hex = parts[3]
                    return Shard(idx, hex)
                }
                return null
            }
        }
    }

    /**
     * Splits a 32-byte secret (AES-256) into 3 shards with a threshold of 2.
     * f(x) = secret + a1 * x  (in GF(256))
     * evaluations at x=1, x=2, x=3
     */
    fun splitSecret(secret: ByteArray): List<Shard> {
        val n = 3
        val shares = Array(n) { ByteArray(secret.size) }

        for (i in secret.indices) {
            val a0 = secret[i].toInt() and 0xFF
            val a1 = secureRandom.nextInt(256) // random degree-1 coefficient

            for (x in 1..n) {
                // f(x) = a0 ^ (a1 * x)
                val fx = a0 xor gfMul(a1, x)
                shares[x - 1][i] = fx.toByte()
            }
        }

        return (1..n).map { x ->
            Shard(x, shares[x - 1].joinToString("") { "%02x".format(it) })
        }
    }

    /**
     * Reconstructs the original secret from any 2 shards using Lagrange interpolation at x=0.
     * f(0) = y1 * (x2 / (x2 ^ x1)) ^ y2 * (x1 / (x1 ^ x2))
     */
    fun combineShares(shard1: Shard, shard2: Shard): ByteArray {
        require(shard1.index != shard2.index) { "Os shards precisam ser diferentes" }

        val b1 = hexToBytes(shard1.payloadHex)
        val b2 = hexToBytes(shard2.payloadHex)
        require(b1.size == b2.size) { "Tamanho dos shards incompatível" }

        val secret = ByteArray(b1.size)
        val x1 = shard1.index
        val x2 = shard2.index

        // In GF(256), addition/subtraction is XOR.
        // dx = x2 ^ x1
        val dx = x2 xor x1
        val l1 = gfDiv(x2, dx) // x2 / (x2 - x1)
        val l2 = gfDiv(x1, dx) // x1 / (x1 - x2)

        for (i in b1.indices) {
            val y1 = b1[i].toInt() and 0xFF
            val y2 = b2[i].toInt() and 0xFF
            val a0 = gfMul(y1, l1) xor gfMul(y2, l2)
            secret[i] = a0.toByte()
        }

        return secret
    }

    /**
     * Creates an encrypted backup of messages and contacts, returns the backup file and 3 shards.
     */
    suspend fun createDecentralizedBackup(context: Context): Pair<File, List<Shard>> = withContext(Dispatchers.IO) {
        val db = OfflineDatabase.getInstance(context)
        val messages = db.offlineDao().getAllMensagensList()
        val contatos = db.offlineDao().getAllContatosList()

        val json = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("device", android.os.Build.MODEL)

            val msgsArray = JSONArray()
            messages.forEach { m ->
                msgsArray.put(JSONObject().apply {
                    put("idMensagem", m.idMensagem)
                    put("remetenteId", m.remetenteId)
                    put("remetenteNome", m.remetenteNome)
                    put("destinatarioId", m.destinatarioId)
                    put("conteudo", m.conteudo)
                    put("timestamp", m.timestamp)
                    put("statusEntrega", m.statusEntrega.name)
                    put("isMeshRoute", m.isMeshRoute)
                    put("hops", m.hops)
                    put("ttl", m.ttl)
                    put("relayedVia", m.relayedVia)
                    put("mediaType", m.mediaType)
                    put("isDeadDrop", m.isDeadDrop)
                    put("isBurned", m.isBurned)
                })
            }
            put("messages", msgsArray)

            val contatosArray = JSONArray()
            contatos.forEach { c ->
                contatosArray.put(JSONObject().apply {
                    put("id", c.id)
                    put("nomeDispositivo", c.nomeDispositivo)
                    put("ultimoVisto", c.ultimoVisto)
                })
            }
            put("contatos", contatosArray)
        }

        val rawData = json.toString().toByteArray(Charsets.UTF_8)

        // Generate 256-bit AES Key
        val aesKey = ByteArray(32)
        secureRandom.nextBytes(aesKey)

        // Encrypt with AES-GCM
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(rawData)

        // Backup file format: IV (12 bytes) + ciphertext
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val backupFile = File(backupDir, "livrechat_backup_${System.currentTimeMillis()}.enc")
        backupFile.writeBytes(iv + ciphertext)

        // Split key into 3 shards
        val shards = splitSecret(aesKey)
        Pair(backupFile, shards)
    }

    /**
     * Restores backup from encrypted file using any 2 shards.
     */
    suspend fun restoreDecentralizedBackup(
        context: Context,
        backupFile: File,
        shardA: Shard,
        shardB: Shard
    ): Int = withContext(Dispatchers.IO) {
        val aesKey = combineShares(shardA, shardB)
        val fileBytes = backupFile.readBytes()
        if (fileBytes.size < 12) throw IllegalArgumentException("Arquivo de backup corrompido ou vazio")

        val iv = fileBytes.copyOfRange(0, 12)
        val ciphertext = fileBytes.copyOfRange(12, fileBytes.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        val decrypted = cipher.doFinal(ciphertext)

        val json = JSONObject(String(decrypted, Charsets.UTF_8))
        val db = OfflineDatabase.getInstance(context)
        val dao = db.offlineDao()

        val msgsArray = json.optJSONArray("messages") ?: JSONArray()
        var restoredCount = 0

        for (i in 0 until msgsArray.length()) {
            val item = msgsArray.getJSONObject(i)
            val statusStr = item.optString("statusEntrega", "ENTREGUE")
            val status = try {
                com.livrechat.app.offline.data.StatusEntrega.valueOf(statusStr)
            } catch (e: Exception) {
                com.livrechat.app.offline.data.StatusEntrega.ENTREGUE
            }

            val msg = com.livrechat.app.offline.data.Mensagem(
                idMensagem = item.getString("idMensagem"),
                remetenteId = item.getString("remetenteId"),
                remetenteNome = item.getString("remetenteNome"),
                destinatarioId = item.getString("destinatarioId"),
                conteudo = item.getString("conteudo"),
                timestamp = item.getLong("timestamp"),
                statusEntrega = status,
                isMeshRoute = item.optBoolean("isMeshRoute", false),
                hops = item.optInt("hops", 0),
                ttl = item.optInt("ttl", 5),
                relayedVia = if (item.isNull("relayedVia")) null else item.optString("relayedVia"),
                mediaType = item.optString("mediaType", "text"),
                isDeadDrop = item.optBoolean("isDeadDrop", false),
                isBurned = item.optBoolean("isBurned", false)
            )
            dao.insertMensagem(msg)
            restoredCount++
        }

        // Restore contacts
        val contatosArray = json.optJSONArray("contatos") ?: JSONArray()
        for (i in 0 until contatosArray.length()) {
            val item = contatosArray.getJSONObject(i)
            val contato = com.livrechat.app.offline.data.Contato(
                id = item.getString("id"),
                nomeDispositivo = item.getString("nomeDispositivo"),
                ultimoVisto = item.optLong("ultimoVisto", System.currentTimeMillis())
            )
            dao.insertOrUpdateContato(contato)
        }

        restoredCount
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
