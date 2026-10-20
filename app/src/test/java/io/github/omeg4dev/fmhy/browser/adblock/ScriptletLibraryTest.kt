package io.github.omeg4dev.fmhy.browser.adblock

import io.github.omeg4dev.fmhy.data.adblock.Scriptlet
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScriptletLibraryTest {

    private lateinit var library: ScriptletLibrary

    @Before
    fun setUp() {
        val assetsDir = File("src/main/assets").takeIf { it.exists() }
            ?: File("app/src/main/assets")
        assertTrue("Assets directory should exist: ${assetsDir.absolutePath}", assetsDir.exists())
        library = ScriptletLibrary.fromDirectory(assetsDir)
    }

    @Test
    fun testEmptyScriptletList() {
        assertEquals("", library.build(emptyList()))
    }

    @Test
    fun testScriptletDeduplicationAndArgsEncoding() {
        val scriptlets = listOf(
            Scriptlet("set-constant", listOf("adsEnabled", "false")),
            Scriptlet("set-constant", listOf("specialProp", "hello \"world\"\nnewline")),
            Scriptlet("abort-on-property-read", listOf("google_tag"))
        )

        val code = library.build(scriptlets)

        assertTrue(code.startsWith("(function() {\n"))
        assertTrue(code.endsWith("})();\n"))

        // Helpers should be included
        assertTrue(code.contains("function safeSelf()"))

        // Definitions should be included once
        val setConstantDefOccurrences = "function set_constant".toRegex().findAll(code).count()
        assertEquals("set_constant function should only be defined once", 1, setConstantDefOccurrences)

        val abortReadDefOccurrences = "function abort_on_property_read".toRegex().findAll(code).count()
        assertEquals("abort_on_property_read function should only be defined once", 1, abortReadDefOccurrences)

        // Calls should be present with proper JSON encoding
        assertTrue(code.contains("try { set_constant(\"adsEnabled\", \"false\"); } catch (e) {}"))
        assertTrue(code.contains("try { set_constant(\"specialProp\", \"hello \\\"world\\\"\\nnewline\"); } catch (e) {}"))
        assertTrue(code.contains("try { abort_on_property_read(\"google_tag\"); } catch (e) {}"))
    }

    @Test
    fun testUnknownScriptletsAreSkipped() {
        val scriptlets = listOf(
            Scriptlet("unknown-scriptlet-xyz", listOf("a", "b")),
            Scriptlet("set-constant", listOf("foo", "true"))
        )

        val code = library.build(scriptlets)
        assertTrue(code.contains("set_constant"))
        assertFalse(code.contains("unknown_scriptlet_xyz"))
        assertFalse(code.contains("unknown-scriptlet-xyz"))
    }

    @Test
    fun testAllUnknownScriptletsReturnEmpty() {
        val scriptlets = listOf(
            Scriptlet("totally-unknown-1", listOf()),
            Scriptlet("totally-unknown-2", listOf())
        )
        val code = library.build(scriptlets)
        assertEquals("", code)
    }
}
