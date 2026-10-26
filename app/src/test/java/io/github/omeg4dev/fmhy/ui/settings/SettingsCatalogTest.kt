package io.github.omeg4dev.fmhy.ui.settings

import io.github.omeg4dev.fmhy.SettingsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {

    @Test
    fun catalogIdsAreUnique() {
        val ids = SettingsCatalog.all.map { it.id }
        val duplicates = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("Duplicate ids found: $duplicates", duplicates.isEmpty())
        assertEquals("All ids must be unique", ids.size, ids.distinct().size)
    }

    @Test
    fun everyIdStartsWithPageLowercase() {
        SettingsCatalog.all.forEach { entry ->
            val expectedPrefix = "${entry.page.name.lowercase()}."
            assertTrue(
                "Entry '${entry.id}' does not start with expected prefix '$expectedPrefix'",
                entry.id.startsWith(expectedPrefix)
            )
        }
    }

    @Test
    fun titleAndDescriptionAreNonBlank() {
        SettingsCatalog.all.forEach { entry ->
            assertTrue("Title is blank for ${entry.id}", entry.title.isNotBlank())
            assertTrue("Description is blank for ${entry.id}", entry.description.isNotBlank())
        }
    }

    @Test
    fun descriptionDoesNotEndWithPeriodOrContainNewline() {
        SettingsCatalog.all.forEach { entry ->
            assertFalse(
                "Description for ${entry.id} ends with a period: '${entry.description}'",
                entry.description.endsWith(".")
            )
            assertFalse(
                "Description for ${entry.id} contains newline: '${entry.description}'",
                entry.description.contains("\n")
            )
        }
    }

    @Test
    fun everyEntryPageIsRealSettingsPage() {
        val validPages = SettingsPage.entries.toSet()
        SettingsCatalog.all.forEach { entry ->
            assertTrue(
                "Entry ${entry.id} has invalid page: ${entry.page}",
                entry.page in validPages
            )
        }
    }

    @Test
    fun byIdReturnsCorrectEntry() {
        SettingsCatalog.all.forEach { entry ->
            val found = SettingsCatalog.byId(entry.id)
            assertNotNull("byId returned null for ${entry.id}", found)
            assertEquals("byId returned wrong entry for ${entry.id}", entry, found)
        }
    }

    @Test
    fun pageHeadingAndLabelCoverAllPages() {
        SettingsPage.entries.forEach { page ->
            assertTrue("Heading must not be blank for $page", page.heading().isNotBlank())
            assertTrue("Label must not be blank for $page", page.label().isNotBlank())
        }
    }

    @Test
    fun advancedEntriesExistOnlyOnAllowedPages() {
        val pagesWithAdvanced = SettingsCatalog.all.filter { it.advanced }.map { it.page }.toSet()
        assertEquals(
            setOf(
                SettingsPage.AD_BLOCKING,
                SettingsPage.BROWSER,
                SettingsPage.NOTIFICATIONS,
                SettingsPage.VIDEO,
            ),
            pagesWithAdvanced,
        )
    }
}
