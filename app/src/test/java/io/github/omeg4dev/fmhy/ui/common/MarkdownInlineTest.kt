package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownInlineTest {

    private val testColor = Color.Blue

    @Test
    fun plainText() {
        val result = parseMarkdownToAnnotatedString("Just plain text", testColor)
        assertEquals("Just plain text", result.text)
        assertTrue(result.spanStyles.isEmpty())
        assertTrue(result.getLinkAnnotations(0, result.length).isEmpty())
    }

    @Test
    fun boldAsterisks() {
        val result = parseMarkdownToAnnotatedString("Hello **bold** world", testColor)
        assertEquals("Hello bold world", result.text)
        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, boldSpans.size)
        assertEquals(6, boldSpans[0].start)
        assertEquals(10, boldSpans[0].end)
    }

    @Test
    fun boldUnderscores() {
        val result = parseMarkdownToAnnotatedString("Hello __bold__ world", testColor)
        assertEquals("Hello bold world", result.text)
        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, boldSpans.size)
        assertEquals(6, boldSpans[0].start)
        assertEquals(10, boldSpans[0].end)
    }

    @Test
    fun italicAsterisks() {
        val result = parseMarkdownToAnnotatedString("Hello *italic* world", testColor)
        assertEquals("Hello italic world", result.text)
        val italicSpans = result.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertEquals(1, italicSpans.size)
        assertEquals(6, italicSpans[0].start)
        assertEquals(12, italicSpans[0].end)
    }

    @Test
    fun italicUnderscores() {
        val result = parseMarkdownToAnnotatedString("Hello _italic_ world", testColor)
        assertEquals("Hello italic world", result.text)
        val italicSpans = result.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertEquals(1, italicSpans.size)
        assertEquals(6, italicSpans[0].start)
        assertEquals(12, italicSpans[0].end)
    }

    @Test
    fun underscoreWordBoundary_snakeCaseStaysPlain() {
        val result = parseMarkdownToAnnotatedString("This snake_case_identifier stays plain.", testColor)
        assertEquals("This snake_case_identifier stays plain.", result.text)
        val italicSpans = result.spanStyles.filter { it.item.fontStyle == FontStyle.Italic }
        assertTrue(italicSpans.isEmpty())
    }

    @Test
    fun boldItalicAsterisks() {
        val result = parseMarkdownToAnnotatedString("Hello ***bold italic*** world", testColor)
        assertEquals("Hello bold italic world", result.text)
        val spans = result.spanStyles.filter {
            it.item.fontWeight == FontWeight.Bold && it.item.fontStyle == FontStyle.Italic
        }
        assertEquals(1, spans.size)
        assertEquals(6, spans[0].start)
        assertEquals(17, spans[0].end)
    }

    @Test
    fun strikethrough() {
        val result = parseMarkdownToAnnotatedString("This is ~~strike~~ text", testColor)
        assertEquals("This is strike text", result.text)
        val strikeSpans = result.spanStyles.filter { it.item.textDecoration == TextDecoration.LineThrough }
        assertEquals(1, strikeSpans.size)
        assertEquals(8, strikeSpans[0].start)
        assertEquals(14, strikeSpans[0].end)
    }

    @Test
    fun inlineCode() {
        val result = parseMarkdownToAnnotatedString("Here is `val x = 1` in code", testColor)
        assertEquals("Here is \u2009val x = 1\u2009 in code", result.text)
        val codeSpans = result.spanStyles.filter { it.item.fontFamily != null }
        assertEquals(1, codeSpans.size)
    }

    @Test
    fun inlineCode_ignoresMarkdownInside() {
        val result = parseMarkdownToAnnotatedString("Here is `**not bold**` code", testColor)
        assertEquals("Here is \u2009**not bold**\u2009 code", result.text)
        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertTrue(boldSpans.isEmpty())
    }

    @Test
    fun escapedCharacters() {
        val result = parseMarkdownToAnnotatedString("""Literal \*not italic\* and \[not link\] and \_not italic\_ and \~not strike\~""", testColor)
        assertEquals("Literal *not italic* and [not link] and _not italic_ and ~not strike~", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun basicLink() {
        val result = parseMarkdownToAnnotatedString("Visit [FMHY](https://fmhy.net) now", testColor)
        assertEquals("Visit FMHY now", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        val link = links[0].item as LinkAnnotation.Url
        assertEquals("https://fmhy.net", link.url)
        assertEquals(6, links[0].start)
        assertEquals(10, links[0].end)
    }

    @Test
    fun linkWithNestedBold() {
        val result = parseMarkdownToAnnotatedString("Visit [**FMHY**](https://fmhy.net) now", testColor)
        assertEquals("Visit FMHY now", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, boldSpans.size)
        assertEquals(6, boldSpans[0].start)
        assertEquals(10, boldSpans[0].end)
    }

    @Test
    fun boldAroundLink() {
        val result = parseMarkdownToAnnotatedString("Visit **[FMHY](https://fmhy.net)** now", testColor)
        assertEquals("Visit FMHY now", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, boldSpans.size)
        assertEquals(6, boldSpans[0].start)
        assertEquals(10, boldSpans[0].end)
    }

    @Test
    fun linkWithAngleBrackets() {
        val result = parseMarkdownToAnnotatedString("[FMHY](<https://fmhy.net/url with spaces>)", testColor)
        assertEquals("FMHY", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        val link = links[0].item as LinkAnnotation.Url
        assertEquals("https://fmhy.net/url with spaces", link.url)
    }

    @Test
    fun autolinkAngleBrackets() {
        val result = parseMarkdownToAnnotatedString("Check <https://fmhy.net/storage> out", testColor)
        assertEquals("Check https://fmhy.net/storage out", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        val link = links[0].item as LinkAnnotation.Url
        assertEquals("https://fmhy.net/storage", link.url)
    }

    @Test
    fun bareUrl() {
        val result = parseMarkdownToAnnotatedString("Visit https://fmhy.net for links", testColor)
        assertEquals("Visit https://fmhy.net for links", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        val link = links[0].item as LinkAnnotation.Url
        assertEquals("https://fmhy.net", link.url)
    }

    @Test
    fun bareUrlTrailingPunctuation() {
        val result = parseMarkdownToAnnotatedString("Check https://fmhy.net/guide. Also https://fmhy.net/faq!", testColor)
        assertEquals("Check https://fmhy.net/guide. Also https://fmhy.net/faq!", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(2, links.size)
        assertEquals("https://fmhy.net/guide", (links[0].item as LinkAnnotation.Url).url)
        assertEquals("https://fmhy.net/faq", (links[1].item as LinkAnnotation.Url).url)
    }

    @Test
    fun bareUrlBalancedParens() {
        val result = parseMarkdownToAnnotatedString("See (https://en.wikipedia.org/wiki/Foo_(bar)) here", testColor)
        assertEquals("See (https://en.wikipedia.org/wiki/Foo_(bar)) here", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", (links[0].item as LinkAnnotation.Url).url)
    }

    @Test
    fun linkWithBalancedParensInUrl() {
        val result = parseMarkdownToAnnotatedString("[Wiki](https://en.wikipedia.org/wiki/Foo_(bar))", testColor)
        assertEquals("Wiki", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", (links[0].item as LinkAnnotation.Url).url)
    }

    @Test
    fun imageWithAltText() {
        val result = parseMarkdownToAnnotatedString("Look at ![Logo](https://fmhy.net/logo.png)", testColor)
        assertEquals("Look at 🖼 Logo", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        assertEquals("https://fmhy.net/logo.png", (links[0].item as LinkAnnotation.Url).url)
    }

    @Test
    fun imageWithBlankAltText() {
        val result = parseMarkdownToAnnotatedString("Look at ![](https://fmhy.net/logo.png)", testColor)
        assertEquals("Look at 🖼 image", result.text)
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        assertEquals("https://fmhy.net/logo.png", (links[0].item as LinkAnnotation.Url).url)
    }

    @Test
    fun unclosedMarkersRemainLiteral() {
        val result = parseMarkdownToAnnotatedString("Calculate 2 * 3 and a ** b and a lone [ bracket", testColor)
        assertEquals("Calculate 2 * 3 and a ** b and a lone [ bracket", result.text)
        assertTrue(result.spanStyles.isEmpty())
        assertTrue(result.getLinkAnnotations(0, result.length).isEmpty())
    }

    @Test
    fun fullVirusTotalLine() {
        val line = "Always scan downloaded software with **[VirusTotal](https://www.virustotal.com/gui/)** before installation and run it in a sandboxed environment like **[Triage](https://tria.ge/)** to prevent system compromise. ... Using the full version of [uBlock Origin](https://www.reddit.com/r/FREEMEDIAHECKYEAH/wiki/adblock-vpn-privacy#wiki_.25BA_adblocking) can prevent this ..."

        val result = parseMarkdownToAnnotatedString(line, testColor)

        // Expect: text contains no *, [, ](
        assertFalse("Text should not contain *", result.text.contains("*"))
        assertFalse("Text should not contain [", result.text.contains("["))
        assertFalse("Text should not contain ](", result.text.contains("]("))

        // Link annotations for VirusTotal, Triage, uBlock Origin with exact URLs
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(3, links.size)

        val vtLink = links.find { (it.item as LinkAnnotation.Url).url == "https://www.virustotal.com/gui/" }
        assertNotNull("VirusTotal link missing", vtLink)
        val vtText = result.text.substring(vtLink!!.start, vtLink.end)
        assertEquals("VirusTotal", vtText)

        val triageLink = links.find { (it.item as LinkAnnotation.Url).url == "https://tria.ge/" }
        assertNotNull("Triage link missing", triageLink)
        val triageText = result.text.substring(triageLink!!.start, triageLink.end)
        assertEquals("Triage", triageText)

        val uboLink = links.find { (it.item as LinkAnnotation.Url).url == "https://www.reddit.com/r/FREEMEDIAHECKYEAH/wiki/adblock-vpn-privacy#wiki_.25BA_adblocking" }
        assertNotNull("uBlock Origin link missing", uboLink)
        val uboText = result.text.substring(uboLink!!.start, uboLink.end)
        assertEquals("uBlock Origin", uboText)

        // VirusTotal range is bold
        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        val vtBold = boldSpans.find { it.start <= vtLink.start && it.end >= vtLink.end }
        assertNotNull("VirusTotal range should be bold", vtBold)
    }

    @Test
    fun timingCase_5000CharsUnder20ms() {
        val paragraph = buildString {
            while (length < 5000) {
                append("Some text with **bold** words, *italic* phrases, `code snippets`, ")
                append("and [links](https://example.com/test) plus bare https://example.com/site. ")
            }
        }.take(5000)

        // Warmup
        repeat(5) {
            parseMarkdownToAnnotatedString(paragraph, testColor)
        }

        val startNs = System.nanoTime()
        parseMarkdownToAnnotatedString(paragraph, testColor)
        val durationMs = (System.nanoTime() - startNs) / 1_000_000.0

        assertTrue("Expected < 20 ms, was $durationMs ms", durationMs < 20.0)
    }

    @Test
    fun clickableListener_whenOnOpenUrlGiven() {
        var clickedUrl = ""
        val result = parseMarkdownToAnnotatedString(
            text = "Go to [FMHY](https://fmhy.net)",
            linkColor = testColor,
            onOpenUrl = { clickedUrl = it },
        )
        val links = result.getLinkAnnotations(0, result.length)
        assertEquals(1, links.size)
        val link = links[0].item as LinkAnnotation.Clickable
        assertEquals("https://fmhy.net", link.tag)
        assertNotNull(link.linkInteractionListener)
        link.linkInteractionListener?.onClick(link)
        assertEquals("https://fmhy.net", clickedUrl)
    }
}
