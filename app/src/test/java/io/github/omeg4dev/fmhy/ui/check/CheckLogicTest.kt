package io.github.omeg4dev.fmhy.ui.check

import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class CheckLogicTest {
    private fun rc(url: String, ts: Long) = RecentCheck(url, SafetyStatus.NO_DATA, ts)

    @Test fun dropsTypingStages() {
        val list = listOf(rc("example.com", 5), rc("example.co", 4), rc("exa", 3), rc("github.com", 2))
        assertEquals(listOf("example.com", "github.com"), pruneRecentChecks(list).map { it.url })
    }

    @Test fun keepsUnrelatedAndEqualLengthDifferent() {
        val list = listOf(rc("a.com", 3), rc("b.com", 2), rc("c.org", 1))
        assertEquals(3, pruneRecentChecks(list).size)
    }

    @Test fun olderLongerEntryDoesNotShadowNewerPrefix() {
        val list = listOf(rc("exa", 9), rc("example.com", 1))
        assertEquals(2, pruneRecentChecks(list).size)
    }

    @Test fun dropsCaseDuplicates() {
        val list = listOf(rc("Example.com", 5), rc("example.com", 3))
        assertEquals(listOf("Example.com"), pruneRecentChecks(list).map { it.url })
    }

    @Test fun breadcrumbSkipsRepeatedSection() {
        assertEquals(listOf("Privacy", "Adblocking"), matchBreadcrumb("Privacy", "Privacy > Adblocking"))
        assertEquals(listOf("Privacy", "Adblocking"), matchBreadcrumb("Privacy", "Adblocking"))
        assertEquals(listOf("A", "B"), matchBreadcrumb(null, "A > B"))
    }
}
