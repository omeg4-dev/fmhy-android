package io.github.omeg4dev.fmhy.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashReportStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun latestOnly() {
        val file = File(tempFolder.root, "crash.json")
        val store = CrashReportStore(file, enabled = { true })

        val crash1 = CrashDetails("java.lang.NullPointerException", listOf("Frame1"), 1000L)
        val crash2 = CrashDetails("java.lang.IllegalStateException", listOf("Frame2"), 2000L)

        store.save(crash1)
        assertEquals(crash1, store.peek())

        store.save(crash2)
        assertEquals("Latest crash should overwrite previous one", crash2, store.peek())
    }

    @Test
    fun clearDeletesFileGone() {
        val file = File(tempFolder.root, "crash.json")
        val store = CrashReportStore(file, enabled = { true })

        val crash = CrashDetails("java.lang.NullPointerException", listOf("Frame1"), 1000L)
        store.save(crash)
        assertTrue("File should exist after save", file.exists())
        assertNotNull(store.peek())

        store.clear()
        assertFalse("File must be gone after clear()", file.exists())
        assertNull("peek() must return null after clear()", store.peek())
    }

    @Test
    fun nothingKeptWhenEnabledIsFalse() {
        val file = File(tempFolder.root, "crash.json")
        var enabled = false
        val store = CrashReportStore(file, enabled = { enabled })

        val crash = CrashDetails("java.lang.NullPointerException", listOf("Frame1"), 1000L)
        store.save(crash)

        assertFalse("File must not exist when enabled is false", file.exists())
        assertNull("peek() must return null when enabled is false", store.peek())
    }

    @Test
    fun survivesNewStoreInstanceOnSameFile() {
        val file = File(tempFolder.root, "crash.json")
        val store1 = CrashReportStore(file, enabled = { true })

        val crash = CrashDetails("java.lang.RuntimeException", listOf("Frame1", "Frame2"), 12345L)
        store1.save(crash)

        val store2 = CrashReportStore(file, enabled = { true })
        assertEquals("New store instance on same file should peek the same crash", crash, store2.peek())
    }
}
