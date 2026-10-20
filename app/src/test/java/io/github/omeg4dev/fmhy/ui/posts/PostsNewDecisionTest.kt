package io.github.omeg4dev.fmhy.ui.posts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostsNewDecisionTest {
    private fun post(name: String, epoch: Long) = CachedPost("posts/$name.md", "Monthly $name", name, epoch)

    private val sept = post("sept-2026", 300)
    private val aug = post("aug-2026", 200)
    private val jul = post("jul-2026", 100)

    @Test
    fun newPostIsDetected() {
        val result = newUnreadPosts(listOf(sept, aug, jul), setOf(aug.path, jul.path), setOf(aug.path, jul.path))
        assertEquals(listOf(sept), result)
    }

    @Test
    fun readPostIsNotNew() {
        val result = newUnreadPosts(listOf(sept, aug), setOf(aug.path), setOf(aug.path, sept.path))
        assertTrue(result.isEmpty())
    }

    @Test
    fun noBaselineMeansNothingIsNew() {
        assertTrue(newUnreadPosts(listOf(sept, aug), null, setOf()).isEmpty())
        assertTrue(newUnreadPosts(listOf(sept, aug), setOf(), null).isEmpty())
    }

    @Test
    fun severalNewAreSortedNewestFirst() {
        val result = newUnreadPosts(listOf(jul, sept, aug), setOf(), setOf())
        assertEquals(listOf(sept, aug, jul), result)
    }

    @Test
    fun notificationText() {
        assertNull(newPostsNotificationText(emptyList()))
        assertEquals("New FMHY post" to sept.title, newPostsNotificationText(listOf(sept)))
        assertEquals("New FMHY post" to "${sept.title} and 2 more", newPostsNotificationText(listOf(sept, aug, jul)))
    }

    @Test
    fun checkAtMostOncePer24Hours() {
        assertTrue(shouldCheckPosts(null, 1000))
        assertFalse(shouldCheckPosts(1000, 1000 + 86_399))
        assertTrue(shouldCheckPosts(1000, 1000 + 86_400))
        assertTrue(shouldCheckPosts(5000, 1000))
    }
}
