package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkMenuTest {

    @Test
    fun linkYieldsFiveItemsInExactOrder() {
        val items = linkMenuItems(HitKind.LINK, "https://fmhy.net/wiki")
        assertEquals(5, items.size)
        assertEquals(LinkMenuAction.OPEN_NEW_TAB, items[0].action)
        assertEquals("Open in new tab", items[0].label)
        assertEquals(LinkMenuAction.OPEN_BACKGROUND, items[1].action)
        assertEquals("Open in background tab", items[1].label)
        assertEquals(LinkMenuAction.OPEN_PRIVATE, items[2].action)
        assertEquals("Open in private tab", items[2].label)
        assertEquals(LinkMenuAction.COPY_LINK, items[3].action)
        assertEquals("Copy link", items[3].label)
        assertEquals(LinkMenuAction.SHARE_LINK, items[4].action)
        assertEquals("Share link", items[4].label)
    }

    @Test
    fun imageLinkYieldsSameFiveItems() {
        val items = linkMenuItems(HitKind.IMAGE_LINK, "https://fmhy.net/wiki")
        assertEquals(5, items.size)
        assertEquals(LinkMenuAction.OPEN_NEW_TAB, items[0].action)
        assertEquals("Open in new tab", items[0].label)
        assertEquals(LinkMenuAction.OPEN_BACKGROUND, items[1].action)
        assertEquals("Open in background tab", items[1].label)
        assertEquals(LinkMenuAction.OPEN_PRIVATE, items[2].action)
        assertEquals("Open in private tab", items[2].label)
        assertEquals(LinkMenuAction.COPY_LINK, items[3].action)
        assertEquals("Copy link", items[3].label)
        assertEquals(LinkMenuAction.SHARE_LINK, items[4].action)
        assertEquals("Share link", items[4].label)
    }

    @Test
    fun imageYieldsTwoImageItems() {
        val items = linkMenuItems(HitKind.IMAGE, "https://fmhy.net/banner.png")
        assertEquals(2, items.size)
        assertEquals(LinkMenuAction.OPEN_IMAGE_NEW_TAB, items[0].action)
        assertEquals("Open image in new tab", items[0].label)
        assertEquals(LinkMenuAction.COPY_IMAGE_LINK, items[1].action)
        assertEquals("Copy image link", items[1].label)
    }

    @Test
    fun otherKindYieldsEmpty() {
        val items = linkMenuItems(HitKind.OTHER, "https://fmhy.net")
        assertTrue(items.isEmpty())
    }

    @Test
    fun javascriptWithNoRealAddressYieldsEmpty() {
        val itemsDefault = linkMenuItems(HitKind.LINK, "javascript:void(0)")
        assertTrue(itemsDefault.isEmpty())

        val itemsExplicit = linkMenuItems(HitKind.LINK, "javascript:alert(1)", hasRealAddress = false)
        assertTrue(itemsExplicit.isEmpty())

        val itemsBlank = linkMenuItems(HitKind.LINK, "")
        assertTrue(itemsBlank.isEmpty())

        val itemsNull = linkMenuItems(HitKind.LINK, null)
        assertTrue(itemsNull.isEmpty())
    }

    @Test
    fun javascriptWithRealAddressYieldsOnlyCopyLink() {
        val items = linkMenuItems(HitKind.LINK, "javascript:window.open('https://fmhy.net')", hasRealAddress = true)
        assertEquals(1, items.size)
        assertEquals(LinkMenuAction.COPY_LINK, items[0].action)
        assertEquals("Copy link", items[0].label)
    }

    @Test
    fun tabPrivacyForDecisions() {
        // private opener + new/background -> private
        assertTrue(tabPrivacyFor(openerPrivate = true, action = LinkMenuAction.OPEN_NEW_TAB))
        assertTrue(tabPrivacyFor(openerPrivate = true, action = LinkMenuAction.OPEN_BACKGROUND))
        assertTrue(tabPrivacyFor(openerPrivate = true, action = LinkMenuAction.OPEN_IMAGE_NEW_TAB))

        // normal opener + OPEN_PRIVATE -> private
        assertTrue(tabPrivacyFor(openerPrivate = false, action = LinkMenuAction.OPEN_PRIVATE))

        // normal opener + new/background -> normal
        assertFalse(tabPrivacyFor(openerPrivate = false, action = LinkMenuAction.OPEN_NEW_TAB))
        assertFalse(tabPrivacyFor(openerPrivate = false, action = LinkMenuAction.OPEN_BACKGROUND))
        assertFalse(tabPrivacyFor(openerPrivate = false, action = LinkMenuAction.OPEN_IMAGE_NEW_TAB))
    }

    @Test
    fun shortenAddressEllipsizesLongUrlsAndKeepsShortOnes() {
        val shortUrl = "https://example.com"
        assertEquals(shortUrl, shortenAddress(shortUrl, max = 48))

        val longUrl = "https://example.com/a/very/long/path/that/exceeds/the/default/maximum/length/of/characters"
        val shortened = shortenAddress(longUrl, max = 48)
        assertEquals(48, shortened.length)
        assertTrue(shortened.endsWith("…"))
        assertEquals(longUrl.take(47) + "…", shortened)

        // Exact threshold check
        val exact48 = "a".repeat(48)
        assertEquals(exact48, shortenAddress(exact48, max = 48))

        val exact49 = "a".repeat(49)
        assertEquals("a".repeat(47) + "…", shortenAddress(exact49, max = 48))
    }
}
