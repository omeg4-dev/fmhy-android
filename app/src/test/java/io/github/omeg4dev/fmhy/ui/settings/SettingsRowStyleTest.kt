package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRowStyleTest {

    @Test
    fun settingsDescriptionGapIsFourDp() {
        assertEquals(4.dp, SettingsDescriptionGap)
    }
}
