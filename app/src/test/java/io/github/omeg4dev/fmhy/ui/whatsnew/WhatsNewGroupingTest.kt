package io.github.omeg4dev.fmhy.ui.whatsnew

import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.user.Follow
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewGroupingTest {

    private val utcZone = ZoneId.of("UTC")
    private val nowSec = 1760800000L // arbitrary fixed point

    private fun makeChange(
        id: Long,
        ts: Long,
        kind: ChangeKind,
        entryId: String,
        name: String,
        url: String = "https://example.com/$entryId",
        sectionId: String = "video",
        detail: String = "",
    ): ChangeItem {
        return ChangeItem(
            id = id,
            ts = ts,
            kind = kind,
            entryId = entryId,
            sectionId = sectionId,
            name = name,
            url = url,
            detail = detail,
            entry = null,
        )
    }

    @Test
    fun testCollapseRenamedAndStarredSameDay() {
        val ts = nowSec - 1000
        val renamed = makeChange(1, ts, ChangeKind.RENAMED, "e1", "New Name", detail = "Old Name")
        val starred = makeChange(2, ts + 10, ChangeKind.STARRED, "e1", "New Name")

        val cards = WhatsNewGrouping.collapseChanges(listOf(renamed, starred), zoneId = utcZone)

        assertEquals(1, cards.size)
        val card = cards.first()
        assertEquals(ChangeKind.RENAMED, card.primaryKind)
        assertEquals(setOf(ChangeKind.RENAMED, ChangeKind.STARRED), card.kinds)
        assertEquals("Old Name", card.oldName)
        assertEquals("New Name", card.name)
        assertEquals(ts + 10, card.ts)
    }

    @Test
    fun testCollapseAddedAndRemovedSameUrlTreatedAsEdited() {
        val ts = nowSec - 2000
        val url = "https://example.com/readded"
        val added = makeChange(1, ts, ChangeKind.ADDED, "e2", "Readded Site", url = url)
        val removed = makeChange(2, ts + 20, ChangeKind.REMOVED, "e2", "Readded Site", url = url)

        val cards = WhatsNewGrouping.collapseChanges(listOf(added, removed), zoneId = utcZone)

        assertEquals(1, cards.size)
        val card = cards.first()
        assertEquals(ChangeKind.EDITED, card.primaryKind)
        assertEquals(setOf(ChangeKind.EDITED), card.kinds)
    }

    @Test
    fun testRemovedAndAddedInOtherSectionIsOneMove() {
        val ts = nowSec - 2000
        val url = "https://www.joypixels.com/"
        val removed = makeChange(1, ts, ChangeKind.REMOVED, "image-tools/joypixels", "JoyPixels", url = url, sectionId = "image-tools")
        val added = makeChange(2, ts + 5, ChangeKind.ADDED, "text-tools/joypixels", "JoyPixels", url = url, sectionId = "text-tools")

        val cards = WhatsNewGrouping.collapseChanges(listOf(removed, added), zoneId = utcZone)

        assertEquals(1, cards.size)
        val card = cards.first()
        assertEquals(ChangeKind.MOVED, card.primaryKind)
        assertEquals(setOf(ChangeKind.MOVED), card.kinds)
        assertEquals("image-tools", card.oldSectionId)
        assertEquals("text-tools", card.sectionId)
    }

    @Test
    fun testPrimaryKindPriorityOrder() {
        val priority = WhatsNewGrouping.PRIMARY_KIND_PRIORITY
        assertEquals(
            listOf(
                ChangeKind.REMOVED,
                ChangeKind.ADDED,
                ChangeKind.MOVED,
                ChangeKind.RENAMED,
                ChangeKind.URL_CHANGED,
                ChangeKind.STARRED,
                ChangeKind.UNSTARRED,
                ChangeKind.EDITED,
            ),
            priority,
        )

        // Test that MOVED wins over RENAMED and STARRED
        val ts = nowSec - 500
        val moved = makeChange(1, ts, ChangeKind.MOVED, "e3", "Site 3", detail = "old-section")
        val renamed = makeChange(2, ts + 1, ChangeKind.RENAMED, "e3", "Site 3", detail = "Old Name")
        val starred = makeChange(3, ts + 2, ChangeKind.STARRED, "e3", "Site 3")

        val cards = WhatsNewGrouping.collapseChanges(listOf(moved, renamed, starred), zoneId = utcZone)
        assertEquals(1, cards.size)
        assertEquals(ChangeKind.MOVED, cards.first().primaryKind)
        assertEquals(setOf(ChangeKind.MOVED, ChangeKind.RENAMED, ChangeKind.STARRED), cards.first().kinds)
    }

    @Test
    fun testPerKindCounts() {
        val ts = nowSec - 1000
        // Card 1: RENAMED + STARRED
        val c1 = makeChange(1, ts, ChangeKind.RENAMED, "e1", "Site 1", detail = "Old 1")
        val c2 = makeChange(2, ts, ChangeKind.STARRED, "e1", "Site 1")
        // Card 2: ADDED
        val c3 = makeChange(3, ts, ChangeKind.ADDED, "e2", "Site 2")
        // Card 3: REMOVED
        val c4 = makeChange(4, ts, ChangeKind.REMOVED, "e3", "Site 3")

        val cards = WhatsNewGrouping.collapseChanges(listOf(c1, c2, c3, c4), zoneId = utcZone)
        val counts = WhatsNewGrouping.countsPerKind(cards)

        assertEquals(1, counts[ChangeKind.RENAMED])
        assertEquals(1, counts[ChangeKind.STARRED])
        assertEquals(1, counts[ChangeKind.ADDED])
        assertEquals(1, counts[ChangeKind.REMOVED])
        assertEquals(0, counts[ChangeKind.EDITED])
        assertEquals(0, counts[ChangeKind.URL_CHANGED])
    }

    @Test
    fun testDayGrouping() {
        val todayTs = nowSec - 3600
        val yesterdayTs = nowSec - 90000
        val olderTs = nowSec - 300000

        val itemToday1 = makeChange(1, todayTs, ChangeKind.ADDED, "e1", "Site A")
        val itemToday2 = makeChange(2, todayTs - 60, ChangeKind.RENAMED, "e2", "Site B")
        val itemYesterday = makeChange(3, yesterdayTs, ChangeKind.ADDED, "e3", "Site C")
        val itemOlder = makeChange(4, olderTs, ChangeKind.ADDED, "e4", "Site D")

        val cards = WhatsNewGrouping.collapseChanges(
            listOf(itemToday1, itemToday2, itemYesterday, itemOlder),
            zoneId = utcZone,
        )
        val groups = WhatsNewGrouping.groupByDay(cards, nowSec = nowSec, zoneId = utcZone)

        assertEquals(3, groups.size)
        assertEquals("Today · 2 changes", groups[0].dayHeader)
        assertEquals(2, groups[0].cards.size)

        assertEquals("Yesterday · 1 change", groups[1].dayHeader)
        assertEquals(1, groups[1].cards.size)

        assertTrue(groups[2].dayHeader.endsWith(" · 1 change"))
        assertEquals(1, groups[2].cards.size)
    }

    @Test
    fun testUrlChangeFormatting() {
        // Different hosts
        val diffHost = WhatsNewGrouping.formatUrlChange("https://old.org/page", "https://new.org/page")
        assertEquals("old.org → new.org", diffHost)

        // Same host, different paths
        val sameHost = WhatsNewGrouping.formatUrlChange("https://site.com/oldpath", "https://site.com/newpath")
        assertEquals("/oldpath → /newpath", sameHost)

        // Empty old url
        val emptyOld = WhatsNewGrouping.formatUrlChange("", "https://site.com/path")
        assertEquals("site.com", emptyOld)
    }

    @Test
    fun testExtractSubsectionTarget() {
        val entryWithHeading = Entry(
            id = "e1",
            num = 1,
            sectionId = "video",
            headingId = "movies-cat",
            ord = 1,
            name = "Movie Site",
            url = "https://example.com/movie",
            originalUrl = null,
            description = "desc",
            tags = emptyList(),
            headingPath = "Video › [Movies / TV](https://fmhy.net/video)",
            tier = Tier.NORMAL,
            isInlineMore = false,
            isBase64 = false,
            mirrors = emptyList(),
            extraLinks = emptyList(),
            firstSeen = 0L,
            lastChanged = 0L,
            starredAt = null,
        )
        val card = ChangeCard(
            key = "key1",
            ts = 1000L,
            primaryKind = ChangeKind.ADDED,
            kinds = setOf(ChangeKind.ADDED),
            name = "Movie Site",
            url = "https://example.com/movie",
            sectionId = "video",
            entry = entryWithHeading,
            oldName = null,
            oldUrl = null,
            oldSectionId = null,
            oldLine = null,
        )
        val sectionTitles = mapOf("video" to "Movies, TV & Anime")
        val target = extractSubsectionTarget(card, sectionTitles)
        assertNotNull(target)
        assertEquals("video", target!!.sectionId)
        assertEquals("movies-cat", target.headingId)
        assertEquals("Movies / TV", target.subTitle)
        assertEquals("Movies, TV & Anime", target.sectionTitle)

        // Card without headingId
        val entryWithoutHeading = entryWithHeading.copy(headingId = null)
        val cardWithoutHeading = card.copy(entry = entryWithoutHeading)
        assertNull(extractSubsectionTarget(cardWithoutHeading, sectionTitles))

        // Card with empty headingId
        val entryEmptyHeading = entryWithHeading.copy(headingId = "")
        val cardEmptyHeading = card.copy(entry = entryEmptyHeading)
        assertNull(extractSubsectionTarget(cardEmptyHeading, sectionTitles))

        // Card with null entry
        val cardNullEntry = card.copy(entry = null)
        assertNull(extractSubsectionTarget(cardNullEntry, sectionTitles))
    }

    private fun makeCard(
        key: String,
        sectionId: String,
        headingId: String? = null,
        name: String = "Test Site",
    ): ChangeCard {
        val entry = headingId?.let {
            Entry(
                id = key,
                num = 1,
                sectionId = sectionId,
                headingId = it,
                ord = 1,
                name = name,
                url = "https://example.com/$key",
                originalUrl = null,
                description = "",
                tags = emptyList(),
                headingPath = "$sectionId › $it",
                tier = Tier.NORMAL,
                isInlineMore = false,
                isBase64 = false,
                mirrors = emptyList(),
                extraLinks = emptyList(),
                firstSeen = 0L,
                lastChanged = 0L,
                starredAt = null,
            )
        }
        return ChangeCard(
            key = key,
            ts = nowSec,
            primaryKind = ChangeKind.ADDED,
            kinds = setOf(ChangeKind.ADDED),
            name = name,
            url = "https://example.com/$key",
            sectionId = sectionId,
            entry = entry,
            oldName = null,
            oldUrl = null,
            oldSectionId = null,
            oldLine = null,
        )
    }

    private fun follow(
        sectionId: String,
        headingId: String = "",
        title: String = sectionId,
        excluded: Boolean = false,
    ) = Follow(
        sectionId = sectionId,
        headingId = headingId,
        title = title,
        createdAt = nowSec,
        excluded = excluded,
    )

    @Test
    fun followingScope_sectionFollowedAndSubsectionExcluded_excludedCardHiddenOtherCardsShown() {
        val follows = listOf(
            follow("video", ""),
            follow("video", "movies", excluded = true),
        )
        val cardMovies = makeCard("movies-site", "video", "movies")
        val cardTv = makeCard("tv-site", "video", "tv")
        val cardNoHeading = makeCard("no-heading-site", "video", null)
        val cardOtherSection = makeCard("podcast-site", "audio", "podcasts")

        val filtered = WhatsNewGrouping.filterByScope(
            cards = listOf(cardMovies, cardTv, cardNoHeading, cardOtherSection),
            scope = WhatsNewScope.Following,
            follows = follows,
        )

        val keys = filtered.map { it.key }
        assertFalse("Excluded subsection card should be hidden", keys.contains("movies-site"))
        assertTrue("Other subsection card should be shown", keys.contains("tv-site"))
        assertTrue("Card without heading in followed section should be shown", keys.contains("no-heading-site"))
        assertFalse("Card in unfollowed section should be hidden", keys.contains("podcast-site"))
    }

    @Test
    fun followingScope_exclusionWithoutSectionFollow_returnsNothing() {
        // Section not followed, only an exclusion row exists
        val follows = listOf(
            follow("video", "movies", excluded = true),
        )
        val cardMovies = makeCard("movies-site", "video", "movies")
        val cardTv = makeCard("tv-site", "video", "tv")
        val cardNoHeading = makeCard("no-heading-site", "video", null)

        val filtered = WhatsNewGrouping.filterByScope(
            cards = listOf(cardMovies, cardTv, cardNoHeading),
            scope = WhatsNewScope.Following,
            follows = follows,
        )

        assertTrue("Exclusion row without section follow should match nothing", filtered.isEmpty())
    }

    @Test
    fun followingScope_directSubsectionFollow_cardShown() {
        val follows = listOf(
            follow("video", "movies", excluded = false),
        )
        val cardMovies = makeCard("movies-site", "video", "movies")
        val cardTv = makeCard("tv-site", "video", "tv")
        val cardNoHeading = makeCard("no-heading-site", "video", null)

        val filtered = WhatsNewGrouping.filterByScope(
            cards = listOf(cardMovies, cardTv, cardNoHeading),
            scope = WhatsNewScope.Following,
            follows = follows,
        )

        assertEquals(listOf("movies-site"), filtered.map { it.key })
    }

    @Test
    fun followingScope_excludedSubsectionWithHierarchy_descendantsHidden() {
        val follows = listOf(
            follow("video", ""),
            follow("video", "parent-cat", excluded = true),
        )
        val hierarchy = mapOf(
            "parent-cat" to setOf("parent-cat", "child1", "child2"),
        )
        val cardChild1 = makeCard("child1-site", "video", "child1")
        val cardChild2 = makeCard("child2-site", "video", "child2")
        val cardOther = makeCard("other-site", "video", "other-cat")

        val filtered = WhatsNewGrouping.filterByScope(
            cards = listOf(cardChild1, cardChild2, cardOther),
            scope = WhatsNewScope.Following,
            follows = follows,
            hierarchy = hierarchy,
        )

        assertEquals(listOf("other-site"), filtered.map { it.key })
    }

    @Test
    fun followingScope_directSubsectionFollowWithHierarchy_descendantsShown() {
        val follows = listOf(
            follow("video", "parent-cat", excluded = false),
        )
        val hierarchy = mapOf(
            "parent-cat" to setOf("parent-cat", "child1", "child2"),
        )
        val cardChild1 = makeCard("child1-site", "video", "child1")
        val cardChild2 = makeCard("child2-site", "video", "child2")
        val cardOther = makeCard("other-site", "video", "other-cat")

        val filtered = WhatsNewGrouping.filterByScope(
            cards = listOf(cardChild1, cardChild2, cardOther),
            scope = WhatsNewScope.Following,
            follows = follows,
            hierarchy = hierarchy,
        )

        assertEquals(listOf("child1-site", "child2-site"), filtered.map { it.key })
    }
}
