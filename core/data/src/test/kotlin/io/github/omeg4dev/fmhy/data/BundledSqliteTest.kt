package io.github.omeg4dev.fmhy.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.junit.Assert.assertEquals
import org.junit.Test

class BundledSqliteTest {
  @Test fun fts5TrigramAvailable() {
    val c = BundledSQLiteDriver().open(":memory:")
    c.execSQL("CREATE VIRTUAL TABLE t USING fts5(a, tokenize = 'trigram')")
    c.execSQL("INSERT INTO t(a) VALUES ('Stremio')")
    val n = c.prepare("SELECT count(*) FROM t WHERE t MATCH 'remi'").use { it.step(); it.getLong(0) }
    assertEquals(1L, n)
    c.close()
  }
}
