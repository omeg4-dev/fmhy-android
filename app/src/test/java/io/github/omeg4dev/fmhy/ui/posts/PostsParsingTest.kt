package io.github.omeg4dev.fmhy.ui.posts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PostsParsingTest {

    @Test
    fun parseDate_fromFrontMatter() {
        val dateEpoch = PostsViewModel.parseDate("arbitrary-name", "2026-04-01")
        val expected = LocalDate.of(2026, 4, 1).toEpochDay() * 86400L
        assertEquals(expected, dateEpoch)
    }

    @Test
    fun parseDate_fromMonthFileName() {
        val oct2026 = PostsViewModel.parseDate("oct-2026", null)
        val expectedOct = LocalDate.of(2026, 10, 1).toEpochDay() * 86400L
        assertEquals(expectedOct, oct2026)

        val april2023 = PostsViewModel.parseDate("april-2023", null)
        val expectedApr = LocalDate.of(2023, 4, 1).toEpochDay() * 86400L
        assertEquals(expectedApr, april2023)

        val sept2024 = PostsViewModel.parseDate("sept-2024", null)
        val expectedSept = LocalDate.of(2024, 9, 1).toEpochDay() * 86400L
        assertEquals(expectedSept, sept2024)
    }

    @Test
    fun sorting_newestFirst() {
        val files = listOf("april-2023", "oct-2026", "sept-2025", "jan-2026")
        val sorted = files.sortedByDescending { PostsViewModel.parseDate(it, null) }
        assertEquals(listOf("oct-2026", "jan-2026", "sept-2025", "april-2023"), sorted)
    }

    @Test
    fun formatDefaultTitle_monthFiles() {
        val t1 = PostsViewModel.formatDefaultTitle("oct-2026")
        assertEquals("Monthly Updates [October 2026]", t1)

        val t2 = PostsViewModel.formatDefaultTitle("KeepAndroidOpen")
        assertEquals("KeepAndroidOpen", t2)
    }
}
