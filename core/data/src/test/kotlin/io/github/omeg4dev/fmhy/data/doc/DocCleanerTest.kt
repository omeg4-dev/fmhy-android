package io.github.omeg4dev.fmhy.data.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DocCleanerTest {

    @Test
    fun stripFrontMatter_removesYamlHeader() {
        val input = """
            ---
            title: FAQ
            description: A List Of Questions And Answers.
            date: 2026-10-01
            ---
            # FAQs
            Here are the answers.
        """.trimIndent()

        val cleaned = DocCleaner.clean(input)
        assertFalse(cleaned.contains("---"))
        assertFalse(cleaned.contains("description:"))
        assertTrue(cleaned.startsWith("# FAQs"))
    }

    @Test
    fun extractFrontMatter_parsesKeyValuePairs() {
        val input = """
            ---
            title: Monthly Updates [April]
            description: "April 2026 updates"
            date: 2026-04-01
            next: false
            ---
            # Content
        """.trimIndent()

        val fm = DocCleaner.extractFrontMatter(input)
        assertEquals("Monthly Updates [April]", fm["title"])
        assertEquals("April 2026 updates", fm["description"])
        assertEquals("2026-04-01", fm["date"])
        assertEquals("false", fm["next"])
    }

    @Test
    fun stripVueComponents_removesSelfClosingAndPairedTags() {
        val input = """
            <Post authors="nbats"/>
            
            # Update
            <StartPage />
            Some text with <Foo>bar</Foo> and <Index/>.
        """.trimIndent()

        val cleaned = DocCleaner.clean(input)
        assertFalse(cleaned.contains("<Post"))
        assertFalse(cleaned.contains("<StartPage"))
        assertFalse(cleaned.contains("<Foo>"))
        assertFalse(cleaned.contains("</Foo>"))
        assertFalse(cleaned.contains("<Index/>"))
        assertTrue(cleaned.contains("# Update"))
        assertTrue(cleaned.contains("Some text with  and ."))
    }

    @Test
    fun stripHtmlComments_removesComments() {
        val input = """
            # Header
            <!-- single line comment -->
            Visible text
            <!--
            multi
            line
            comment
            -->
            More visible text
        """.trimIndent()

        val cleaned = DocCleaner.clean(input)
        assertFalse(cleaned.contains("comment"))
        assertTrue(cleaned.contains("# Header"))
        assertTrue(cleaned.contains("Visible text"))
        assertTrue(cleaned.contains("More visible text"))
    }

    @Test
    fun stripScripts_removesScriptSetupBlocks() {
        val input = """
            <script setup>
            import Index from './.vitepress/theme/Posts.vue'
            </script>
            
            # Hello
        """.trimIndent()

        val cleaned = DocCleaner.clean(input)
        assertFalse(cleaned.contains("<script"))
        assertFalse(cleaned.contains("import Index"))
        assertEquals("# Hello", cleaned)
    }

    @Test
    fun transformContainers_convertsTipWithTitleToBlockquote() {
        val input = """
            :::tip What is this?
            Anonymous comments taken from Reddit, Discord, X.com.
            :::
        """.trimIndent()

        val cleaned = DocCleaner.clean(input)
        assertFalse(cleaned.contains(":::"))
        assertTrue(cleaned.contains("> [!TIP] What is this?"))
        assertTrue(cleaned.contains("> Anonymous comments taken from Reddit, Discord, X.com."))
    }

    @Test
    fun transformContainers_convertsInfoWithoutTitleToBlockquote() {
        val input = """
            :::info
            These update threads only contain major updates.
            :::
        """.trimIndent()

        val cleaned = DocCleaner.clean(input)
        assertFalse(cleaned.contains(":::"))
        assertTrue(cleaned.contains("> [!INFO]"))
        assertTrue(cleaned.contains("> These update threads only contain major updates."))
    }

    @Test
    fun clean_fiveNoteLinesFromBeginnersGuide() {
        val notes = listOf(
            "!!!note Using several ad blockers, like uBO and AdGuard at the same time can [mess things up](https://x.com/gorhill/status/1033706103782170625). This only happens with regular ad blockers, so it's perfectly okay to use uBO alongside something like SponsorBlock.",
            "!!!note Most antivirus programs are unnecessary and can cause slowdowns. If you use trusted websites, Windows Defender should be all you need to stay safe, and you can run a [Malwarebytes](https://www.malwarebytes.com/) scan from time to time for extra protection.",
            "!!!note We recommend looking through our [Extension](https://www.reddit.com/r/FREEMEDIAHECKYEAH/wiki/internet-tools#wiki_.25B7_browser_extensions) / [Userscript](https://www.reddit.com/r/FREEMEDIAHECKYEAH/wiki/internet-tools#wiki_.25B7_userscripts) sections to find ways to enhance your browser.",
            "!!!note Never enter your real email or password on any site you don't know you can trust. Use a unique password for each site you register on. That way, if a breach occurs, only that one site's credentials are compromised. You can also take advantage of email [aliasing](https://www.reddit.com/r/FREEMEDIAHECKYEAH/wiki/internet-tools/#wiki_.25B7_email_aliasing) for an extra layer of protection.",
            "!!!note Keep in mind that it's **never** a good idea to upload personal or confidential files to any file host / cloud AI, even if they claim to be encrypted.",
        )

        for ((idx, noteLine) in notes.withIndex()) {
            val cleaned = DocCleaner.clean(noteLine)
            assertFalse("Note $idx should not have raw !!!", cleaned.contains("!!!"))
            assertTrue("Note $idx should have [!NOTE] callout marker", cleaned.contains("> [!NOTE]"))
            val expectedText = noteLine.removePrefix("!!!note").trim()
            assertTrue("Note $idx should preserve body text", cleaned.contains(expectedText))
        }
    }

    @Test
    fun clean_sampleFiles_ifPresent() {
        val sampleFiles = listOf(
            File(".cache/fmhy-edit/docs/feedback.md"),
            File(".cache/fmhy-edit/docs/other/FAQ.md"),
            File(".cache/fmhy-edit/docs/posts/april-2026.md"),
            File(".cache/fmhy-edit/docs/beginners-guide.md"),
        )

        for (file in sampleFiles) {
            if (!file.exists()) continue
            val raw = file.readText()
            val cleaned = DocCleaner.clean(raw)
            assertFalse("Cleaned ${file.name} should not have front matter", cleaned.startsWith("---"))
            assertFalse("Cleaned ${file.name} should not have ::: fences", cleaned.contains(":::"))
            assertFalse("Cleaned ${file.name} should not have <script", cleaned.contains("<script", ignoreCase = true))
            assertFalse("Cleaned ${file.name} should not have HTML comments", cleaned.contains("<!--"))
            assertFalse("Cleaned ${file.name} should not have <Post", cleaned.contains("<Post"))
            assertTrue("Cleaned ${file.name} should not be empty", cleaned.isNotBlank())
        }
    }
}
