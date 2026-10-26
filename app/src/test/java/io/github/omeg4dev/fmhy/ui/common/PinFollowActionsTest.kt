package io.github.omeg4dev.fmhy.ui.common

import io.github.omeg4dev.fmhy.ui.settings.shouldShowFollowingPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinFollowActionsTest {

    @Test
    fun testPinResultMessagePersonalHomeTrue() {
        val (message, action) = pinResultMessage(personalHome = true)
        assertEquals("Pinned to Home", message)
        assertNull(action)
    }

    @Test
    fun testPinResultMessagePersonalHomeFalse() {
        val (message, action) = pinResultMessage(personalHome = false)
        assertEquals("Pinned. Turn on Personal Home to see pins", message)
        assertEquals("Turn on", action)
    }

    @Test
    fun testFollowMessageAction() {
        assertNull(followMessageAction(isPinned = true))
        assertEquals("Pin to Home", followMessageAction(isPinned = false))
    }

    @Test
    fun testSubsectionPinMenuLabel() {
        assertEquals("Unpin VPN", subsectionPinMenuLabel("VPN", isPinned = true))
        assertEquals("Pin VPN to Home", subsectionPinMenuLabel("VPN", isPinned = false))
    }

    @Test
    fun testShouldShowFollowingPin() {
        assertTrue(shouldShowFollowingPin(isFollowed = true, isExcluded = false, isPinned = false))
        assertTrue(shouldShowFollowingPin(isFollowed = true, isExcluded = false, isPinned = true))
        assertTrue(shouldShowFollowingPin(isFollowed = false, isExcluded = false, isPinned = true))
        assertFalse(shouldShowFollowingPin(isFollowed = false, isExcluded = false, isPinned = false))
        assertFalse(shouldShowFollowingPin(isFollowed = true, isExcluded = true, isPinned = false))
        assertFalse(shouldShowFollowingPin(isFollowed = true, isExcluded = true, isPinned = true))
        assertFalse(shouldShowFollowingPin(isFollowed = false, isExcluded = true, isPinned = true))
    }
}
