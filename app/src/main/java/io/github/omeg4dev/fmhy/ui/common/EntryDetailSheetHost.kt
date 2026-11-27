package io.github.omeg4dev.fmhy.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.nsfw.NSFW_SECTION_ID
import io.github.omeg4dev.fmhy.data.user.Follow
import kotlinx.coroutines.launch

@Composable
internal fun EntryDetailSheetHost(
    entry: Entry,
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    alternatives: List<Entry> = emptyList(),
    history: List<ChangeItem>? = null,
    onShowInSection: (() -> Unit)? = null,
    followState: FollowState? = null,
    onToggleFollow: (() -> Unit)? = null,
    isPinned: Boolean? = null,
    onTogglePin: (() -> Unit)? = null,
    onShowSnackbar: (String) -> Unit = {},
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val datasetStore = LocalDatasetStore.current
    val userStore = LocalUserStore.current
    val snackbarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val container = LocalAppContainer.current ?: (context.applicationContext as? FmhyApp)?.container
    val isNsfw = entry.sectionId == NSFW_SECTION_ID
    val targetHeadingId = entry.headingId ?: ""
    val rememberedIsPinned = if (isNsfw) false else rememberIsPinned(container, entry.sectionId, targetHeadingId)
    val effectiveIsPinned = if (isNsfw) false else (isPinned ?: rememberedIsPinned)

    var loadedHistory by remember(entry.id, history) { mutableStateOf<List<ChangeItem>?>(history) }
    LaunchedEffect(visible, history, entry.id) {
        if (!isNsfw && visible && history == null && datasetStore != null) {
            loadedHistory = try {
                datasetStore.entryHistory(entry.id)
            } catch (_: Exception) {
                null
            }
        }
    }

    var followsList by remember { mutableStateOf<List<Follow>>(emptyList()) }
    LaunchedEffect(userStore, visible) {
        if (!isNsfw && visible && userStore != null && followState == null) {
            userStore.changes.collect {
                followsList = userStore.follows()
            }
        }
    }

    val categoryTitle = remember(entry.headingPath) {
        val segments = entry.headingPath
            .split(HEADING_PATH_SEPARATOR)
            .filter { it.isNotBlank() }
            .map { CrossLinks.splitLinkTitle(it).first }
        segments.drop(1).firstOrNull() ?: segments.firstOrNull().orEmpty()
    }

    val effectiveFollowState = remember(isNsfw, followState, followsList, entry.sectionId, entry.headingId, categoryTitle) {
        if (isNsfw) {
            null
        } else {
            followState ?: if (categoryTitle.isNotEmpty()) {
                val tree = FollowTree(followsList)
                val isFollowing = tree.isSubsectionFollowed(entry.sectionId, targetHeadingId)
                FollowState(categoryTitle = categoryTitle, isFollowing = isFollowing)
            } else {
                null
            }
        }
    }

    val effectiveToggleFollow: (() -> Unit)? = remember(
        isNsfw,
        onToggleFollow,
        effectiveFollowState,
        userStore,
        container,
        entry.sectionId,
        targetHeadingId,
        entry.headingPath,
        snackbarHost,
        effectiveIsPinned,
    ) {
        if (isNsfw) {
            null
        } else {
            onToggleFollow ?: if (userStore != null && effectiveFollowState != null) {
                {
                    scope.launch {
                        val isCurrentlyFollowing = effectiveFollowState.isFollowing
                        val nextFollowing = !isCurrentlyFollowing
                        val segments = entry.headingPath
                            .split(HEADING_PATH_SEPARATOR)
                            .filter { it.isNotBlank() }
                            .map { CrossLinks.splitLinkTitle(it).first }
                        val rawSec = segments.firstOrNull() ?: entry.sectionId
                        val sectionTitle = sectionDisplayTitle(entry.sectionId, rawSec)
                        val title = if (segments.size >= 2) "$sectionTitle › ${effectiveFollowState.categoryTitle}" else effectiveFollowState.categoryTitle

                        userStore.setSubsectionFollowed(entry.sectionId, targetHeadingId, title, nextFollowing)
                        if (!nextFollowing) {
                            haptics.remove()
                            val host = snackbarHost
                            if (host != null) {
                                if (host.showUndo("Unfollowed ${effectiveFollowState.categoryTitle}")) {
                                    userStore.setSubsectionFollowed(entry.sectionId, targetHeadingId, title, true)
                                }
                            } else {
                                onShowSnackbar("Unfollowed ${effectiveFollowState.categoryTitle}")
                            }
                        } else {
                            val host = snackbarHost
                            if (host != null) {
                                showFollowing(
                                    snackbarHostState = host,
                                    title = effectiveFollowState.categoryTitle,
                                    isPinned = effectiveIsPinned,
                                ) {
                                    if (container != null) {
                                        pinItem(
                                            container = container,
                                            snackbarHostState = host,
                                            sectionId = entry.sectionId,
                                            headingId = targetHeadingId,
                                            title = effectiveFollowState.categoryTitle,
                                        )
                                    }
                                }
                            } else {
                                onShowSnackbar("Following ${effectiveFollowState.categoryTitle}")
                            }
                        }
                    }
                }
            } else {
                null
            }
        }
    }

    val effectiveTogglePin: (() -> Unit)? = remember(
        isNsfw,
        onTogglePin,
        container,
        snackbarHost,
        haptics,
        entry.sectionId,
        targetHeadingId,
        categoryTitle,
        effectiveIsPinned,
    ) {
        if (isNsfw) {
            null
        } else {
            onTogglePin ?: if (container != null && categoryTitle.isNotEmpty() && snackbarHost != null) {
                {
                    scope.launch {
                        if (effectiveIsPinned) {
                            unpinItem(
                                container = container,
                                snackbarHostState = snackbarHost,
                                haptics = haptics,
                                sectionId = entry.sectionId,
                                headingId = targetHeadingId,
                                title = categoryTitle,
                            )
                        } else {
                            pinItem(
                                container = container,
                                snackbarHostState = snackbarHost,
                                sectionId = entry.sectionId,
                                headingId = targetHeadingId,
                                title = categoryTitle,
                            )
                        }
                    }
                }
            } else {
                null
            }
        }
    }

    val pasteDecoder = LocalPasteDecoder.current
    if (visible) {
        EntryDetailBottomSheet(
            entry = entry,
            alternatives = alternatives,
            history = loadedHistory,
            onShowInSection = onShowInSection,
            followState = effectiveFollowState,
            onToggleFollow = effectiveToggleFollow,
            isPinned = effectiveIsPinned,
            onTogglePin = effectiveTogglePin,
            onDismiss = onDismiss,
            onOpenUrl = { url ->
                onDismiss()
                onOpenUrl(url)
            },
            onCopyLink = { url ->
                onDismiss()
                pasteDecoder.linkText(url, PasteAction.COPY) { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", text))
                    onShowSnackbar(if ('\n' in text) "Links copied to clipboard" else "Link copied to clipboard")
                }
            },
            onShareLink = { url ->
                onDismiss()
                pasteDecoder.linkText(url, PasteAction.SHARE) { text ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share ${entry.name}"))
                }
            },
            onShowSnackbar = onShowSnackbar,
        )
    }
}
