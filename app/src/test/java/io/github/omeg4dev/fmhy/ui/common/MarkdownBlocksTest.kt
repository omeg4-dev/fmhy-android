package io.github.omeg4dev.fmhy.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlocksTest {

    @Test
    fun parse_headings() {
        val md = """
            # Heading 1
            ## Heading 2
            ### Heading 3
            #### Heading 4
            ##### Heading 5 (capped at 4)
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(5, blocks.size)
        assertEquals(MarkdownBlock.Heading(1, "Heading 1"), blocks[0])
        assertEquals(MarkdownBlock.Heading(2, "Heading 2"), blocks[1])
        assertEquals(MarkdownBlock.Heading(3, "Heading 3"), blocks[2])
        assertEquals(MarkdownBlock.Heading(4, "Heading 4"), blocks[3])
        assertEquals(MarkdownBlock.Heading(4, "Heading 5 (capped at 4)"), blocks[4])
    }

    @Test
    fun parse_paragraphs() {
        val md = """
            This is paragraph one
            with two lines.

            This is paragraph two.
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(2, blocks.size)
        assertEquals(MarkdownBlock.Paragraph("This is paragraph one with two lines."), blocks[0])
        assertEquals(MarkdownBlock.Paragraph("This is paragraph two."), blocks[1])
    }

    @Test
    fun parse_nestedBulletList() {
        val md = """
            * Item 1
              * Subitem 1.1
                * Deep item 1.1.1
              * Subitem 1.2
            * Item 2
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(5, blocks.size)

        val i1 = blocks[0] as MarkdownBlock.ListItem
        assertEquals("Item 1", i1.text)
        assertEquals(0, i1.level)
        assertFalse(i1.isOrdered)

        val i11 = blocks[1] as MarkdownBlock.ListItem
        assertEquals("Subitem 1.1", i11.text)
        assertEquals(1, i11.level)
        assertFalse(i11.isOrdered)

        val i111 = blocks[2] as MarkdownBlock.ListItem
        assertEquals("Deep item 1.1.1", i111.text)
        assertEquals(2, i111.level)
        assertFalse(i111.isOrdered)

        val i12 = blocks[3] as MarkdownBlock.ListItem
        assertEquals("Subitem 1.2", i12.text)
        assertEquals(1, i12.level)
        assertFalse(i12.isOrdered)

        val i2 = blocks[4] as MarkdownBlock.ListItem
        assertEquals("Item 2", i2.text)
        assertEquals(0, i2.level)
        assertFalse(i2.isOrdered)
    }

    @Test
    fun parse_nestedNumberedList() {
        val md = """
            1. First step
            2. Second step
               1. Sub-step 2a
               2. Sub-step 2b
            3. Third step
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(5, blocks.size)

        val step1 = blocks[0] as MarkdownBlock.ListItem
        assertEquals("First step", step1.text)
        assertEquals(0, step1.level)
        assertTrue(step1.isOrdered)
        assertEquals(1, step1.number)

        val step2 = blocks[1] as MarkdownBlock.ListItem
        assertEquals("Second step", step2.text)
        assertEquals(0, step2.level)
        assertTrue(step2.isOrdered)
        assertEquals(2, step2.number)

        val step2a = blocks[2] as MarkdownBlock.ListItem
        assertEquals("Sub-step 2a", step2a.text)
        assertEquals(1, step2a.level)
        assertTrue(step2a.isOrdered)
        assertEquals(1, step2a.number)

        val step2b = blocks[3] as MarkdownBlock.ListItem
        assertEquals("Sub-step 2b", step2b.text)
        assertEquals(1, step2b.level)
        assertTrue(step2b.isOrdered)
        assertEquals(2, step2b.number)

        val step3 = blocks[4] as MarkdownBlock.ListItem
        assertEquals("Third step", step3.text)
        assertEquals(0, step3.level)
        assertTrue(step3.isOrdered)
        assertEquals(3, step3.number)
    }

    @Test
    fun parse_table() {
        val md = """
            | Feature | Free | Pro |
            | --- | --- | --- |
            | Storage | 5GB | 100GB |
            | Bandwidth | Unlimited | Unlimited |
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Table)

        val table = blocks[0] as MarkdownBlock.Table
        assertEquals(listOf("Feature", "Free", "Pro"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Storage", "5GB", "100GB"), table.rows[0])
        assertEquals(listOf("Bandwidth", "Unlimited", "Unlimited"), table.rows[1])
    }

    @Test
    fun parse_blockquote() {
        val md = """
            > This is a quote
            > that spans multiple lines.
            
            Normal text outside quote.
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Blockquote)
        val quote = blocks[0] as MarkdownBlock.Blockquote
        assertEquals("This is a quote\nthat spans multiple lines.", quote.text)
        assertEquals(MarkdownBlock.Paragraph("Normal text outside quote."), blocks[1])
    }

    @Test
    fun parse_fencedCode() {
        val md = """
            ```kotlin
            fun answer() = 42
            ```

            ~~~
            plain code block
            ~~~
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(2, blocks.size)
        assertEquals(MarkdownBlock.FencedCode("fun answer() = 42", "kotlin"), blocks[0])
        assertEquals(MarkdownBlock.FencedCode("plain code block", null), blocks[1])
    }

    @Test
    fun parse_horizontalRule() {
        val md = """
            Top text
            ***
            Middle text
            ---
            Bottom text
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(5, blocks.size)
        assertEquals(MarkdownBlock.Paragraph("Top text"), blocks[0])
        assertEquals(MarkdownBlock.HorizontalRule, blocks[1])
        assertEquals(MarkdownBlock.Paragraph("Middle text"), blocks[2])
        assertEquals(MarkdownBlock.HorizontalRule, blocks[3])
        assertEquals(MarkdownBlock.Paragraph("Bottom text"), blocks[4])
    }

    @Test
    fun parse_image() {
        val md = """
            ![FMHY Logo](https://fmhy.net/assets/logo.png)
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(1, blocks.size)
        assertEquals(MarkdownBlock.Image("FMHY Logo", "https://fmhy.net/assets/logo.png"), blocks[0])
    }

    @Test
    fun parse_calloutGitHubStyle() {
        val md = """
            > [!TIP] Pro Tip
            > Always backup your files before testing.
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Callout)
        val callout = blocks[0] as MarkdownBlock.Callout
        assertEquals("TIP", callout.type)
        assertEquals("Pro Tip", callout.title)
        assertEquals(1, callout.children.size)
        assertEquals(MarkdownBlock.Paragraph("Always backup your files before testing."), callout.children[0])
    }

    @Test
    fun parse_fiveNoteLinesFromBeginnersGuide() {
        val notes = listOf(
            "!!!note Using several ad blockers, like uBO and AdGuard at the same time can [mess things up](https://x.com/gorhill/status/1033706103782170625). This only happens with regular ad blockers, so it's perfectly okay to use uBO alongside something like SponsorBlock.",
            "!!!note Most antivirus programs are unnecessary and can cause slowdowns. If you use trusted websites, Windows Defender should be all you need to stay safe, and you can run a [Malwarebytes](https://www.malwarebytes.com/) scan from time to time for extra protection.",
            "!!!note We recommend looking through our [Extension](https://www.reddit.com/r/FREEMEDIAHECKYEAH/wiki/internet-tools#wiki_.25B7_browser_extensions) / [Userscript](https://www.reddit.com/r/FREEMEDIAHECKYEAH/wiki/internet-tools#wiki_.25B7_userscripts) sections to find ways to enhance your browser.",
            "!!!note Never enter your real email or password on any site you don't know you can trust. Use a unique password for each site you register on. That way, if a breach occurs, only that one site's credentials are compromised. You can also take advantage of email [aliasing](https://www.reddit.com/r/FREEMEDIAHECKYEAH/wiki/internet-tools/#wiki_.25B7_email_aliasing) for an extra layer of protection.",
            "!!!note Keep in mind that it's **never** a good idea to upload personal or confidential files to any file host / cloud AI, even if they claim to be encrypted.",
        )

        for ((idx, note) in notes.withIndex()) {
            val blocks = parseMarkdownBlocks(note)
            assertEquals("Note $idx should parse to 1 block", 1, blocks.size)
            assertTrue("Note $idx should be a Callout", blocks[0] is MarkdownBlock.Callout)
            val callout = blocks[0] as MarkdownBlock.Callout
            assertEquals("NOTE", callout.type)
            assertEquals(1, callout.children.size)
            assertTrue(callout.children[0] is MarkdownBlock.Paragraph)
        }
    }

    @Test
    fun parse_infoBlockWithListInside() {
        val md = """
            :::info Important points
            * Point A
            * Point B
            :::
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Callout)
        val callout = blocks[0] as MarkdownBlock.Callout
        assertEquals("INFO", callout.type)
        assertEquals("Important points", callout.title)
        assertEquals(2, callout.children.size)
        assertEquals(MarkdownBlock.ListItem(text = "Point A", level = 0, isOrdered = false, number = 1), callout.children[0])
        assertEquals(MarkdownBlock.ListItem(text = "Point B", level = 0, isOrdered = false, number = 1), callout.children[1])
    }

    @Test
    fun parse_listContinuation() {
        val md = """
            * First item
              with continuation line
              and a second continuation line
            * Second item
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(2, blocks.size)
        val item1 = blocks[0] as MarkdownBlock.ListItem
        assertEquals("First item with continuation line and a second continuation line", item1.text)
        assertEquals(0, item1.level)

        val item2 = blocks[1] as MarkdownBlock.ListItem
        assertEquals("Second item", item2.text)
        assertEquals(0, item2.level)
    }

    @Test
    fun parse_nestedList_fourSpaces() {
        val md = """
            * Level 0 item
                * Level 1 item (4 spaces)
                    * Level 2 item (8 spaces)
                * Level 1 item again
            * Back to Level 0
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(5, blocks.size)
        assertEquals(0, (blocks[0] as MarkdownBlock.ListItem).level)
        assertEquals(1, (blocks[1] as MarkdownBlock.ListItem).level)
        assertEquals(2, (blocks[2] as MarkdownBlock.ListItem).level)
        assertEquals(1, (blocks[3] as MarkdownBlock.ListItem).level)
        assertEquals(0, (blocks[4] as MarkdownBlock.ListItem).level)
    }

    @Test
    fun parse_quoteContainingList() {
        val md = """
            > * Quoted item 1
            > * Quoted item 2
        """.trimIndent()

        val blocks = parseMarkdownBlocks(md)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Blockquote)
        val quote = blocks[0] as MarkdownBlock.Blockquote
        assertEquals(2, quote.children.size)
        assertEquals(MarkdownBlock.ListItem(text = "Quoted item 1", level = 0, isOrdered = false, number = 1), quote.children[0])
        assertEquals(MarkdownBlock.ListItem(text = "Quoted item 2", level = 0, isOrdered = false, number = 1), quote.children[1])
    }

    @Test
    fun headingLevelsAreRelativeToTheShallowestHeading() {
        val blocks = normalizeHeadingLevels(parseMarkdownBlocks("### Adblocking\n\ntext\n\n#### Sub\n\n### Antivirus"))
        val levels = blocks.filterIsInstance<MarkdownBlock.Heading>().map { it.text to it.level }
        assertEquals(listOf("Adblocking" to 1, "Sub" to 2, "Antivirus" to 1), levels)
        val unchanged = parseMarkdownBlocks("# Title\n\n## Part")
        assertEquals(unchanged, normalizeHeadingLevels(unchanged))
    }
}
