package io.github.omeg4dev.fmhy.browser.download

import io.github.omeg4dev.fmhy.data.user.UserStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class VtSiteChecksTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun testRememberAndMarkForWithHostNormalization() = runTest {
        val userStore = UserStore(tmp.newFile("user1.db"))
        var now = 1_000L
        val vtChecks = VtSiteChecks(userStore, clock = { now })

        vtChecks.remember(" Example.COM ", 0, 94)

        val mark = vtChecks.markFor("example.com")
        assertNotNull(mark)
        assertEquals(0, mark?.flagged)
        assertEquals(94, mark?.engines)
        assertEquals(1_000L, mark?.checkedAt)

        val markSpaces = vtChecks.markFor("  EXAMPLE.com  ")
        assertNotNull(markSpaces)
        assertEquals(mark, markSpaces)
        assertEquals(mark, vtChecks.checked.value["example.com"])
    }

    @Test
    fun testExpiry() = runTest {
        val userStore = UserStore(tmp.newFile("user2.db"))
        var now = 1_000L
        val t0 = now
        val vtChecks = VtSiteChecks(userStore, clock = { now })

        vtChecks.remember("example.com", 0, 94)

        now = t0 + VtSiteChecks.TTL_MS - 1
        assertNotNull(vtChecks.markFor("example.com"))

        now = t0 + VtSiteChecks.TTL_MS
        assertNull(vtChecks.markFor("example.com"))
    }

    @Test
    fun testPersistenceAndReloadExpiry() = runTest {
        val dbFile = tmp.newFile("user3.db")
        val userStore = UserStore(dbFile)
        var now = 1_000L
        val vt1 = VtSiteChecks(userStore, clock = { now })
        vt1.remember("example.com", 0, 94)

        val vt2 = VtSiteChecks(userStore, clock = { now })
        vt2.load()
        val mark = vt2.markFor("example.com")
        assertNotNull(mark)
        assertEquals(0, mark?.flagged)
        assertEquals(94, mark?.engines)
        assertEquals(1, vt2.checked.value.size)

        now += VtSiteChecks.TTL_MS
        val vt3 = VtSiteChecks(userStore, clock = { now })
        vt3.load()
        assertNull(vt3.markFor("example.com"))
        assertTrue(vt3.checked.value.isEmpty())
    }

    @Test
    fun testForgetAll() = runTest {
        val userStore = UserStore(tmp.newFile("user4.db"))
        var now = 1_000L
        val vtChecks = VtSiteChecks(userStore, clock = { now })
        vtChecks.remember("example.com", 0, 94)

        assertNotNull(vtChecks.markFor("example.com"))
        assertEquals(1, vtChecks.checked.value.size)

        vtChecks.forgetAll()

        assertNull(vtChecks.markFor("example.com"))
        assertTrue(vtChecks.checked.value.isEmpty())
        assertNull(userStore.get(VtSiteChecks.KEY))
    }

    @Test
    fun testCapEnforcement() = runTest {
        val userStore = UserStore(tmp.newFile("user5.db"))
        var now = 1_000L
        val vtChecks = VtSiteChecks(userStore, clock = { now })

        for (i in 1..501) {
            now += 10L
            vtChecks.remember("host$i.com", 0, 94)
        }

        assertEquals(500, vtChecks.checked.value.size)
        assertNull(vtChecks.markFor("host1.com"))
        assertNotNull(vtChecks.markFor("host2.com"))
        assertNotNull(vtChecks.markFor("host501.com"))
    }

    @Test
    fun testShouldRemember() {
        assertTrue(VtSiteChecks.shouldRemember(ScanVerdict.NO_FLAGS))
        assertFalse(VtSiteChecks.shouldRemember(ScanVerdict.SUSPICIOUS))
        assertFalse(VtSiteChecks.shouldRemember(ScanVerdict.DANGEROUS))
        assertFalse(VtSiteChecks.shouldRemember(ScanVerdict.UNKNOWN))
    }

    @Test
    fun testCorruptKvValueDoesNotThrow() = runTest {
        val userStore = UserStore(tmp.newFile("user6.db"))
        userStore.put(VtSiteChecks.KEY, "{not json")

        var now = 1_000L
        val vtChecks = VtSiteChecks(userStore, clock = { now })
        vtChecks.load()

        assertTrue(vtChecks.checked.value.isEmpty())
        assertNull(vtChecks.markFor("example.com"))
    }
}
