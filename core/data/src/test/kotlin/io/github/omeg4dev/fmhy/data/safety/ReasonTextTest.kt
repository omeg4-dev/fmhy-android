package io.github.omeg4dev.fmhy.data.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReasonTextTest {

    @Test fun fakeClones() {
        assertEquals(
            "Fake clones exist",
            readableReason("Fake clones exist - https://i.ibb.co/2YHqHmVW/image.png"),
        )
    }

    @Test fun historyOfDdos() {
        assertEquals(
            "History of ddos attacks",
            readableReason("History of ddos attacks - https://github.com/fmhy/edit/pull/307"),
        )
    }

    @Test fun multipleLinksPreservesMiddleText() {
        assertEquals(
            "History of spam / spying",
            readableReason("History of spam - https://redd.it/12h2v6n / spying - https://rentry.co/tvrnw"),
        )
    }

    @Test fun fakeBlackBox() {
        assertEquals(
            "Fake BlackBox Repacks, contain malware",
            readableReason("Fake BlackBox Repacks, contain malware - https://rentry.org/pgames#untrusted-uploaders"),
        )
    }

    @Test fun avastWindows() {
        assertEquals(
            "Owned by Avast, it's best to use the built-in Windows tool or BleachBit",
            readableReason("Owned by Avast, it's best to use the built-in Windows tool or BleachBit - https://www.bleachbit.org/"),
        )
    }

    @Test fun markdownLink() {
        assertEquals(
            "See this post for details",
            readableReason("See [this post](https://redd.it/abc) for details"),
        )
    }

    @Test fun urlInParens() {
        assertEquals(
            "Contains miners",
            readableReason("Contains miners (https://example.com/proof)"),
        )
    }

    @Test fun plainText() {
        assertEquals("Malware", readableReason("Malware"))
    }

    @Test fun onlyUrl() {
        assertNull(readableReason("https://example.com"))
    }

    @Test fun emptyAndBlankAndNull() {
        assertNull(readableReason(""))
        assertNull(readableReason("   "))
        assertNull(readableReason(null))
    }
}
