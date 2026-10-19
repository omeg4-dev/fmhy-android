package io.github.omeg4dev.fmhy.nav

import androidx.navigation3.runtime.NavKey
import io.github.omeg4dev.fmhy.HomeNavKey
import io.github.omeg4dev.fmhy.SavedNavKey
import io.github.omeg4dev.fmhy.SearchNavKey
import io.github.omeg4dev.fmhy.SectionNavKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppNavStateTest {

    private fun createAppNavState(
        browseStack: MutableList<NavKey> = mutableListOf(HomeNavKey),
        searchStack: MutableList<NavKey> = mutableListOf(SearchNavKey()),
        savedStack: MutableList<NavKey> = mutableListOf(SavedNavKey),
        initial: Tab = Tab.BROWSE,
    ): AppNavState {
        return AppNavState(
            stacks = mapOf(
                Tab.BROWSE to browseStack,
                Tab.SEARCH to searchStack,
                Tab.SAVED to savedStack,
            ),
            initial = initial,
        )
    }

    @Test
    fun testSelectSwitchesWithoutTouchingStacks() {
        val browseStack = mutableListOf<NavKey>(HomeNavKey, SectionNavKey("sec1"))
        val searchStack = mutableListOf<NavKey>(SearchNavKey())
        val savedStack = mutableListOf<NavKey>(SavedNavKey)
        val state = createAppNavState(browseStack, searchStack, savedStack, initial = Tab.BROWSE)

        state.select(Tab.SEARCH)

        assertEquals(Tab.SEARCH, state.current)
        assertEquals(listOf(HomeNavKey, SectionNavKey("sec1")), browseStack)
        assertEquals(listOf(SearchNavKey()), searchStack)
    }

    @Test
    fun testSelectCurrentPopsToRoot() {
        val browseStack = mutableListOf<NavKey>(HomeNavKey, SectionNavKey("sec1"), SectionNavKey("sec2"))
        val state = createAppNavState(browseStack = browseStack, initial = Tab.BROWSE)

        state.select(Tab.BROWSE)

        assertEquals(Tab.BROWSE, state.current)
        assertEquals(listOf(HomeNavKey), browseStack)
        assertEquals(listOf(HomeNavKey), state.currentStack)
    }

    @Test
    fun testSelectCurrentAtRootEmitsReselect() = runTest {
        val browseStack = mutableListOf<NavKey>(HomeNavKey)
        val state = createAppNavState(browseStack = browseStack, initial = Tab.BROWSE)

        var reselectTab: Tab? = null
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            state.reselect.collect { reselectTab = it }
        }

        state.select(Tab.BROWSE)

        assertEquals(Tab.BROWSE, reselectTab)
        assertEquals(listOf(HomeNavKey), browseStack)
        job.cancel()
    }

    @Test
    fun testPushNoOpForDuplicateTop() {
        val browseStack = mutableListOf<NavKey>(HomeNavKey)
        val state = createAppNavState(browseStack = browseStack, initial = Tab.BROWSE)

        state.push(SectionNavKey("sec1"))
        assertEquals(listOf(HomeNavKey, SectionNavKey("sec1")), browseStack)

        // Pushing duplicate top
        state.push(SectionNavKey("sec1"))
        assertEquals(listOf(HomeNavKey, SectionNavKey("sec1")), browseStack)

        // Pushing distinct key
        state.push(SectionNavKey("sec2"))
        assertEquals(listOf(HomeNavKey, SectionNavKey("sec1"), SectionNavKey("sec2")), browseStack)
    }

    @Test
    fun testBackPops() {
        val browseStack = mutableListOf<NavKey>(HomeNavKey, SectionNavKey("sec1"))
        val state = createAppNavState(browseStack = browseStack, initial = Tab.BROWSE)

        val handled = state.back()

        assertTrue(handled)
        assertEquals(listOf(HomeNavKey), browseStack)
    }

    @Test
    fun testBackAtSearchRootSwitchesToBrowse() {
        val searchStack = mutableListOf<NavKey>(SearchNavKey())
        val state = createAppNavState(searchStack = searchStack, initial = Tab.SEARCH)

        val handled = state.back()

        assertTrue(handled)
        assertEquals(Tab.BROWSE, state.current)
        assertEquals(listOf(SearchNavKey()), searchStack)
    }

    @Test
    fun testBackAtBrowseRootReturnsFalse() {
        val browseStack = mutableListOf<NavKey>(HomeNavKey)
        val state = createAppNavState(browseStack = browseStack, initial = Tab.BROWSE)

        val handled = state.back()

        assertFalse(handled)
        assertEquals(Tab.BROWSE, state.current)
        assertEquals(listOf(HomeNavKey), browseStack)
    }
}
