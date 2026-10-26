package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.user.Pin
import io.github.omeg4dev.fmhy.di.AppContainer
import kotlinx.coroutines.flow.first

/**
 * Message and optional action label for a successful pin.
 * When Personal Home is disabled, offers an action to turn it on so the user can see their pins.
 */
fun pinResultMessage(personalHome: Boolean): Pair<String, String?> =
    if (personalHome) {
        "Pinned to Home" to null
    } else {
        "Pinned. Turn on Personal Home to see pins" to "Turn on"
    }

/**
 * Action label for the "Following X" snackbar.
 * Offers "Pin to Home" if the item is not already pinned.
 */
fun followMessageAction(isPinned: Boolean): String? =
    if (isPinned) null else "Pin to Home"

/**
 * Overflow / context menu label for pinning or unpinning a subsection.
 */
fun subsectionPinMenuLabel(title: String, isPinned: Boolean): String =
    if (isPinned) "Unpin $title" else "Pin $title to Home"

/**
 * Pins an item, displays a snackbar with the appropriate message, and offers to turn on
 * Personal Home if it is currently disabled.
 */
suspend fun pinItem(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
    sectionId: String,
    headingId: String = "",
    title: String,
) {
    container.userStore.pin(sectionId, headingId, title)
    val personalHome = container.settingsRepository.homePrefs.first().personalHome
    val (message, action) = pinResultMessage(personalHome)
    if (action != null) {
        val tapped = snackbarHostState.showAction(message, action)
        if (tapped) {
            container.settingsRepository.updateHome { it.copy(personalHome = true) }
        }
    } else {
        snackbarHostState.showSnackbar(
            message = message,
            withDismissAction = false,
            duration = SnackbarDuration.Short,
        )
    }
}

/**
 * Unpins an item with haptic feedback and an Undo snackbar that restores the pin if tapped.
 */
suspend fun unpinItem(
    container: AppContainer,
    snackbarHostState: SnackbarHostState,
    haptics: AppHaptics,
    sectionId: String,
    headingId: String = "",
    title: String,
) {
    val snapshot = container.userStore.pins().firstOrNull { it.sectionId == sectionId && it.headingId == headingId }
        ?: Pin(sectionId, headingId, title, 0)
    container.userStore.unpin(sectionId, headingId)
    haptics.remove()
    val undone = snackbarHostState.showUndo("Unpinned $title")
    if (undone) {
        container.userStore.restorePin(snapshot)
    }
}

/**
 * Shows "Following [title]" snackbar, with an action "Pin to Home" when [isPinned] is false.
 * Tapping "Pin to Home" invokes [onPin].
 */
suspend fun showFollowing(
    snackbarHostState: SnackbarHostState,
    title: String,
    isPinned: Boolean,
    onPin: suspend () -> Unit,
) {
    val action = followMessageAction(isPinned)
    if (action != null) {
        val tapped = snackbarHostState.showAction("Following $title", action)
        if (tapped) {
            onPin()
        }
    } else {
        snackbarHostState.showSnackbar(
            message = "Following $title",
            withDismissAction = false,
            duration = SnackbarDuration.Short,
        )
    }
}

/**
 * Observes whether an item is pinned, updating automatically whenever userStore changes.
 */
@Composable
fun rememberIsPinned(
    container: AppContainer?,
    sectionId: String,
    headingId: String = "",
): Boolean {
    if (container == null) return false
    val userChanges by container.userStore.changes.collectAsStateWithLifecycle()
    var isPinned by remember(sectionId, headingId) { mutableStateOf(false) }
    LaunchedEffect(userChanges, sectionId, headingId) {
        val pins = container.userStore.pins()
        isPinned = pins.any { it.sectionId == sectionId && it.headingId == headingId }
    }
    return isPinned
}
