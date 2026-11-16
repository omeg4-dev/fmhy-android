package io.github.omeg4dev.fmhy.ui.whatsnew

import io.github.omeg4dev.fmhy.data.model.ChangeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewFiltersTest {

    @Test
    fun testDotRule() {
        // defaults -> false
        assertFalse(
            filtersAreCustom(
                period = WhatsNewPeriod.WEEK,
                types = WhatsNewGrouping.DEFAULT_TYPES,
                scope = WhatsNewScope.All,
            )
        )

        // period custom -> true
        assertTrue(
            filtersAreCustom(
                period = WhatsNewPeriod.DAY,
                types = WhatsNewGrouping.DEFAULT_TYPES,
                scope = WhatsNewScope.All,
            )
        )
        assertTrue(
            filtersAreCustom(
                period = WhatsNewPeriod.MONTH,
                types = WhatsNewGrouping.DEFAULT_TYPES,
                scope = WhatsNewScope.All,
            )
        )
        assertTrue(
            filtersAreCustom(
                period = WhatsNewPeriod.SINCE_LAST_VISIT,
                types = WhatsNewGrouping.DEFAULT_TYPES,
                scope = WhatsNewScope.All,
            )
        )

        // types empty (All types) -> true
        assertTrue(
            filtersAreCustom(
                period = WhatsNewPeriod.WEEK,
                types = emptySet(),
                scope = WhatsNewScope.All,
            )
        )

        // types custom -> true
        assertTrue(
            filtersAreCustom(
                period = WhatsNewPeriod.WEEK,
                types = setOf(ChangeKind.ADDED),
                scope = WhatsNewScope.All,
            )
        )

        // scope Following / Saved / Sections -> true
        assertTrue(
            filtersAreCustom(
                period = WhatsNewPeriod.WEEK,
                types = WhatsNewGrouping.DEFAULT_TYPES,
                scope = WhatsNewScope.Following,
            )
        )
        assertTrue(
            filtersAreCustom(
                period = WhatsNewPeriod.WEEK,
                types = WhatsNewGrouping.DEFAULT_TYPES,
                scope = WhatsNewScope.Saved,
            )
        )
        assertTrue(
            filtersAreCustom(
                period = WhatsNewPeriod.WEEK,
                types = WhatsNewGrouping.DEFAULT_TYPES,
                scope = WhatsNewScope.Sections(setOf("sec1")),
            )
        )
    }

    @Test
    fun testCodecRoundTrips() {
        // every period
        for (p in WhatsNewPeriod.entries) {
            val encoded = WhatsNewFilterCodec.encodePeriod(p)
            val decoded = WhatsNewFilterCodec.decodePeriod(encoded)
            assertEquals(p, decoded)
        }

        // types round trips
        val defaultTypes = WhatsNewGrouping.DEFAULT_TYPES
        assertEquals(defaultTypes, WhatsNewFilterCodec.decodeTypes(WhatsNewFilterCodec.encodeTypes(defaultTypes)))

        val emptyTypes = emptySet<ChangeKind>()
        assertEquals(emptyTypes, WhatsNewFilterCodec.decodeTypes(WhatsNewFilterCodec.encodeTypes(emptyTypes)))

        val addedMoved = setOf(ChangeKind.ADDED, ChangeKind.MOVED)
        assertEquals(addedMoved, WhatsNewFilterCodec.decodeTypes(WhatsNewFilterCodec.encodeTypes(addedMoved)))

        // scope round trips
        assertEquals(WhatsNewScope.All, WhatsNewFilterCodec.decodeScope(WhatsNewFilterCodec.encodeScope(WhatsNewScope.All)))
        assertEquals(WhatsNewScope.Following, WhatsNewFilterCodec.decodeScope(WhatsNewFilterCodec.encodeScope(WhatsNewScope.Following)))
        assertEquals(WhatsNewScope.Saved, WhatsNewFilterCodec.decodeScope(WhatsNewFilterCodec.encodeScope(WhatsNewScope.Saved)))
        val sectionsScope = WhatsNewScope.Sections(setOf("a", "b"))
        assertEquals(sectionsScope, WhatsNewFilterCodec.decodeScope(WhatsNewFilterCodec.encodeScope(sectionsScope)))

        // garbage strings -> defaults
        assertEquals(WhatsNewPeriod.WEEK, WhatsNewFilterCodec.decodePeriod("garbage_period"))
        assertEquals(WhatsNewGrouping.DEFAULT_TYPES, WhatsNewFilterCodec.decodeTypes("garbage_kind"))
        assertEquals(WhatsNewScope.All, WhatsNewFilterCodec.decodeScope("garbage_scope"))
        assertEquals(WhatsNewScope.All, WhatsNewFilterCodec.decodeScope("sections:"))
        assertEquals(WhatsNewScope.All, WhatsNewFilterCodec.decodeScope("sections:   "))

        // types string "zzz" -> DEFAULT_TYPES
        assertEquals(WhatsNewGrouping.DEFAULT_TYPES, WhatsNewFilterCodec.decodeTypes("zzz"))

        // types "" -> empty set
        assertEquals(emptySet<ChangeKind>(), WhatsNewFilterCodec.decodeTypes(""))

        // null -> DEFAULT_TYPES
        assertEquals(WhatsNewPeriod.WEEK, WhatsNewFilterCodec.decodePeriod(null))
        assertEquals(WhatsNewGrouping.DEFAULT_TYPES, WhatsNewFilterCodec.decodeTypes(null))
        assertEquals(WhatsNewScope.All, WhatsNewFilterCodec.decodeScope(null))
    }

    @Test
    fun testResolveInitialFilters() {
        val storedCustom = WhatsNewFilterPrefs(
            remember = false,
            period = WhatsNewPeriod.MONTH,
            types = setOf(ChangeKind.ADDED),
            scope = WhatsNewScope.Following,
        )

        // remember=false + no nav args -> defaults even if stored has custom values
        val resolvedDefault = resolveInitialFilters(
            stored = storedCustom,
            initialFilter = "all",
            initialType = null,
            initialPeriod = null,
        )
        assertEquals(
            WhatsNewFilterPrefs(
                remember = false,
                period = WhatsNewPeriod.WEEK,
                types = WhatsNewGrouping.DEFAULT_TYPES,
                scope = WhatsNewScope.All,
            ),
            resolvedDefault,
        )
        assertEquals(storedCustom.remember, resolvedDefault.remember)

        // remember=true + no nav args -> stored values
        val storedRemembered = storedCustom.copy(remember = true)
        val resolvedRemembered = resolveInitialFilters(
            stored = storedRemembered,
            initialFilter = "all",
            initialType = null,
            initialPeriod = null,
        )
        assertEquals(storedRemembered, resolvedRemembered)
        assertEquals(storedRemembered.remember, resolvedRemembered.remember)

        // remember=true + initialPeriod = "since_last_visit" -> SINCE_LAST_VISIT with DEFAULT_TYPES and All (nav wins)
        val resolvedNavPeriod = resolveInitialFilters(
            stored = storedRemembered,
            initialFilter = "all",
            initialType = null,
            initialPeriod = "since_last_visit",
        )
        assertEquals(
            WhatsNewFilterPrefs(
                remember = true,
                period = WhatsNewPeriod.SINCE_LAST_VISIT,
                types = WhatsNewGrouping.DEFAULT_TYPES,
                scope = WhatsNewScope.All,
            ),
            resolvedNavPeriod,
        )
        assertEquals(storedRemembered.remember, resolvedNavPeriod.remember)

        // remember=true + initialFilter = "following" -> scope Following, stored types ignored
        val resolvedNavScope = resolveInitialFilters(
            stored = storedRemembered,
            initialFilter = "following",
            initialType = null,
            initialPeriod = null,
        )
        assertEquals(
            WhatsNewFilterPrefs(
                remember = true,
                period = WhatsNewPeriod.WEEK,
                types = WhatsNewGrouping.DEFAULT_TYPES,
                scope = WhatsNewScope.Following,
            ),
            resolvedNavScope,
        )
        assertEquals(storedRemembered.remember, resolvedNavScope.remember)

        // initialType = "ALL" -> empty types
        val resolvedTypeAll = resolveInitialFilters(
            stored = storedRemembered,
            initialFilter = "all",
            initialType = "ALL",
            initialPeriod = null,
        )
        assertEquals(emptySet<ChangeKind>(), resolvedTypeAll.types)
        assertEquals(storedRemembered.remember, resolvedTypeAll.remember)

        // initialType = "added" -> setOf(ADDED)
        val resolvedTypeAdded = resolveInitialFilters(
            stored = storedRemembered,
            initialFilter = "all",
            initialType = "added",
            initialPeriod = null,
        )
        assertEquals(setOf(ChangeKind.ADDED), resolvedTypeAdded.types)
        assertEquals(storedRemembered.remember, resolvedTypeAdded.remember)

        // initialType = "bogus" -> DEFAULT_TYPES
        val resolvedTypeBogus = resolveInitialFilters(
            stored = storedRemembered,
            initialFilter = "all",
            initialType = "bogus",
            initialPeriod = null,
        )
        assertEquals(WhatsNewGrouping.DEFAULT_TYPES, resolvedTypeBogus.types)
        assertEquals(storedRemembered.remember, resolvedTypeBogus.remember)
    }
}
