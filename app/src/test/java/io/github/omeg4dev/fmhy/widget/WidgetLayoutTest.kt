package io.github.omeg4dev.fmhy.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutTest {
    @Test fun oneRowIsSmallTwoRowsIsLarge() {
        assertEquals(false, widgetUsesLargeLayout(70))
        assertEquals(false, widgetUsesLargeLayout(109))
        assertEquals(true, widgetUsesLargeLayout(110))
        assertEquals(true, widgetUsesLargeLayout(180))
    }
}
