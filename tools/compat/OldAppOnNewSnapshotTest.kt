package io.github.omeg4dev.fmhy.data.compat

import io.github.omeg4dev.fmhy.data.DatasetStore
import java.io.File
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Copied by tools/compat/old-reader-replay.sh into an OLD release tag (v1.1.x / v1.2.x) and run there against the
 * NEW bundled snapshot. Uses only DatasetStore APIs that exist in every released version. Proves that an old
 * reader (a) can still read every table it knows from a schema-5 dataset, (b) recognises it as "not current"
 * (the trigger for its own self-heal: reinstall of its bundled schema-4 snapshot).
 */
class OldAppOnNewSnapshotTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun oldReaderReadsNewSnapshotAndSeesItAsNotCurrent() = runTest {
        val snapshot = File("../../app/src/main/assets/dataset/snapshot.gzbin")
        assertTrue("new snapshot missing at ${snapshot.absolutePath}", snapshot.exists())
        val dbFile = tmp.newFile("new-snapshot.db")
        GZIPInputStream(snapshot.inputStream()).use { input -> dbFile.outputStream().use { input.copyTo(it) } }

        val store = DatasetStore(dbFile)
        assertEquals("new snapshot schema", 5, store.schemaVersion())
        assertFalse("old reader must see schema 5 as not current", store.isSchemaCurrent())

        val sections = store.sections()
        assertTrue("sections: ${sections.size}", sections.size >= 20)
        var entries = 0
        for (s in sections) {
            entries += store.sectionItems(s.id).size.coerceAtLeast(0)
        }
        assertTrue("items read by the old reader: $entries", entries > 15_000)
        assertTrue("entryCount: ${store.entryCount()}", store.entryCount() > 20_000)
        store.close()
    }
}
