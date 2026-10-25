package io.github.omeg4dev.fmhy.shortcuts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutIntentTest {
    @Test fun searchAndSaved() {
        assertEquals(ShortcutDestination.Search, shortcutDestination("search"))
        assertEquals(ShortcutDestination.Saved, shortcutDestination("saved"))
    }

    @Test fun whatsNewDefaultsToAllFilter() {
        assertEquals(ShortcutDestination.WhatsNew("all"), shortcutDestination("whats_new"))
        assertEquals(ShortcutDestination.WhatsNew("all"), shortcutDestination("whats_new", filter = ""))
        assertEquals(ShortcutDestination.WhatsNew("followed"), shortcutDestination("whats_new", filter = "followed"))
    }

    @Test fun checkLink() {
        assertEquals(ShortcutDestination.CheckLink, shortcutDestination("check"))
        assertEquals(ShortcutDestination.Posts, shortcutDestination("posts"))
    }

    @Test fun randomKeepsTheShownEntry() {
        assertEquals(ShortcutDestination.Random("e1"), shortcutDestination("random", entryId = "e1"))
        assertEquals(ShortcutDestination.Random(null), shortcutDestination("random"))
        assertEquals(ShortcutDestination.Random(null), shortcutDestination("random", entryId = ""))
    }

    @Test fun pinNeedsASection() {
        assertEquals(ShortcutDestination.Pin("audio", ""), shortcutDestination("pin", sectionId = "audio"))
        assertEquals(ShortcutDestination.Pin("audio", "tools"), shortcutDestination("pin", sectionId = "audio", headingId = "tools"))
        assertNull(shortcutDestination("pin"))
    }

    @Test fun unknownOrMissingIsIgnored() {
        assertNull(shortcutDestination(null))
        assertNull(shortcutDestination("nope"))
    }

    @Test fun entryNeedsAnId() {
        assertEquals(ShortcutDestination.Entry("e1", "audio", "h1"), shortcutDestination("entry", entryId = "e1", sectionId = "audio", headingId = "h1"))
        assertEquals(ShortcutDestination.Entry("e1", null, null), shortcutDestination("entry", entryId = "e1"))
        assertEquals(ShortcutDestination.Entry("e1", null, null), shortcutDestination("entry", entryId = "e1", sectionId = "", headingId = ""))
        assertNull(shortcutDestination("entry"))
        assertNull(shortcutDestination("entry", entryId = ""))
    }
}
