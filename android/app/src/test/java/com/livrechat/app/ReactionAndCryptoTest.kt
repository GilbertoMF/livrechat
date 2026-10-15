package com.livrechat.app

import com.livrechat.app.crypto.CryptoManager
import com.livrechat.app.data.local.model.MessageReaction
import com.livrechat.app.data.local.model.ReactionUtils
import com.livrechat.app.util.MediaFileManager
import org.junit.Assert.*
import org.junit.Test

class ReactionAndCryptoTest {

    @Test
    fun testReactionUtilsParsingAndSerialization() {
        // Test empty/null handling
        assertTrue(ReactionUtils.parseReactions(null).isEmpty())
        assertTrue(ReactionUtils.parseReactions("").isEmpty())
        assertTrue(ReactionUtils.parseReactions("   ").isEmpty())
        assertTrue(ReactionUtils.parseReactions("invalid json { [").isEmpty())

        // Test normal reactions
        val original = listOf(
            MessageReaction(emoji = "❤️", userId = "user_123", username = "alice"),
            MessageReaction(emoji = "👍", userId = "user_456", username = "bob"),
            MessageReaction(emoji = "😂", userId = "user_789", username = "charlie")
        )

        val json = ReactionUtils.serializeReactions(original)
        assertNotNull(json)
        assertTrue(json.contains("❤️"))

        val parsed = ReactionUtils.parseReactions(json)
        assertEquals(3, parsed.size)
        assertEquals("❤️", parsed[0].emoji)
        assertEquals("user_123", parsed[0].userId)
        assertEquals("alice", parsed[0].username)
        assertEquals("👍", parsed[1].emoji)
        assertEquals("user_456", parsed[1].userId)
        assertEquals("bob", parsed[1].username)
    }

    @Test
    fun testMediaFileManagerPathDetection() {
        assertTrue(MediaFileManager.isLocalFilePath("/data/user/0/com.livrechat.app/files/media/photo.jpg"))
        assertTrue(MediaFileManager.isLocalFilePath("file:///storage/emulated/0/Download/test.mp4"))
        assertFalse(MediaFileManager.isLocalFilePath("data:image/jpeg;base64,/9j/4AAQSkZJRg=="))
        assertFalse(MediaFileManager.isLocalFilePath("https://example.com/image.png"))
        assertFalse(MediaFileManager.isLocalFilePath(""))
        assertFalse(MediaFileManager.isLocalFilePath("   "))
    }

    @Test
    fun testCryptoManagerKeyDerivationAndChecksum() {
        val crypto = CryptoManager()

        // Test SHA-256 calculation
        val input = "LivreChat_Secure_Test".toByteArray(Charsets.UTF_8)
        val hash = crypto.computeSha256(input)
        assertNotNull(hash)
        assertEquals(64, hash.length)

        // Test PBKDF2 key derivation
        val salt = "random_salt_12345".toByteArray(Charsets.UTF_8)
        val key1 = crypto.deriveKeyFromPassphrase("super_secret_password", salt, 1000)
        assertEquals(32, key1.size) // 256 bits

        val key2 = crypto.deriveKeyFromPassphrase("super_secret_password", salt, 1000)
        assertArrayEquals(key1, key2)

        // Test shared conversation key symmetry (Order independent)
        val sharedAB = crypto.generateSharedConversationKey("user_aaa", "user_bbb", "master_secret_key")
        val sharedBA = crypto.generateSharedConversationKey("user_bbb", "user_aaa", "master_secret_key")
        assertArrayEquals(sharedAB, sharedBA)
        assertEquals(32, sharedAB.size)
    }
}
