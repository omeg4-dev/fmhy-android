package io.github.omeg4dev.fmhy.ui.common

import io.github.omeg4dev.fmhy.data.user.Follow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowTreeTest {

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
    fun mainSectionFollowed_allSubsectionsFollowed() {
        val tree = FollowTree(listOf(follow("video", "")))

        assertTrue(tree.isSectionFollowed("video"))
        assertTrue(tree.isSubsectionFollowed("video", "movies"))
        assertTrue(tree.isSubsectionFollowed("video", "tv"))
        assertEquals(
            SectionFollowState.ALL,
            tree.sectionState("video", listOf("movies", "tv")),
        )
    }

    @Test
    fun excludedSubsection_partialAndSubsectionFalse() {
        val tree = FollowTree(
            listOf(
                follow("video", ""),
                follow("video", "movies", excluded = true),
            ),
        )

        assertTrue(tree.isSectionFollowed("video"))
        assertFalse(tree.isSubsectionFollowed("video", "movies"))
        assertTrue(tree.isSubsectionFollowed("video", "tv"))
        assertEquals(
            SectionFollowState.PARTIAL,
            tree.sectionState("video", listOf("movies", "tv")),
        )
    }

    @Test
    fun allSubsectionsExcluded_returnsNone() {
        val tree = FollowTree(
            listOf(
                follow("video", ""),
                follow("video", "movies", excluded = true),
                follow("video", "tv", excluded = true),
            ),
        )

        assertTrue(tree.isSectionFollowed("video"))
        assertFalse(tree.isSubsectionFollowed("video", "movies"))
        assertFalse(tree.isSubsectionFollowed("video", "tv"))
        assertEquals(
            SectionFollowState.NONE,
            tree.sectionState("video", listOf("movies", "tv")),
        )
    }

    @Test
    fun subsectionOnlyFollow_partialForSectionAndTrueForThatSubsectionOnly() {
        val tree = FollowTree(
            listOf(
                follow("video", "movies", excluded = false),
            ),
        )

        assertFalse(tree.isSectionFollowed("video"))
        assertTrue(tree.isSubsectionFollowed("video", "movies"))
        assertFalse(tree.isSubsectionFollowed("video", "tv"))
        assertEquals(
            SectionFollowState.PARTIAL,
            tree.sectionState("video", listOf("movies", "tv")),
        )
    }

    @Test
    fun allSubsectionsPlainFollowed_returnsAll() {
        val tree = FollowTree(
            listOf(
                follow("video", "movies", excluded = false),
                follow("video", "tv", excluded = false),
            ),
        )

        assertFalse(tree.isSectionFollowed("video"))
        assertTrue(tree.isSubsectionFollowed("video", "movies"))
        assertTrue(tree.isSubsectionFollowed("video", "tv"))
        assertEquals(
            SectionFollowState.ALL,
            tree.sectionState("video", listOf("movies", "tv")),
        )
    }

    @Test
    fun newSubsectionUnknownToExclusionList_underFollowedSectionIsFollowed() {
        val tree = FollowTree(
            listOf(
                follow("video", ""),
                follow("video", "movies", excluded = true),
            ),
        )

        // "anime" is unknown to the exclusion list, so it is followed automatically
        assertTrue(tree.isSubsectionFollowed("video", "anime"))
        assertEquals(
            SectionFollowState.PARTIAL,
            tree.sectionState("video", listOf("movies", "tv", "anime")),
        )
    }

    @Test
    fun emptyList_returnsNone() {
        val emptyTree = FollowTree(emptyList())
        assertEquals(
            SectionFollowState.NONE,
            emptyTree.sectionState("video", emptyList()),
        )

        val partialTree = FollowTree(listOf(follow("video", "movies")))
        assertEquals(
            SectionFollowState.NONE,
            partialTree.sectionState("video", emptyList()),
        )
    }

    @Test
    fun followedCount() {
        val tree = FollowTree(
            listOf(
                follow("video", ""), // whole video followed
                follow("video", "movies", excluded = true), // 1 excluded out of 2 -> PARTIAL
                follow("audio", ""), // whole audio followed -> ALL
                follow("gaming", ""), // whole gaming followed
                follow("gaming", "emulation", excluded = true), // all excluded -> NONE
                follow("reading", "books"), // plain follow -> PARTIAL
                // tools not followed at all -> NONE
            ),
        )

        val subsections = mapOf(
            "video" to listOf("movies", "tv"),
            "audio" to listOf("music", "podcasts"),
            "gaming" to listOf("emulation"),
            "reading" to listOf("books", "comics"),
            "tools" to listOf("dev", "system"),
        )

        // video: PARTIAL (!= NONE) -> counted
        // audio: ALL (!= NONE) -> counted
        // gaming: NONE (== NONE) -> not counted
        // reading: PARTIAL (!= NONE) -> counted
        // tools: NONE (== NONE) -> not counted
        assertEquals(3, tree.followedCount(subsections))
    }

    @Test
    fun isEmpty() {
        val emptyTree = FollowTree(emptyList())
        assertTrue(emptyTree.isEmpty)

        val nonEmptyTree = FollowTree(listOf(follow("video", "")))
        assertFalse(nonEmptyTree.isEmpty)
    }
}
