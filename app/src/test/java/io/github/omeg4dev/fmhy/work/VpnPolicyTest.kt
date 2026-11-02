package io.github.omeg4dev.fmhy.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnPolicyTest {

    @Test
    fun stateOf_hasVpnTransportMapping() {
        assertEquals(VpnState.ACTIVE, VpnPolicy.stateOf(true))
        assertEquals(VpnState.INACTIVE, VpnPolicy.stateOf(false))
    }

    @Test
    fun markFor_nullWhenDisabledElseState() {
        // When showVpnState is false, markFor returns null for both states
        assertNull(VpnPolicy.markFor(VpnState.ACTIVE, showVpnState = false))
        assertNull(VpnPolicy.markFor(VpnState.INACTIVE, showVpnState = false))

        // When showVpnState is true, markFor returns the state
        assertEquals(VpnState.ACTIVE, VpnPolicy.markFor(VpnState.ACTIVE, showVpnState = true))
        assertEquals(VpnState.INACTIVE, VpnPolicy.markFor(VpnState.INACTIVE, showVpnState = true))
    }
}
