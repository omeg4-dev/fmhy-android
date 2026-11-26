package io.github.omeg4dev.fmhy.data.nsfw

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NsfwCheckpointTest {

    private fun b64(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    @Test
    fun picksYasoLinkAndIgnoresOtherMirrors() {
        val privateBin = b64("https://privatebin.example/?abc123#secretkey")
        val yaso = b64("https://yaso.su/paste42")
        val html = """
            <html><body>
              <p>Mirror 1 (PrivateBin): <code>$privateBin</code></p>
              <p>Mirror 2 (Yaso): <code>$yaso</code></p>
            </body></html>
        """.trimIndent()

        assertEquals(
            "https://yaso.su/raw/paste42",
            NsfwCheckpoint.extractYasoRawUrl(html),
        )
    }

    @Test
    fun supportsUnpaddedAndUrlSafeBase64AndTrailingSlash() {
        val unpadded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("https://yaso.su/abc_99-z/".toByteArray(Charsets.UTF_8))
        val html = "<p>Link: $unpadded</p>"

        assertEquals(
            "http://localhost:8080/raw/abc_99-z",
            NsfwCheckpoint.extractYasoRawUrl(html, rawBaseUrl = "http://localhost:8080/"),
        )
    }

    @Test
    fun requiresExactYasoHostAndSinglePathSegment() {
        val subHost = b64("https://www.yaso.su/abc")
        val lookalike = b64("https://notyaso.su/abc")
        val rootOnly = b64("https://yaso.su/")
        val multiSegment = b64("https://yaso.su/raw/abc")
        val valid = b64("https://yaso.su/good1")

        val html = """
            <div>$subHost</div>
            <div>$lookalike</div>
            <div>$rootOnly</div>
            <div>$multiSegment</div>
            <div>$valid</div>
        """.trimIndent()

        assertEquals(
            "https://yaso.su/raw/good1",
            NsfwCheckpoint.extractYasoRawUrl(html),
        )
    }

    @Test
    fun returnsNullWhenNoYasoLinkExists() {
        val other = b64("https://example.org/something")
        assertNull(NsfwCheckpoint.extractYasoRawUrl("<html><body><p>$other</p></body></html>"))
        assertNull(NsfwCheckpoint.extractYasoRawUrl("<html><body>No tokens</body></html>"))
    }
}
