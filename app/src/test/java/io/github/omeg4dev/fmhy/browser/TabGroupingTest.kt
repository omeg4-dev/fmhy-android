package io.github.omeg4dev.fmhy.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabGroupingTest {
    private fun t(
        id: Long,
        url: String = "https://example.com/$id",
        title: String = "T$id",
        priv: Boolean = false,
    ) = TabState(id = id, url = url, title = title, isPrivate = priv)

    @Test
    fun siteKeyExtractsTopPrivateDomainAndHandlesFallbacks() {
        assertEquals("github.com", TabsLogic.siteKey("https://www.github.com/a"))
        assertEquals("github.com", TabsLogic.siteKey("https://gist.github.com/x"))
        assertEquals("bbc.co.uk", TabsLogic.siteKey("https://news.bbc.co.uk/"))
        assertEquals("192.168.1.1", TabsLogic.siteKey("http://192.168.1.1/"))
        assertEquals("", TabsLogic.siteKey("about:blank"))
        assertEquals("", TabsLogic.siteKey(""))
        assertEquals("", TabsLogic.siteKey("   "))
        assertEquals("localhost", TabsLogic.siteKey("http://localhost:8080"))
    }

    @Test
    fun groupingCombinesMultipleTabsFromSameSite() {
        val tabs = listOf(
            t(1, "https://www.github.com/a", "Tab 1"),
            t(2, "https://gist.github.com/b", "Tab 2"),
            t(3, "https://github.com/c", "Tab 3"),
        )
        val rows = TabsLogic.rows(tabs, query = "", grouping = true, expanded = emptySet(), currentTabId = null)

        assertEquals(2, rows.size) // Header + Group
        val header = rows[0] as TabsRow.Header
        assertEquals("Tabs", header.label)
        assertEquals(3, header.count)

        val group = rows[1] as TabsRow.Group
        assertEquals("github.com", group.site)
        assertFalse(group.isPrivate)
        assertEquals(3, group.tabs.size)
        assertEquals(listOf(1L, 2L, 3L), group.tabs.map { it.id })
        assertFalse(group.expanded)
        assertEquals("g:n:github.com", group.key)
    }

    @Test
    fun singleTabSiteRemainsNormalItemAndPreservesPosition() {
        val tabs = listOf(
            t(1, "https://www.github.com/a"),
            t(2, "https://example.com/b"),
            t(3, "https://github.com/c"),
        )
        val rows = TabsLogic.rows(tabs, query = "", grouping = true, expanded = emptySet(), currentTabId = null)

        assertEquals(3, rows.size) // Header, Group(github), Item(example.com)
        val header = rows[0] as TabsRow.Header
        assertEquals(3, header.count)

        val group = rows[1] as TabsRow.Group
        assertEquals("github.com", group.site)
        assertEquals(listOf(1L, 3L), group.tabs.map { it.id })

        val item = rows[2] as TabsRow.Item
        assertEquals(2L, item.tab.id)
        assertFalse(item.indented)
    }

    @Test
    fun normalAndPrivateTabsOfSameSiteNeverMix() {
        val tabs = listOf(
            t(1, "https://github.com/1", priv = false),
            t(2, "https://github.com/2", priv = false),
            t(3, "https://github.com/3", priv = true),
            t(4, "https://github.com/4", priv = true),
        )
        val rows = TabsLogic.rows(tabs, query = "", grouping = true, expanded = emptySet(), currentTabId = null)

        assertEquals(4, rows.size)
        val normalHeader = rows[0] as TabsRow.Header
        assertEquals("Tabs", normalHeader.label)
        assertEquals(2, normalHeader.count)

        val normalGroup = rows[1] as TabsRow.Group
        assertEquals("github.com", normalGroup.site)
        assertFalse(normalGroup.isPrivate)
        assertEquals(listOf(1L, 2L), normalGroup.tabs.map { it.id })
        assertEquals("g:n:github.com", normalGroup.key)

        val privHeader = rows[2] as TabsRow.Header
        assertEquals("Private tabs", privHeader.label)
        assertEquals(2, privHeader.count)

        val privGroup = rows[3] as TabsRow.Group
        assertEquals("github.com", privGroup.site)
        assertTrue(privGroup.isPrivate)
        assertEquals(listOf(3L, 4L), privGroup.tabs.map { it.id })
        assertEquals("g:p:github.com", privGroup.key)
    }

    @Test
    fun blankAndAboutBlankTabsAreNeverGrouped() {
        val tabs = listOf(
            t(1, "about:blank"),
            t(2, "about:blank"),
            t(3, ""),
        )
        val rows = TabsLogic.rows(tabs, query = "", grouping = true, expanded = emptySet(), currentTabId = null)

        assertEquals(4, rows.size) // Header + 3 Items
        assertTrue(rows[0] is TabsRow.Header)
        val items = rows.drop(1).filterIsInstance<TabsRow.Item>()
        assertEquals(3, items.size)
        assertEquals(listOf(1L, 2L, 3L), items.map { it.tab.id })
        items.forEach { assertFalse(it.indented) }
    }

    @Test
    fun foldingDefaultCollapsedAndKeyInExpandedShowsIndentedItems() {
        val tabs = listOf(
            t(1, "https://github.com/1"),
            t(2, "https://github.com/2"),
        )
        // Collapsed
        val collapsedRows = TabsLogic.rows(tabs, query = "", grouping = true, expanded = emptySet(), currentTabId = null)
        assertEquals(2, collapsedRows.size) // Header + Group
        val collapsedGroup = collapsedRows[1] as TabsRow.Group
        assertFalse(collapsedGroup.expanded)

        // Expanded
        val expandedKey = TabsLogic.groupKey("github.com", false)
        assertEquals("n:github.com", expandedKey)
        val expandedRows = TabsLogic.rows(tabs, query = "", grouping = true, expanded = setOf(expandedKey), currentTabId = null)

        assertEquals(4, expandedRows.size) // Header + Group + 2 Items
        val expandedGroup = expandedRows[1] as TabsRow.Group
        assertTrue(expandedGroup.expanded)

        val item1 = expandedRows[2] as TabsRow.Item
        assertEquals(1L, item1.tab.id)
        assertTrue(item1.indented)

        val item2 = expandedRows[3] as TabsRow.Item
        assertEquals(2L, item2.tab.id)
        assertTrue(item2.indented)
    }

    @Test
    fun searchForcesGroupExpandedAndShowsOnlyMatchingTabs() {
        val tabs = listOf(
            t(1, "https://github.com/apple", title = "Apple repository"),
            t(2, "https://github.com/banana", title = "Banana repository"),
            t(3, "https://example.com/other", title = "Other page"),
        )
        val rows = TabsLogic.rows(tabs, query = "apple", grouping = true, expanded = emptySet(), currentTabId = null)

        // Only 1 tab matches in normal section
        assertEquals(3, rows.size) // Header("Tabs", 1) + Group + Item(1)
        val header = rows[0] as TabsRow.Header
        assertEquals(1, header.count)

        val group = rows[1] as TabsRow.Group
        assertEquals("github.com", group.site)
        assertTrue(group.expanded) // Forced expanded
        assertEquals(2, group.tabs.size) // Group.tabs still has ALL tabs

        val item = rows[2] as TabsRow.Item
        assertEquals(1L, item.tab.id)
        assertTrue(item.indented)
    }

    @Test
    fun twoToOneTabTurnsBackToNormalItem() {
        val twoTabs = listOf(
            t(1, "https://github.com/1"),
            t(2, "https://github.com/2"),
        )
        val rowsBefore = TabsLogic.rows(twoTabs, query = "", grouping = true, expanded = emptySet(), currentTabId = null)
        assertTrue(rowsBefore.any { it is TabsRow.Group })

        // After removing one tab
        val oneTab = listOf(t(1, "https://github.com/1"))
        val rowsAfter = TabsLogic.rows(oneTab, query = "", grouping = true, expanded = emptySet(), currentTabId = null)
        assertFalse(rowsAfter.any { it is TabsRow.Group })

        val item = rowsAfter.filterIsInstance<TabsRow.Item>().first()
        assertEquals(1L, item.tab.id)
        assertFalse(item.indented)
    }

    @Test
    fun groupingFalseGivesOldHeaderAndItemRowsWithoutGroups() {
        val tabs = listOf(
            t(1, "https://github.com/1"),
            t(2, "https://github.com/2"),
            t(3, "https://github.com/3", priv = true),
        )
        val rows = TabsLogic.rows(tabs, query = "", grouping = false, expanded = emptySet(), currentTabId = null)

        assertFalse(rows.any { it is TabsRow.Group })
        assertEquals(5, rows.size) // Header("Tabs", 2), Item(1), Item(2), Header("Private tabs", 1), Item(3)

        assertEquals(TabsRow.Header("Tabs", 2), rows[0])
        assertEquals(TabsRow.Item(tabs[0], indented = false), rows[1])
        assertEquals(TabsRow.Item(tabs[1], indented = false), rows[2])
        assertEquals(TabsRow.Header("Private tabs", 1), rows[3])
        assertEquals(TabsRow.Item(tabs[2], indented = false), rows[4])
    }

    @Test
    fun containsCurrentAndScrollKeyBehavior() {
        val tabs = listOf(
            t(1, "https://github.com/1"),
            t(2, "https://github.com/2"),
            t(3, "https://example.com/3"),
        )

        // Collapsed: currentTabId = 2 inside group
        val collapsed = TabsLogic.rows(tabs, query = "", grouping = true, expanded = emptySet(), currentTabId = 2)
        val group = collapsed.filterIsInstance<TabsRow.Group>().first()
        assertTrue(group.containsCurrent)
        // Folded group -> returns group key
        assertEquals("g:n:github.com", TabsLogic.scrollKey(collapsed, 2))

        // Expanded: currentTabId = 2 inside group
        val expanded = TabsLogic.rows(tabs, query = "", grouping = true, expanded = setOf("n:github.com"), currentTabId = 2)
        // Unfolded -> returns item key
        assertEquals("t:2", TabsLogic.scrollKey(expanded, 2))

        // Tab not in group -> returns item key
        assertEquals("t:3", TabsLogic.scrollKey(collapsed, 3))

        // Tab not found or null
        assertNull(TabsLogic.scrollKey(collapsed, 999))
        assertNull(TabsLogic.scrollKey(collapsed, null))
    }
}
