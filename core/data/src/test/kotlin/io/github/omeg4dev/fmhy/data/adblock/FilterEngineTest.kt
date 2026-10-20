package io.github.omeg4dev.fmhy.data.adblock

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FilterEngineTest {

    private data class TestCase(
        val lineNum: Int,
        val rawLine: String,
        val rules: String,
        val url: String?,
        val src: String?,
        val type: ResourceType,
        val expected: String?,
        val page: String?,
        val cosmeticExpect: String?,
        val classes: List<String>,
        val ids: List<String>,
        val genericExpect: List<String>?,
        val scriptletExpect: String?
    )

    private fun parseCases(): List<TestCase> {
        val stream = javaClass.classLoader.getResourceAsStream("adblock/cases.txt")
            ?: error("adblock/cases.txt resource not found")
        val cases = mutableListOf<TestCase>()
        var lineNum = 0
        stream.bufferedReader().useLines { lines ->
            for (raw in lines) {
                lineNum++
                val trimmed = raw.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                val parts = trimmed.split(" ;; ")
                var rules = ""
                var url: String? = null
                var src: String? = null
                var type = ResourceType.OTHER
                var expected: String? = null
                var page: String? = null
                var cosmeticExpect: String? = null
                var classes = emptyList<String>()
                var ids = emptyList<String>()
                var genericExpect: List<String>? = null
                var scriptletExpect: String? = null

                for (p in parts) {
                    val kv = p.split(" ", limit = 2)
                    val k = kv[0].trim()
                    val v = if (kv.size > 1) kv[1].trim() else ""
                    when (k) {
                        "RULES" -> rules = v.replace("\\n", "\n")
                        "URL" -> url = v
                        "SRC" -> src = v
                        "TYPE" -> type = ResourceType.valueOf(v)
                        "EXPECT" -> expected = v
                        "PAGE" -> page = v
                        "COSMETIC_EXPECT" -> cosmeticExpect = v
                        "CLASSES" -> classes = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        "IDS" -> ids = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        "GENERIC_EXPECT" -> genericExpect = v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        "SCRIPTLET_EXPECT" -> scriptletExpect = v
                    }
                }

                cases.add(
                    TestCase(
                        lineNum, trimmed, rules, url, src, type, expected,
                        page, cosmeticExpect, classes, ids, genericExpect, scriptletExpect
                    )
                )
            }
        }
        return cases
    }

    private fun runCaseOnEngine(c: TestCase, engine: FilterEngine) {
        if (c.url != null) {
            val src = c.src ?: c.url
            val res = engine.match(FilterRequest(c.url, src, c.type))
            when (c.expected) {
                "Block" -> assertTrue("Line ${c.lineNum}: Expected Block but got $res for ${c.url}", res is MatchResult.Block)
                "Allow" -> assertTrue("Line ${c.lineNum}: Expected Allow but got $res for ${c.url}", res is MatchResult.Allow)
                "NoMatch" -> assertTrue("Line ${c.lineNum}: Expected NoMatch but got $res for ${c.url}", res is MatchResult.NoMatch)
                else -> {
                    if (c.expected?.startsWith("Redirect:") == true) {
                        val target = c.expected.removePrefix("Redirect:")
                        assertTrue("Line ${c.lineNum}: Expected Redirect but got $res", res is MatchResult.Redirect)
                        assertEquals("Line ${c.lineNum}: Redirect target mismatch", target, (res as MatchResult.Redirect).resource)
                    } else {
                        fail("Line ${c.lineNum}: Unknown expectation ${c.expected}")
                    }
                }
            }
        }

        if (c.cosmeticExpect != null) {
            val page = c.page ?: error("Line ${c.lineNum}: Cosmetic test missing PAGE")
            val css = engine.cosmeticCss(page)
            assertEquals("Line ${c.lineNum}: Cosmetic CSS mismatch", c.cosmeticExpect, css)
        }

        if (c.genericExpect != null) {
            val page = c.page ?: error("Line ${c.lineNum}: Generic test missing PAGE")
            val selectors = engine.genericSelectors(page, c.classes, c.ids)
            assertEquals("Line ${c.lineNum}: Generic selectors mismatch", c.genericExpect, selectors)
        }

        if (c.scriptletExpect != null) {
            val page = c.page ?: error("Line ${c.lineNum}: Scriptlet test missing PAGE")
            val scriptlets = engine.scriptlets(page)
            val actual = scriptlets.joinToString(";") { s ->
                "${s.name}(${s.args.joinToString(",")})"
            }
            assertEquals("Line ${c.lineNum}: Scriptlets mismatch", c.scriptletExpect, actual)
        }
    }

    @Test
    fun testAllCasesFromFixture() {
        val cases = parseCases()
        assertTrue("Expected at least 60 cases but found ${cases.size}", cases.size >= 60)

        for (c in cases) {
            val builder = FilterEngineBuilder()
            builder.addCustomRules(c.rules)
            val engine = builder.build()
            runCaseOnEngine(c, engine)
        }
    }

    @Test
    fun testSerializationRoundTrip() {
        val cases = parseCases()
        val allRules = StringBuilder()
        for (c in cases) {
            allRules.append(c.rules).append("\n")
        }

        val builder = FilterEngineBuilder()
        builder.addCustomRules(allRules.toString())
        val originalEngine = builder.build()

        val baos = ByteArrayOutputStream()
        originalEngine.writeTo(baos)
        val bytes = baos.toByteArray()

        val restoredEngine = FilterEngine.readFrom(ByteArrayInputStream(bytes))

        // Re-run every case against restored engine
        for (c in cases) {
            val caseBuilder = FilterEngineBuilder()
            caseBuilder.addCustomRules(c.rules)
            val caseEngine = caseBuilder.build()

            val caseBaos = ByteArrayOutputStream()
            caseEngine.writeTo(caseBaos)
            val restoredCaseEngine = FilterEngine.readFrom(ByteArrayInputStream(caseBaos.toByteArray()))
            runCaseOnEngine(c, restoredCaseEngine)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testReadFromInvalidMagic() {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeInt(0x12345678) // wrong magic
        dos.writeInt(1) // version
        dos.flush()

        FilterEngine.readFrom(ByteArrayInputStream(baos.toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testReadFromInvalidVersion() {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeInt(0x464D4859) // valid magic "FMHY"
        dos.writeInt(999) // wrong version
        dos.flush()

        FilterEngine.readFrom(ByteArrayInputStream(baos.toByteArray()))
    }

    @Test
    fun testNeuteredResources() {
        val noopjs = NeuteredResources.get("noopjs")
        assertNotNull(noopjs)
        assertEquals("application/javascript", noopjs!!.mimeType)
        assertEquals("(function(){})();", String(noopjs.bytes, Charsets.UTF_8))

        val gif = NeuteredResources.get("1x1.gif")
        assertNotNull(gif)
        assertEquals("image/gif", gif!!.mimeType)
        assertEquals(43, gif.bytes.size)

        val png2x2 = NeuteredResources.get("2x2.png")
        assertNotNull(png2x2)
        assertEquals("image/png", png2x2!!.mimeType)
        assertTrue(png2x2.bytes.isNotEmpty())

        val png3x2 = NeuteredResources.get("3x2.png")
        assertNotNull(png3x2)
        assertEquals("image/png", png3x2!!.mimeType)
        assertTrue(png3x2.bytes.isNotEmpty())

        val png32x32 = NeuteredResources.get("32x32.png")
        assertNotNull(png32x32)
        assertEquals("image/png", png32x32!!.mimeType)
        assertTrue(png32x32.bytes.isNotEmpty())

        assertNull(NeuteredResources.get("unknown_resource_xyz"))
    }

    @Test
    fun testFilterListMetaParsing() {
        val text = """
            [Adblock Plus 2.0]
            ! Title: EasyList Test
            ! Expires: 5 days
            ! Homepage: https://easylist.to/
        """.trimIndent()

        val meta = FilterListMeta.parse(text)
        assertEquals("EasyList Test", meta.title)
        assertEquals(5.days, meta.expires)

        val textHours = """
            ! Title: Quick Fixes
            ! Expires: 12 hours
        """.trimIndent()
        val metaHours = FilterListMeta.parse(textHours)
        assertEquals("Quick Fixes", metaHours.title)
        assertEquals(12.hours, metaHours.expires)
    }

    @Test
    fun testResourceTypesInfer() {
        assertEquals(ResourceType.DOCUMENT, ResourceTypes.infer("https://example.com/", isMainFrame = true, acceptHeader = null))
        assertEquals(ResourceType.SUBDOCUMENT, ResourceTypes.infer("https://example.com/frame", isMainFrame = false, acceptHeader = null, isFrameNavigation = true))
        assertEquals(ResourceType.WEBSOCKET, ResourceTypes.infer("wss://example.com/socket", isMainFrame = false, acceptHeader = null))
        assertEquals(ResourceType.SCRIPT, ResourceTypes.infer("https://example.com/app.js", isMainFrame = false, acceptHeader = null))
        assertEquals(ResourceType.STYLESHEET, ResourceTypes.infer("https://example.com/style.css", isMainFrame = false, acceptHeader = null))
        assertEquals(ResourceType.IMAGE, ResourceTypes.infer("https://example.com/logo.png", isMainFrame = false, acceptHeader = null))
        assertEquals(ResourceType.MEDIA, ResourceTypes.infer("https://example.com/song.mp3", isMainFrame = false, acceptHeader = null))
        assertEquals(ResourceType.FONT, ResourceTypes.infer("https://example.com/font.woff2", isMainFrame = false, acceptHeader = null))
        assertEquals(ResourceType.XHR, ResourceTypes.infer("https://example.com/api/data", isMainFrame = false, acceptHeader = "application/json"))
        assertEquals(ResourceType.OTHER, ResourceTypes.infer("https://example.com/api/unknown", isMainFrame = false, acceptHeader = null))
    }
}
