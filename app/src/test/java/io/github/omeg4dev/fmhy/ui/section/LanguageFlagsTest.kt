package io.github.omeg4dev.fmhy.ui.section

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageFlagsTest {

    private val nonEnglishHeadings = listOf(
        "► Arabic / العربية",
        "► Bangla / বাংলা",
        "► Bulgarian / Български",
        "► Chinese / 华语",
        "► Czech / Čeština",
        "► Filipino / Pinoy",
        "► Finnish / Suomi",
        "► French / Français",
        "► German / Deutsch",
        "► Greek / Ελληνικά",
        "► Hebrew / עברית",
        "► Hungarian / Magyar",
        "► Indian Languages",
        "► Indonesian / Bahasa Indonesia",
        "► Italian / Italiano",
        "► Japanese / 日本語",
        "► Korean / 한국어",
        "► Persian / فارسی",
        "► Polish / Polski",
        "► Portuguese / Português",
        "► Romanian / Limba Română",
        "► Russian / Русский",
        "► Slovak / Slovenčina",
        "► Spanish / Español",
        "► Swedish / Svenska",
        "► Thai / ไทย",
        "► Turkish / Türkçe",
        "► Ukrainian / Українська",
        "► Uzbek / Ўзбек",
        "► Vietnamese / Tiếng Việt",
        "► Other Languages",
    )

    @Test
    fun testEveryLevel1HeadingMapsToNonNull() {
        for (heading in nonEnglishHeadings) {
            val flag = flagFor(heading)
            assertNotNull("Heading '$heading' should map to a non-null flag", flag)

            // Also test stripped format as produced by parser
            val stripped = heading.replace("►", "").trim()
            val flagStripped = flagFor(stripped)
            assertEquals("Flag for '$stripped' should match '$heading'", flag, flagStripped)
        }
    }

    @Test
    fun testSpotChecks() {
        assertEquals("🇸🇦", flagFor("► Arabic / العربية"))
        assertEquals("🇸🇦", flagFor("Arabic / العربية"))
        assertEquals("🇩🇪", flagFor("► German / Deutsch"))
        assertEquals("🇩🇪", flagFor("German / Deutsch"))
        assertEquals("🇨🇳", flagFor("► Chinese / 华语"))
        assertEquals("🇨🇳", flagFor("Chinese / 华语"))
        assertEquals("🇧🇩", flagFor("► Bangla / বাংলা"))
        assertEquals("🇵🇭", flagFor("► Filipino / Pinoy"))
        assertEquals("🇮🇷", flagFor("► Persian / فارسی"))
        assertEquals("🇮🇳", flagFor("► Indian Languages"))
        assertEquals("🇰🇷", flagFor("► Korean / 한국어"))
        assertEquals("🇯🇵", flagFor("► Japanese / 日本語"))
        assertEquals("🇺🇦", flagFor("► Ukrainian / Українська"))
        assertEquals("🇪🇸", flagFor("► Spanish / Español"))
        assertEquals("🇵🇹", flagFor("► Portuguese / Português"))
        assertEquals("🇧🇷", flagFor("► Brazilian Portuguese / Português do Brasil"))
        assertEquals("🌍", flagFor("► Other Languages"))
    }

    @Test
    fun testSectionAwareWrapper() {
        assertEquals("🇸🇦", flagFor("non-english", "► Arabic / العربية"))
        assertEquals("🇩🇪", flagFor("non-english", "► German / Deutsch"))
        assertEquals("🇨🇳", flagFor("non-english", "► Chinese / 华语"))

        // Headings from another section or with null sectionId return null
        assertNull(flagFor("video", "► Arabic / العربية"))
        assertNull(flagFor("reading", "► German / Deutsch"))
        assertNull(flagFor("audio", "► Chinese / 华语"))
        assertNull(flagFor(null, "► Arabic / العربية"))

        // flagForSection alias works equivalently
        assertEquals("🇸🇦", flagForSection("non-english", "► Arabic / العربية"))
        assertNull(flagForSection("games", "► Arabic / العربية"))
    }
}
