package io.github.omeg4dev.fmhy.browser.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

class FileHashTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testEmptyAndAbc() {
        val emptyHex = FileHash.sha256(ByteArrayInputStream(ByteArray(0)))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHex)

        val abcHex = FileHash.sha256(ByteArrayInputStream("abc".toByteArray(Charsets.UTF_8)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", abcHex)
    }

    @Test
    fun testChunkBoundarySizes() {
        val sizes = listOf(FileHash.CHUNK - 1, FileHash.CHUNK, FileHash.CHUNK + 1)
        for (size in sizes) {
            val bytes = ByteArray(size) { (it % 251).toByte() }
            val expectedDigest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val expectedHex = expectedDigest.joinToString("") { "%02x".format(it) }

            val actualHex = FileHash.sha256(ByteArrayInputStream(bytes))
            assertEquals("Mismatch for size $size", expectedHex, actualHex)
        }
    }

    @Test
    fun testSyntheticStream64MiB() {
        val totalBytes = 64L * 1024 * 1024 // 64 MiB

        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(FileHash.CHUNK)
        var remaining = totalBytes
        var offset = 0L
        while (remaining > 0) {
            val step = minOf(remaining, buffer.size.toLong()).toInt()
            for (i in 0 until step) {
                buffer[i] = ((offset + i) % 251).toByte()
            }
            digest.update(buffer, 0, step)
            offset += step
            remaining -= step
        }
        val expectedHex = digest.digest().joinToString("") { "%02x".format(it) }

        val progressList = mutableListOf<Long>()
        val actualHex = FileHash.sha256(
            input = SyntheticStream(totalBytes),
            totalBytes = totalBytes,
            onProgress = { progressList.add(it) },
        )

        assertEquals(expectedHex, actualHex)
        assertEquals(64, progressList.size)
        assertEquals(totalBytes, progressList.last())
        assertEquals(1L * FileHash.CHUNK, progressList.first())
    }

    @Test
    fun testIsActiveCancellationAfter3Chunks() {
        val totalBytes = 64L * 1024 * 1024
        var chunksProcessed = 0
        try {
            FileHash.sha256(
                input = SyntheticStream(totalBytes),
                totalBytes = totalBytes,
                onProgress = { chunksProcessed++ },
                isActive = { chunksProcessed < 3 },
            )
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            assertEquals(3, chunksProcessed)
        }
    }

    @Test
    fun testFileOverloadOnTempFile() {
        val tempFile = tempFolder.newFile("test_hash.bin")
        val content = "The quick brown fox jumps over the lazy dog".toByteArray(Charsets.UTF_8)
        tempFile.writeBytes(content)

        val expectedHex = MessageDigest.getInstance("SHA-256").digest(content)
            .joinToString("") { "%02x".format(it) }

        var progressCalled = false
        val actualHex = FileHash.sha256(
            file = tempFile,
            onProgress = { progressCalled = true },
        )

        assertEquals(expectedHex, actualHex)
        assertTrue(progressCalled)
    }

    private class SyntheticStream(private val totalBytes: Long) : InputStream() {
        private var pos = 0L

        override fun read(): Int {
            if (pos >= totalBytes) return -1
            val b = ((pos % 251).toInt()) and 0xFF
            pos++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= totalBytes) return -1
            val toRead = minOf(len.toLong(), totalBytes - pos).toInt()
            for (i in 0 until toRead) {
                b[off + i] = ((pos + i) % 251).toByte()
            }
            pos += toRead
            return toRead
        }
    }
}
