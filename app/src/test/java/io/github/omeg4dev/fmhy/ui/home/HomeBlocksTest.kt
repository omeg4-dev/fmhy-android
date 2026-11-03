package io.github.omeg4dev.fmhy.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBlocksTest {

    @Test
    fun defaultOrderAndEnabledSet() {
        val defaultLayout = HomeLayout.default()
        val expectedBlocks = listOf(
            HomeBlock.SEARCH,
            HomeBlock.NEW_SINCE,
            HomeBlock.RANDOM_PICK,
            HomeBlock.PINNED,
            HomeBlock.NEW_IN_PINNED,
            HomeBlock.FAVOURITES,
            HomeBlock.SUGGESTIONS,
            HomeBlock.SECTIONS,
        )
        assertEquals(expectedBlocks, defaultLayout.entries.map { it.block })

        // Check enabled states
        assertTrue(defaultLayout.isEnabled(HomeBlock.SEARCH))
        assertTrue(defaultLayout.isEnabled(HomeBlock.NEW_SINCE))
        assertTrue(defaultLayout.isEnabled(HomeBlock.RANDOM_PICK))
        assertFalse(defaultLayout.isEnabled(HomeBlock.PINNED))
        assertFalse(defaultLayout.isEnabled(HomeBlock.NEW_IN_PINNED))
        assertFalse(defaultLayout.isEnabled(HomeBlock.FAVOURITES))
        assertFalse(defaultLayout.isEnabled(HomeBlock.SUGGESTIONS))
        assertTrue(defaultLayout.isEnabled(HomeBlock.SECTIONS))

        assertEquals(4, defaultLayout.enabledCount)
        assertEquals(
            listOf(HomeBlock.SEARCH, HomeBlock.NEW_SINCE, HomeBlock.RANDOM_PICK, HomeBlock.SECTIONS),
            defaultLayout.enabledBlocks(),
        )
    }

    @Test
    fun enabledBlocksOrderAfterMove() {
        val layout = HomeLayout.default()
        // Move SECTIONS (index 7) to index 0
        val moved = layout.move(7, 0)
        assertEquals(HomeBlock.SECTIONS, moved.entries.first().block)
        assertEquals(
            listOf(HomeBlock.SECTIONS, HomeBlock.SEARCH, HomeBlock.NEW_SINCE, HomeBlock.RANDOM_PICK),
            moved.enabledBlocks(),
        )

        // Move NEW_SINCE (now index 2) to last (index 7)
        val movedAgain = moved.move(2, 7)
        assertEquals(HomeBlock.NEW_SINCE, movedAgain.entries.last().block)
        assertEquals(
            listOf(HomeBlock.SECTIONS, HomeBlock.SEARCH, HomeBlock.RANDOM_PICK, HomeBlock.NEW_SINCE),
            movedAgain.enabledBlocks(),
        )
    }

    @Test
    fun moveEdgeCases() {
        val layout = HomeLayout.default()

        // from == to -> unchanged
        assertEquals(layout, layout.move(3, 3))
        assertEquals(layout, layout.move(0, 0))
        assertEquals(layout, layout.move(7, 7))

        // negative indices -> unchanged
        assertEquals(layout, layout.move(-1, 2))
        assertEquals(layout, layout.move(2, -1))
        assertEquals(layout, layout.move(-5, -5))

        // too large indices -> unchanged
        assertEquals(layout, layout.move(8, 2))
        assertEquals(layout, layout.move(2, 8))
        assertEquals(layout, layout.move(100, 200))

        // first -> last (0 -> 7)
        val firstToLast = layout.move(0, 7)
        assertEquals(HomeBlock.NEW_SINCE, firstToLast.entries[0].block)
        assertEquals(HomeBlock.SEARCH, firstToLast.entries[7].block)
        assertEquals(8, firstToLast.entries.size)

        // last -> first (7 -> 0)
        val lastToFirst = layout.move(7, 0)
        assertEquals(HomeBlock.SECTIONS, lastToFirst.entries[0].block)
        assertEquals(HomeBlock.SEARCH, lastToFirst.entries[1].block)
        assertEquals(HomeBlock.SUGGESTIONS, lastToFirst.entries[7].block)
        assertEquals(8, lastToFirst.entries.size)
    }

    @Test
    fun withEnabledTogglesOne() {
        val layout = HomeLayout.default()
        assertFalse(layout.isEnabled(HomeBlock.PINNED))

        val toggledOn = layout.withEnabled(HomeBlock.PINNED, true)
        assertTrue(toggledOn.isEnabled(HomeBlock.PINNED))
        assertEquals(5, toggledOn.enabledCount)
        // Ensure other entries and order remained identical
        assertEquals(layout.entries.map { it.block }, toggledOn.entries.map { it.block })
        assertTrue(toggledOn.isEnabled(HomeBlock.SEARCH))
        assertTrue(toggledOn.isEnabled(HomeBlock.SECTIONS))

        val toggledOff = toggledOn.withEnabled(HomeBlock.SEARCH, false)
        assertFalse(toggledOff.isEnabled(HomeBlock.SEARCH))
        assertTrue(toggledOff.isEnabled(HomeBlock.PINNED))
        assertEquals(4, toggledOff.enabledCount)
    }

    @Test
    fun resetReturnsDefault() {
        val modified = HomeLayout.default()
            .withEnabled(HomeBlock.PINNED, true)
            .move(0, 5)
            .withEnabled(HomeBlock.SECTIONS, false)

        assertNotEquals(HomeLayout.default(), modified)
        val resetLayout = modified.reset()
        assertEquals(HomeLayout.default(), resetLayout)
    }

    @Test
    fun encodeDecodeRoundTripForDefault() {
        val defaultLayout = HomeLayout.default()
        val encoded = defaultLayout.encode()
        val expectedEncoded = "SEARCH:1,NEW_SINCE:1,RANDOM_PICK:1,PINNED:0,NEW_IN_PINNED:0,FAVOURITES:0,SUGGESTIONS:0,SECTIONS:1"
        assertEquals(expectedEncoded, encoded)

        val decoded = HomeLayout.decode(encoded)
        assertEquals(defaultLayout, decoded)
    }

    @Test
    fun encodeDecodeRoundTripForShuffledAndToggledLayout() {
        val custom = HomeLayout.default()
            .move(7, 0) // SECTIONS to front
            .move(2, 6)
            .withEnabled(HomeBlock.PINNED, true)
            .withEnabled(HomeBlock.NEW_IN_PINNED, true)
            .withEnabled(HomeBlock.RANDOM_PICK, false)

        val encoded = custom.encode()
        val decoded = HomeLayout.decode(encoded)
        assertEquals(custom, decoded)
        assertEquals(custom.entries, decoded.entries)
    }

    @Test
    fun decodeNullBlankGarbage() {
        assertEquals(HomeLayout.default(), HomeLayout.decode(null))
        assertEquals(HomeLayout.default(), HomeLayout.decode(""))
        assertEquals(HomeLayout.default(), HomeLayout.decode("   "))
        assertEquals(HomeLayout.default(), HomeLayout.decode("garbage"))
        assertEquals(HomeLayout.default(), HomeLayout.decode("not:valid:at:all,another:thing"))
        assertEquals(HomeLayout.default(), HomeLayout.decode(":::,"))
    }

    @Test
    fun decodeDuplicateKeepsFirst() {
        // SEARCH:1,SEARCH:0 -> keeps SEARCH:1
        val decoded = HomeLayout.decode("SEARCH:1,SEARCH:0")
        assertTrue(decoded.isEnabled(HomeBlock.SEARCH))
        assertEquals(HomeBlock.SEARCH, decoded.entries[0].block)
        assertEquals(8, decoded.entries.size)

        // Inverse test: SEARCH:0,SEARCH:1 -> keeps SEARCH:0
        val decodedOff = HomeLayout.decode("SEARCH:0,SEARCH:1")
        assertFalse(decodedOff.isEnabled(HomeBlock.SEARCH))
        assertEquals(HomeBlock.SEARCH, decodedOff.entries[0].block)
    }

    @Test
    fun decodeUnknownDroppedRestFilled() {
        // "FOO:1,SEARCH:0" -> FOO:1 dropped, SEARCH:0 kept, rest filled
        val decoded = HomeLayout.decode("FOO:1,SEARCH:0")
        assertEquals(8, decoded.entries.size)
        assertFalse(decoded.isEnabled(HomeBlock.SEARCH))
        assertEquals(HomeBlock.SEARCH, decoded.entries[0].block)
        // Default blocks filled in relative positions with default enabled states
        assertTrue(decoded.isEnabled(HomeBlock.NEW_SINCE))
        assertTrue(decoded.isEnabled(HomeBlock.RANDOM_PICK))
        assertFalse(decoded.isEnabled(HomeBlock.PINNED))
        assertTrue(decoded.isEnabled(HomeBlock.SECTIONS))
    }

    @Test
    fun decodeMissingBlocksInsertedAtDefaultRelativePosition() {
        // Only PINNED:1 is present
        val decoded = HomeLayout.decode("PINNED:1")
        assertEquals(8, decoded.entries.size)
        assertTrue(decoded.isEnabled(HomeBlock.PINNED))

        // Preceding blocks SEARCH, NEW_SINCE, RANDOM_PICK come before PINNED
        // Succeeding blocks NEW_IN_PINNED, FAVOURITES, SUGGESTIONS, SECTIONS come after
        val blocks = decoded.entries.map { it.block }
        assertEquals(
            listOf(
                HomeBlock.SEARCH,
                HomeBlock.NEW_SINCE,
                HomeBlock.RANDOM_PICK,
                HomeBlock.PINNED,
                HomeBlock.NEW_IN_PINNED,
                HomeBlock.FAVOURITES,
                HomeBlock.SUGGESTIONS,
                HomeBlock.SECTIONS,
            ),
            blocks,
        )

        // Partial string with custom order: SECTIONS:1,SEARCH:0
        val partialCustom = HomeLayout.decode("SECTIONS:1,SEARCH:0")
        assertEquals(8, partialCustom.entries.size)
        // First block is SECTIONS, second is SEARCH
        assertEquals(HomeBlock.SECTIONS, partialCustom.entries[0].block)
        assertEquals(HomeBlock.SEARCH, partialCustom.entries[1].block)
        // The remaining missing blocks (NEW_SINCE..SUGGESTIONS) come after SEARCH (preceding them)
        val expectedCustomOrder = listOf(
            HomeBlock.SECTIONS,
            HomeBlock.SEARCH,
            HomeBlock.NEW_SINCE,
            HomeBlock.RANDOM_PICK,
            HomeBlock.PINNED,
            HomeBlock.NEW_IN_PINNED,
            HomeBlock.FAVOURITES,
            HomeBlock.SUGGESTIONS,
        )
        assertEquals(expectedCustomOrder, partialCustom.entries.map { it.block })
    }

    @Test
    fun fromLegacy() {
        val legacy1 = HomeLayout.fromLegacy(personalHome = true, showRandomPick = false)
        assertTrue(legacy1.isEnabled(HomeBlock.PINNED))
        assertFalse(legacy1.isEnabled(HomeBlock.RANDOM_PICK))
        assertTrue(legacy1.isEnabled(HomeBlock.SEARCH))
        assertTrue(legacy1.isEnabled(HomeBlock.SECTIONS))
        assertEquals(HomeLayout.default().entries.map { it.block }, legacy1.entries.map { it.block })

        val legacy2 = HomeLayout.fromLegacy(personalHome = false, showRandomPick = true)
        assertFalse(legacy2.isEnabled(HomeBlock.PINNED))
        assertTrue(legacy2.isEnabled(HomeBlock.RANDOM_PICK))
        assertEquals(HomeLayout.default().entries.map { it.block }, legacy2.entries.map { it.block })
    }
}
