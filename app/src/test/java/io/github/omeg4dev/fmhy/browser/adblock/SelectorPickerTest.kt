package io.github.omeg4dev.fmhy.browser.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectorPickerTest {

    @Test
    fun testComputeRuleFormatsCorrectly() {
        assertEquals("example.com##.ad-banner", SelectorPicker.computeRule("example.com", ".ad-banner"))
        assertEquals("example.com##div > p", SelectorPicker.computeRule("www.example.com", "div > p"))
        assertEquals("sub.example.com###promo", SelectorPicker.computeRule("sub.example.com", "#promo"))
    }

    @Test
    fun testFilterClassesSkipsDigitsAndLongRandomStrings() {
        val classes = listOf("header", "12345", "nav-bar", "a-very-long-random-class-name-exceeding-20-chars", "main-item", "extra")
        val filtered = SelectorPicker.filterClasses(classes)

        assertEquals(3, filtered.size)
        assertEquals(listOf("header", "nav-bar", "main-item"), filtered)
    }
}
