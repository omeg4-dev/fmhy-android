package io.github.omeg4dev.fmhy.ui.search

import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.search.CategoryHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCategoryBlockTest {

    private fun fakeHit(id: String) = CategoryHit(
        section = Section(id = id, title = "Section $id", description = "", ord = 0, entryCount = 0),
        heading = null,
        path = emptyList(),
    )

    @Test
    fun emptyCategoriesReturnsEmptyBlockWithNullToggle() {
        val collapsed = categoryBlock(emptyList(), expanded = false)
        assertTrue(collapsed.rows.isEmpty())
        assertNull(collapsed.toggleLabel)

        val expanded = categoryBlock(emptyList(), expanded = true)
        assertTrue(expanded.rows.isEmpty())
        assertNull(expanded.toggleLabel)
    }

    @Test
    fun twoCategoriesCollapsedAndExpanded() {
        val cats = (1..2).map { fakeHit("sec_$it") }
        val collapsed = categoryBlock(cats, expanded = false)
        assertEquals(2, collapsed.rows.size)
        assertNull(collapsed.toggleLabel)

        val expanded = categoryBlock(cats, expanded = true)
        assertEquals(2, expanded.rows.size)
        assertNull(expanded.toggleLabel)
    }

    @Test
    fun threeCategoriesCollapsedAndExpanded() {
        val cats = (1..3).map { fakeHit("sec_$it") }
        val collapsed = categoryBlock(cats, expanded = false)
        assertEquals(3, collapsed.rows.size)
        assertNull(collapsed.toggleLabel)

        val expanded = categoryBlock(cats, expanded = true)
        assertEquals(3, expanded.rows.size)
        assertNull(expanded.toggleLabel)
    }

    @Test
    fun fifteenCategoriesCollapsed() {
        val cats = (1..15).map { fakeHit("sec_$it") }
        val block = categoryBlock(cats, expanded = false)
        assertEquals(3, block.rows.size)
        assertEquals(cats.take(3), block.rows)
        assertEquals("View all 15 categories", block.toggleLabel)
    }

    @Test
    fun fifteenCategoriesExpanded() {
        val cats = (1..15).map { fakeHit("sec_$it") }
        val block = categoryBlock(cats, expanded = true)
        assertEquals(15, block.rows.size)
        assertEquals(cats, block.rows)
        assertEquals("Show fewer categories", block.toggleLabel)
    }

    @Test
    fun fiftyCategoriesCollapsed() {
        val cats50 = (1..50).map { fakeHit("sec_$it") }
        val block50 = categoryBlock(cats50, expanded = false)
        assertEquals(3, block50.rows.size)
        assertEquals(cats50.take(3), block50.rows)
        assertEquals("View all 50+ categories", block50.toggleLabel)

        val cats55 = (1..55).map { fakeHit("sec_$it") }
        val block55 = categoryBlock(cats55, expanded = false)
        assertEquals(3, block55.rows.size)
        assertEquals("View all 50+ categories", block55.toggleLabel)
    }

    @Test
    fun invariantsOverSizes0To60() {
        for (size in 0..60) {
            val cats = (1..size).map { fakeHit("sec_$it") }
            for (expanded in listOf(false, true)) {
                val block = categoryBlock(cats, expanded)
                assertEquals(
                    "rows.isEmpty() should match categories.isEmpty() for size=$size, expanded=$expanded",
                    cats.isEmpty(),
                    block.rows.isEmpty()
                )
                if (block.toggleLabel != null) {
                    assertTrue(
                        "toggleLabel != null implies rows.isNotEmpty() for size=$size, expanded=$expanded",
                        block.rows.isNotEmpty()
                    )
                }
            }
        }
    }
}
