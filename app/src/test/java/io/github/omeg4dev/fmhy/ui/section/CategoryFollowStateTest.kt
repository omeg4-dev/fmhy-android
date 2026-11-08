package io.github.omeg4dev.fmhy.ui.section

import io.github.omeg4dev.fmhy.data.user.Follow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryFollowStateTest {

    private fun follow(
        sectionId: String,
        headingId: String = "",
        title: String = sectionId,
        excluded: Boolean = false,
    ) = Follow(
        sectionId = sectionId,
        headingId = headingId,
        title = title,
        createdAt = 1000L,
        excluded = excluded,
    )

    @Test
    fun testWholeSectionFollow_categoryIsFollowed() {
        val follows = listOf(
            follow(sectionId = "video", headingId = ""),
        )
        assertTrue(
            "Category under a followed section should be considered followed",
            isCategoryFollowed(follows, sectionId = "video", headingId = "movies"),
        )
    }

    @Test
    fun testExclusionRow_categoryIsNotFollowed() {
        val follows = listOf(
            follow(sectionId = "video", headingId = ""),
            follow(sectionId = "video", headingId = "movies", excluded = true),
        )
        assertFalse(
            "Category with an exclusion row under a followed section should not be followed",
            isCategoryFollowed(follows, sectionId = "video", headingId = "movies"),
        )
    }

    @Test
    fun testDirectSubFollow_categoryIsFollowed() {
        val follows = listOf(
            follow(sectionId = "video", headingId = "movies", excluded = false),
        )
        assertTrue(
            "Directly followed category without section follow should be followed",
            isCategoryFollowed(follows, sectionId = "video", headingId = "movies"),
        )
    }

    @Test
    fun testNoFollows_categoryIsNotFollowed() {
        val follows = emptyList<Follow>()
        assertFalse(
            "Category with no follows should not be followed",
            isCategoryFollowed(follows, sectionId = "video", headingId = "movies"),
        )
    }

    @Test
    fun testDifferentSectionOrSubFollow_categoryIsNotFollowed() {
        val follows = listOf(
            follow(sectionId = "audio", headingId = ""),
            follow(sectionId = "video", headingId = "tv", excluded = false),
        )
        assertFalse(
            "Category not matching followed section or sub should not be followed",
            isCategoryFollowed(follows, sectionId = "video", headingId = "movies"),
        )
    }

    @Test
    fun testNullHeading_returnsFalse() {
        val follows = listOf(
            follow(sectionId = "video", headingId = ""),
        )
        assertFalse(
            "Null heading should not be followed",
            isCategoryFollowed(follows, sectionId = "video", headingId = null),
        )
    }
}
