package io.github.omeg4dev.fmhy.ui.settings

import io.github.omeg4dev.fmhy.BuildConfig
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
    fun idsHaveStableFormat() {
        val pattern = Regex("^[a-z_]+\\.[a-z_]+$")
        SettingsCatalog.all.forEach { entry ->
            assertTrue(
                "Entry '${entry.id}' does not match expected id format",
                pattern.matches(entry.id)
            )
        }
    }

    @Test
    fun everyDescriptionAtMost70Chars() {
        SettingsCatalog.all.forEach { entry ->
            assertTrue(
                "Description exceeds 70 chars for ${entry.id} (${entry.description.length}): '${entry.description}'",
                entry.description.length <= 70
            )
        }
    }

    @Test
    fun everyEntryPageIsInHubTree() {
        val treePages = SettingsTree.groups
            .flatMap { it.items }
            .filterIsInstance<HubItem.Page>()
            .map { it.page }
            .toSet()
        SettingsCatalog.all.forEach { entry ->
            assertTrue(
                "Entry ${entry.id} has page ${entry.page} not present in SettingsTree.groups",
                entry.page in treePages
            )
        }
    }

    @Test
    fun everyPageAppearsOnceInTree() {
        val treePages = SettingsTree.groups
            .flatMap { it.items }
            .filterIsInstance<HubItem.Page>()
            .map { it.page }
        SettingsPage.entries.forEach { page ->
            assertEquals(
                "Page $page must appear exactly once in SettingsTree.groups",
                1,
                treePages.count { it == page }
            )
        }
    }

    @Test
    fun headingMatchesTreeGroup() {
        SettingsTree.groups.forEach { group ->
            group.items.filterIsInstance<HubItem.Page>().forEach { item ->
                assertEquals(
                    "Heading for ${item.page} must match group title '${group.title}'",
                    group.title,
                    item.page.heading()
                )
            }
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
    fun nsfwLinkOnlyInGithubBuild() {
        assertEquals(
            BuildConfig.FLAVOR == "github",
            SettingsCatalog.all.any { it.id == SettingsCatalog.homeNsfwLink.id },
        )
    }

    @Test
    fun fmhyXAccountOnlyInGithubBuild() {
        val fmhyXEntries = SettingsCatalog.all.filter { it.page == SettingsPage.FMHYX_ACCOUNT }
        assertEquals(BuildConfig.FLAVOR == "github", fmhyXEntries.isNotEmpty())
        fmhyXEntries.forEach { entry ->
            assertTrue(
                "Description exceeds 70 chars for ${entry.id}: ${entry.description.length}",
                entry.description.length <= 70,
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
