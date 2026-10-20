package io.github.omeg4dev.fmhy.data.adblock

import java.nio.ByteBuffer
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater

object NeuteredResources {
    data class Resource(val mimeType: String, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Resource) return false
            return mimeType == other.mimeType && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * mimeType.hashCode() + bytes.contentHashCode()
    }

    private val NOOP_JS = Resource("application/javascript", "(function(){})();".toByteArray(Charsets.UTF_8))
    private val NOOP_CSS = Resource("text/css", ByteArray(0))
    private val NOOP_TXT = Resource("text/plain", ByteArray(0))
    private val NOOP_HTML = Resource("text/html", "<!DOCTYPE html>".toByteArray(Charsets.UTF_8))
    private val NOOP_JSON = Resource("application/json", "{}".toByteArray(Charsets.UTF_8))

    // 43-byte transparent 1x1 GIF
    private val GIF_1X1 = Resource(
        "image/gif",
        Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==")
    )

    private val PNG_2X2 = Resource("image/png", makeTransparentPng(2, 2))
    private val PNG_3X2 = Resource("image/png", makeTransparentPng(3, 2))
    private val PNG_32X32 = Resource("image/png", makeTransparentPng(32, 32))

    // Per section 2.5: noop-0.1s.mp3 and noop-1s.mp4 are mapped to noop.txt.
    private val RESOURCES: Map<String, Resource> = mapOf(
        "noopjs" to NOOP_JS,
        "noop.js" to NOOP_JS,
        "noopcss" to NOOP_CSS,
        "noop.css" to NOOP_CSS,
        "noop.txt" to NOOP_TXT,
        "nooptext" to NOOP_TXT,
        "noop.html" to NOOP_HTML,
        "noopframe" to NOOP_HTML,
        "noop.json" to NOOP_JSON,
        "noopjson" to NOOP_JSON,
        "1x1.gif" to GIF_1X1,
        "1x1-transparent.gif" to GIF_1X1,
        "2x2.png" to PNG_2X2,
        "2x2-transparent.png" to PNG_2X2,
        "3x2.png" to PNG_3X2,
        "3x2-transparent.png" to PNG_3X2,
        "32x32.png" to PNG_32X32,
        "32x32-transparent.png" to PNG_32X32,
        "noop-0.1s.mp3" to NOOP_TXT,
        "noopmp3-0.1s" to NOOP_TXT,
        "noop-1s.mp4" to NOOP_TXT,
        "noopmp4-1s" to NOOP_TXT,
        "empty" to NOOP_TXT,
        "none" to NOOP_TXT,
    )

    fun get(name: String): Resource? = RESOURCES[name.lowercase()]

    private fun makeTransparentPng(width: Int, height: Int): ByteArray {
        val sig = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )
        val ihdrData = ByteArray(13)
        val bb = ByteBuffer.wrap(ihdrData)
        bb.putInt(width)
        bb.putInt(height)
        bb.put(8.toByte()) // bit depth
        bb.put(6.toByte()) // RGBA
        bb.put(0.toByte()) // compression
        bb.put(0.toByte()) // filter
        bb.put(0.toByte()) // interlace

        val ihdrChunk = makeChunk("IHDR", ihdrData)

        val rawScanlines = ByteArray(height * (1 + width * 4))
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(rawScanlines)
        deflater.finish()
        val compressedBuf = ByteArray(rawScanlines.size + 64)
        val compLen = deflater.deflate(compressedBuf)
        deflater.end()
        val idatChunk = makeChunk("IDAT", compressedBuf.copyOf(compLen))

        val iendChunk = makeChunk("IEND", ByteArray(0))

        return sig + ihdrChunk + idatChunk + iendChunk
    }

    private fun makeChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val len = data.size
        val out = ByteArray(4 + 4 + len + 4)
        val bb = ByteBuffer.wrap(out)
        bb.putInt(len)
        bb.put(typeBytes)
        bb.put(data)

        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        bb.putInt(crc.value.toInt())
        return out
    }
}
