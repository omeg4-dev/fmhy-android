package io.github.omeg4dev.fmhy.ui.common

import io.github.omeg4dev.fmhy.data.user.Follow

enum class SectionFollowState { NONE, PARTIAL, ALL }

class FollowTree(follows: List<Follow>) {

    private val followedSections: Set<String> = follows
        .filter { it.headingId.isEmpty() && !it.excluded }
        .map { it.sectionId }
        .toSet()

    private val excludedSubsections: Set<Pair<String, String>> = follows
        .filter { it.headingId.isNotEmpty() && it.excluded }
        .map { it.sectionId to it.headingId }
        .toSet()

    private val plainSubsections: Set<Pair<String, String>> = follows
        .filter { it.headingId.isNotEmpty() && !it.excluded }
        .map { it.sectionId to it.headingId }
        .toSet()

    val isEmpty: Boolean = follows.isEmpty()

    fun isSectionFollowed(sectionId: String): Boolean {
        return sectionId in followedSections
    }

    fun isSubsectionFollowed(sectionId: String, headingId: String): Boolean {
        return if (isSectionFollowed(sectionId)) {
            (sectionId to headingId) !in excludedSubsections
        } else {
            (sectionId to headingId) in plainSubsections
        }
    }

    fun sectionState(sectionId: String, subsectionIds: List<String>): SectionFollowState {
        return if (isSectionFollowed(sectionId)) {
            val excludedCount = subsectionIds.count { (sectionId to it) in excludedSubsections }
            when {
                subsectionIds.isNotEmpty() && excludedCount == subsectionIds.size -> SectionFollowState.NONE
                excludedCount > 0 -> SectionFollowState.PARTIAL
                else -> SectionFollowState.ALL
            }
        } else {
            val plainCount = subsectionIds.count { (sectionId to it) in plainSubsections }
            when {
                subsectionIds.isNotEmpty() && plainCount == subsectionIds.size -> SectionFollowState.ALL
                plainCount > 0 -> SectionFollowState.PARTIAL
                else -> SectionFollowState.NONE
            }
        }
    }

    fun followedCount(subsectionsBySection: Map<String, List<String>>): Int {
        return subsectionsBySection.count { (sectionId, subsectionIds) ->
            sectionState(sectionId, subsectionIds) != SectionFollowState.NONE
        }
    }
}
