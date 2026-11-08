package io.github.omeg4dev.fmhy.ui.section

import org.junit.Assert.assertEquals
import org.junit.Test

class SectionHideOnHomeMenuTest {

    @Test
    fun labelShowsHideOnHomeWhenNotHidden() {
        assertEquals("Hide on Home", sectionHideOnHomeLabel(isSectionHidden = false))
    }

    @Test
    fun labelShowsShowOnHomeWhenHidden() {
        assertEquals("Show on Home", sectionHideOnHomeLabel(isSectionHidden = true))
    }

    @Test
    fun snackbarShowsSectionHiddenWhenHiding() {
        assertEquals("Section hidden on Home", sectionHideOnHomeSnackbar(nextHidden = true))
    }

    @Test
    fun snackbarShowsSectionShownWhenShowing() {
        assertEquals("Section shown on Home", sectionHideOnHomeSnackbar(nextHidden = false))
    }
}
