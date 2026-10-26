package io.github.omeg4dev.fmhy.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsFocusTest {

    private val sectionSettingIds = listOf(
        listOf("theme", "pure_black", "accent"),
        listOf("home_style", "random_pick", "section_order"),
        listOf("about_version", "about_repo"),
    )

    @Test
    fun focusFoundInSecondSectionReturnsOne() {
        assertEquals(1, focusSectionIndex(sectionSettingIds, "random_pick"))
    }

    @Test
    fun nullFocusReturnsNull() {
        assertNull(focusSectionIndex(sectionSettingIds, null))
    }

    @Test
    fun unknownIdReturnsNull() {
        assertNull(focusSectionIndex(sectionSettingIds, "non_existent_setting"))
    }
}
