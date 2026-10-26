package io.github.omeg4dev.fmhy.browser.download

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

object FileHash {
    const val CHUNK = 1 shl 20 // 1 MiB

    /** SHA-256 as 64 lower-case hex chars. Reads in [CHUNK] pieces, never the whole file. Throws CancellationException-compatible: checks [isActive] each chunk and throws java.util.concurrent.CancellationException when false. */
    fun sha256(
        input: InputStream,
        totalBytes: Long = -1,
        onProgress: (Long) -> Unit = {},
        isActive: () -> Boolean = { true },
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK)
        var bytesReadSoFar = 0L

        while (true) {
            if (!isActive()) {
                throw CancellationException("Hash calculation cancelled")
            }
            var chunkBytesRead = 0
            while (chunkBytesRead < CHUNK) {
                val read = input.read(buffer, chunkBytesRead, CHUNK - chunkBytesRead)
                if (read == -1) break
                chunkBytesRead += read
            }
            if (chunkBytesRead > 0) {
                digest.update(buffer, 0, chunkBytesRead)
                bytesReadSoFar += chunkBytesRead
                onProgress(bytesReadSoFar)
            }
            if (chunkBytesRead < CHUNK) {
                break
            }
        }

        val hash = digest.digest()
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun sha256(
        file: File,
        onProgress: (Long) -> Unit = {},
        isActive: () -> Boolean = { true },
    ): String {
        return file.inputStream().use { stream ->
            sha256(
                input = stream,
                totalBytes = file.length(),
                onProgress = onProgress,
                isActive = isActive,
            )
        }
    }
}
