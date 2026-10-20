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
}
