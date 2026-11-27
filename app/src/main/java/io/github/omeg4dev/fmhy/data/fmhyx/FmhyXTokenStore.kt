package io.github.omeg4dev.fmhy.data.fmhyx

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts and decrypts the FMHY X account token blob. */
interface TokenCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
}

/**
 * AndroidKeyStore-backed AES/GCM/NoPadding cipher with a non-exportable key.
 * Stored blob format: 12-byte GCM IV followed by ciphertext + 16-byte GCM tag.
 */
class AndroidKeyStoreTokenCipher(
    private val alias: String = KEY_ALIAS,
) : TokenCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        require(iv != null && iv.size == GCM_IV_BYTES) { "Unexpected GCM IV size" }
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(iv.size + ciphertext.size).apply {
            System.arraycopy(iv, 0, this, 0, iv.size)
            System.arraycopy(ciphertext, 0, this, iv.size, ciphertext.size)
        }
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size <= GCM_IV_BYTES + GCM_TAG_BYTES) {
            throw GeneralSecurityException("Encrypted token blob is too short")
        }
        val key = getExistingKey()
            ?: throw GeneralSecurityException("Missing keystore key")
        val iv = blob.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = blob.copyOfRange(GCM_IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getExistingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey
    }

    private fun getOrCreateKey(): SecretKey {
        getExistingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        const val KEY_ALIAS = "fmhyx_token"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
    }
}

/**
 * Persists the FMHY X account token encrypted in `noBackupFilesDir`.
 * Corrupt or undecryptable files are cleared and treated as signed out.
 */
class FmhyXTokenStore(
    fileProvider: () -> File,
    private val cipher: TokenCipher = AndroidKeyStoreTokenCipher(),
) {
    constructor(file: File, cipher: TokenCipher = AndroidKeyStoreTokenCipher()) : this({ file }, cipher)

    /** Resolved on first use, off the main thread: locating the directory reads the disk. */
    private val file by lazy(fileProvider)
    private val lock = Any()

    fun save(token: String) {
        synchronized(lock) {
            val encrypted = cipher.encrypt(token.toByteArray(Charsets.UTF_8))
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            try {
                FileOutputStream(tmp).use { out ->
                    out.write(encrypted)
                    out.flush()
                    runCatching { out.fd.sync() }
                }
                try {
                    Files.move(
                        tmp.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(
                        tmp.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            } finally {
                if (tmp.exists()) {
                    tmp.delete()
                }
            }
        }
    }

    fun read(): String? {
        synchronized(lock) {
            if (!file.isFile) return null
            return try {
                val blob = file.readBytes()
                val plain = cipher.decrypt(blob)
                val token = plain.toString(Charsets.UTF_8)
                if (token.isBlank()) {
                    clearLocked()
                    null
                } else {
                    token
                }
            } catch (_: Exception) {
                clearLocked()
                null
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            clearLocked()
        }
    }

    private fun clearLocked() {
        file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
    }
}
