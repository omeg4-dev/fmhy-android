package io.github.omeg4dev.fmhy.ui.section

import org.junit.Assert.assertEquals
import org.junit.Test

class SubsectionFollowMenuLabelTest {

    @Test
    fun testSubsectionFollowMenuLabel() {
        assertEquals("Follow VPN", subsectionFollowMenuLabel("VPN", isFollowed = false))
        assertEquals("Unfollow VPN", subsectionFollowMenuLabel("VPN", isFollowed = true))
    }
}
