package io.github.omeg4dev.fmhy.data.fmhyx

import java.io.File
import java.security.GeneralSecurityException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FmhyXTokenStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeTokenCipher(
        var failDecrypt: Boolean = false,
    ) : TokenCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray {
            val prefix = "ENC:".toByteArray(Charsets.UTF_8)
            val inverted = ByteArray(plaintext.size) { i -> (plaintext[i].toInt() xor 0x5A).toByte() }
            return prefix + inverted
        }

        override fun decrypt(blob: ByteArray): ByteArray {
            if (failDecrypt) throw GeneralSecurityException("Key lost")
            val prefix = "ENC:".toByteArray(Charsets.UTF_8)
            if (blob.size < prefix.size || !blob.copyOfRange(0, prefix.size).contentEquals(prefix)) {
                throw GeneralSecurityException("Corrupt blob")
            }
            val body = blob.copyOfRange(prefix.size, blob.size)
            return ByteArray(body.size) { i -> (body[i].toInt() xor 0x5A).toByte() }
        }
    }

    @Test
    fun saveAndReadRoundTripWithFakeCipher() {
        val file = File(tempFolder.root, "fmhyx_token.bin")
        val store = FmhyXTokenStore(file, FakeTokenCipher())

        assertNull(store.read())

        store.save("fmhyx_secret_token_123")
        assertTrue(file.exists())
        // Ciphertext must not contain plaintext token
        val rawText = file.readBytes().toString(Charsets.UTF_8)
        assertFalse(rawText.contains("fmhyx_secret_token_123"))

        assertEquals("fmhyx_secret_token_123", store.read())
        assertFalse(File(tempFolder.root, "fmhyx_token.bin.tmp").exists())
    }

    @Test
    fun corruptFileClearsFileAndReturnsNull() {
        val file = File(tempFolder.root, "fmhyx_token.bin")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        val store = FmhyXTokenStore(file, FakeTokenCipher())

        assertNull(store.read())
        assertFalse("Corrupt token file must be cleared on failed read", file.exists())
    }

    @Test
    fun lostKeyClearsFileAndReturnsNull() {
        val file = File(tempFolder.root, "fmhyx_token.bin")
        val cipher = FakeTokenCipher()
        val store = FmhyXTokenStore(file, cipher)

        store.save("fmhyx_abc")
        assertTrue(file.exists())

        cipher.failDecrypt = true
        assertNull(store.read())
        assertFalse("Undecryptable token file must be cleared", file.exists())
    }

    @Test
    fun clearDeletesFileAndReturnsNull() {
        val file = File(tempFolder.root, "fmhyx_token.bin")
        val store = FmhyXTokenStore(file, FakeTokenCipher())

        store.save("fmhyx_to_clear")
        assertTrue(file.exists())

        store.clear()
        assertFalse(file.exists())
        assertNull(store.read())
    }

    @Test
    fun saveOverwritesPreviousTokenAtomically() {
        val file = File(tempFolder.root, "fmhyx_token.bin")
        val store = FmhyXTokenStore(file, FakeTokenCipher())

        store.save("fmhyx_first")
        store.save("fmhyx_second")
        assertEquals("fmhyx_second", store.read())
        assertFalse(File(tempFolder.root, "fmhyx_token.bin.tmp").exists())
    }
}
