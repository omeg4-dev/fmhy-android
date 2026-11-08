package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.material3.SnackbarHostState
import io.github.omeg4dev.fmhy.data.user.Follow
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.ui.common.AppHaptics
import io.github.omeg4dev.fmhy.ui.common.FollowTree
import io.github.omeg4dev.fmhy.ui.common.showUndo

/**
 * Checks whether a category (heading within a section) is followed, taking into account
 * section-level follows, explicit exclusions, and direct subsection follows.
 */
fun isCategoryFollowed(
    follows: List<Follow>,
    sectionId: String,
    headingId: String?,
): Boolean {
    if (headingId == null) return false
    return FollowTree(follows).isSubsectionFollowed(sectionId, headingId)
}

/**
 * Unfollows a section after capturing a snapshot of all follows under that section,
 * triggers haptic feedback, and displays a snackbar with an Undo action.
 * If Undo is tapped, restores all previously existing follows under the section.
 */
suspend fun unfollowSectionWithUndo(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
    haptics: AppHaptics,
    sectionId: String,
    sectionTitle: String,
    followsSnapshot: List<Follow>? = null,
) {
    val snapshots = followsSnapshot ?: container.userStore.follows().filter { it.sectionId == sectionId }
    container.userStore.unfollowSection(sectionId)
    haptics.remove()
    val undone = snackbarHostState.showUndo("Unfollowed $sectionTitle")
    if (undone) {
        for (f in snapshots) {
            container.userStore.restoreFollow(f)
        }
    }
}
