package io.github.omeg4dev.fmhy.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier
import kotlinx.coroutines.launch

/** Turns FTS highlight markers (\u0002 ... \u0003) into styled spans: [highlightColor] text on a [background] tint. */
fun parseHighlight(text: String, highlightColor: Color, background: Color = Color.Unspecified): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length
        while (i < len) {
            val startHl = text.indexOf('\u0002', i)
            if (startHl == -1) {
                append(text.substring(i))
                break
            }
            append(text.substring(i, startHl))
            val endHl = text.indexOf('\u0003', startHl + 1)
            if (endHl == -1) {
                append(text.substring(startHl + 1))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = highlightColor, background = background)) {
                append(text.substring(startHl + 1, endHl))
            }
            i = endHl + 1
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryRow(
    entry: Entry,
    modifier: Modifier = Modifier,
    alternatives: List<Entry> = emptyList(),
    nameHl: String? = null,
    descHl: String? = null,
    isNew: Boolean = false,
    updateLine: String? = null,
    history: List<ChangeItem>? = null,
    onShowInSection: (() -> Unit)? = null,
    followState: FollowState? = null,
    onToggleFollow: (() -> Unit)? = null,
    onOpenUrl: (String) -> Unit,
    onShowSnackbar: (String) -> Unit = {},
    folderLabel: String? = null,
    notePreview: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val datasetStore = LocalDatasetStore.current
    val userStore = LocalUserStore.current
    val snackbarHost = LocalSnackbarHost.current
    val saveActions = rememberSaveActions(onShowSnackbar = onShowSnackbar)

    var loadedHistory by remember(entry.id, history) { mutableStateOf<List<ChangeItem>?>(history) }
    LaunchedEffect(showBottomSheet, history, entry.id) {
        if (showBottomSheet && history == null && datasetStore != null) {
            loadedHistory = try {
                datasetStore.entryHistory(entry.id)
            } catch (_: Exception) {
                null
            }
        }
    }

    var followsList by remember { mutableStateOf<List<io.github.omeg4dev.fmhy.data.user.Follow>>(emptyList()) }
    LaunchedEffect(userStore, showBottomSheet) {
        if (showBottomSheet && userStore != null && followState == null) {
            userStore.changes.collect {
                followsList = userStore.follows()
            }
        }
    }

    val categoryTitle = remember(entry.headingPath) {
        val segments = entry.headingPath
            .split(Regex("""\s*[›>]\s*"""))
            .filter { it.isNotBlank() }
            .map { CrossLinks.splitLinkTitle(it).first }
        segments.drop(1).firstOrNull() ?: segments.firstOrNull().orEmpty()
    }

    val effectiveFollowState = remember(followState, followsList, entry.sectionId, entry.headingId, categoryTitle) {
        followState ?: if (categoryTitle.isNotEmpty()) {
            val targetHeadingId = entry.headingId ?: ""
            val tree = FollowTree(followsList)
            val isFollowing = tree.isSubsectionFollowed(entry.sectionId, targetHeadingId)
            FollowState(categoryTitle = categoryTitle, isFollowing = isFollowing)
        } else null
    }

    val effectiveToggleFollow: (() -> Unit)? = remember(onToggleFollow, effectiveFollowState, userStore, entry.sectionId, entry.headingId, entry.headingPath, snackbarHost) {
        onToggleFollow ?: if (userStore != null && effectiveFollowState != null) {
            {
                scope.launch {
                    val targetHeadingId = entry.headingId ?: ""
                    val isCurrentlyFollowing = effectiveFollowState.isFollowing
                    val nextFollowing = !isCurrentlyFollowing
                    val segments = entry.headingPath
                        .split(Regex("""\s*[›>]\s*"""))
                        .filter { it.isNotBlank() }
                        .map { CrossLinks.splitLinkTitle(it).first }
                    val sectionTitle = segments.firstOrNull() ?: entry.sectionId
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
                        onShowSnackbar("Following ${effectiveFollowState.categoryTitle}")
                    }
                }
            }
        } else null
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val hlColor = MaterialTheme.colorScheme.onPrimaryContainer
    val hlBackground = MaterialTheme.colorScheme.primaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val displayName = remember(nameHl, entry.name, hlColor, hlBackground) {
        if (!nameHl.isNullOrEmpty() && nameHl.contains('\u0002')) {
            parseHighlight(nameHl, hlColor, hlBackground)
        } else {
            AnnotatedString(entry.name)
        }
    }

    val (displayDesc, inlineContent) = remember(
        entry.id,
        entry.description,
        entry.extraLinks,
        descHl,
        primaryColor,
        hlColor,
        hlBackground,
        onSurfaceVariant,
    ) {
        val baseDesc = if (!descHl.isNullOrEmpty() && descHl.contains('\u0002')) {
            parseHighlight(descHl, hlColor, hlBackground)
        } else {
            AnnotatedString(entry.description)
        }

        val platformSpans = findPlatformSpans(baseDesc.text)
        val annotated = buildAnnotatedString {
            var cursor = 0
            for ((range, platform, word) in platformSpans) {
                if (range.first > cursor) {
                    append(baseDesc.subSequence(cursor, range.first))
                }
                appendInlineContent("platform:${platform.name}", alternateText = word)
                cursor = range.last + 1
            }
            if (cursor < baseDesc.length) {
                append(baseDesc.subSequence(cursor, baseDesc.length))
            }

            val (socialLinks, otherLinks) = entry.extraLinks.withIndex().partition {
                socialIconFor(it.value.label) != null
            }

            for ((_, link) in otherLinks) {
                if (length > 0) {
                    append(" / ")
                }
                withLink(
                    LinkAnnotation.Clickable(
                        tag = link.url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = primaryColor,
                                fontWeight = FontWeight.Medium,
                            )
                        ),
                        linkInteractionListener = { onOpenUrl(link.url) }
                    )
                ) {
                    append(link.label)
                }
            }

            for ((index, link) in socialLinks) {
                if (length > 0) {
                    append(" ")
                }
                appendInlineContent("link:$index", alternateText = link.label)
            }
        }

        val inlineMap = mutableMapOf<String, InlineTextContent>()
        for (p in Platform.entries) {
            inlineMap["platform:${p.name}"] = InlineTextContent(
                Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)
            ) {
                Icon(
                    painter = painterResource(p.icon),
                    contentDescription = p.word,
                    tint = onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        for ((index, link) in entry.extraLinks.withIndex()) {
            val iconRes = socialIconFor(link.label) ?: continue
            inlineMap["link:$index"] = InlineTextContent(
                Placeholder(1.5.em, 1.5.em, PlaceholderVerticalAlign.TextCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable { onOpenUrl(link.url) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = link.label,
                        tint = primaryColor,
                        modifier = Modifier.fillMaxSize(0.8f),
                    )
                }
            }
        }

        annotated to inlineMap
    }

    val crossLinks = LocalCrossLinks.current
    val isCrossLink = remember(entry.url, crossLinks) {
        crossLinks?.resolve(entry.url) != null
    }

    val swapTapHold = LocalGesturePrefs.current.swapTapHold
    val gestures = rowGestures(swapTapHold)
    val altActionLabel = if (gestures.onTap == RowAction.OPEN_SITE) "Show details" else "Open site"
    val altAction: () -> Boolean = {
        if (gestures.onTap == RowAction.OPEN_SITE) {
            showBottomSheet = true
        } else {
            onOpenUrl(entry.url)
        }
        true
    }

    val websiteStyle = LocalWebsiteStyle.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (websiteStyle) 40.dp else 56.dp)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(
                        label = altActionLabel,
                        action = altAction,
                    )
                )
            }
            // Default: tap opens the site directly, hold opens details (swappable in Settings).
            .combinedClickable(
                onClick = {
                    when (gestures.onTap) {
                        RowAction.OPEN_SITE -> onOpenUrl(entry.url)
                        RowAction.OPEN_MENU -> showBottomSheet = true
                    }
                },
                onLongClick = {
                    haptics.longPress()
                    when (gestures.onHold) {
                        RowAction.OPEN_SITE -> onOpenUrl(entry.url)
                        RowAction.OPEN_MENU -> showBottomSheet = true
                    }
                },
                onClickLabel = if (gestures.onTap == RowAction.OPEN_SITE) "Open site" else "Show details",
                onLongClickLabel = if (gestures.onHold == RowAction.OPEN_SITE) "Open site" else "Show details",
            ),
        color = Color.Transparent,
    ) {
        if (websiteStyle) {
            WebsiteEntryContent(
                entry = entry,
                displayName = displayName,
                descriptionText = if (!descHl.isNullOrEmpty() && descHl.contains('\u0002')) descHl else entry.description,
                alternatives = alternatives,
                isNew = isNew,
                isCrossLink = isCrossLink,
                updateLine = updateLine,
                folderLabel = folderLabel,
                notePreview = notePreview,
                trailingContent = trailingContent,
                onOpenUrl = onOpenUrl,
                onShowBottomSheet = { showBottomSheet = true },
                saveActions = saveActions,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Title row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EntryMarker(entry)

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCrossLink) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (entry.tier != Tier.NORMAL) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    EntryTitleBadges(
                        entry = entry,
                        alternatives = alternatives,
                        isNew = isNew,
                        isCrossLink = isCrossLink,
                        onOpenUrl = onOpenUrl,
                        onShowBottomSheet = { showBottomSheet = true },
                        saveActions = saveActions,
                        trailingContent = trailingContent,
                    )
                }

                if (!updateLine.isNullOrEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = updateLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Description
                if (displayDesc.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = displayDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        inlineContent = inlineContent,
                        maxLines = if (entry.extraLinks.isNotEmpty()) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (isCrossLink) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Opens in the app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (folderLabel != null || !notePreview.isNullOrEmpty()) {
                    EntryFolderNoteMetadata(
                        folderLabel = folderLabel,
                        notePreview = notePreview,
                    )
                }
            }
        }
    }

    val pasteDecoder = LocalPasteDecoder.current
    if (showBottomSheet) {
        val context = LocalContext.current
        EntryDetailBottomSheet(
            entry = entry,
            alternatives = alternatives,
            history = loadedHistory,
            onShowInSection = onShowInSection,
            followState = effectiveFollowState,
            onToggleFollow = effectiveToggleFollow,
            onDismiss = { showBottomSheet = false },
            onOpenUrl = { url ->
                showBottomSheet = false
                onOpenUrl(url)
            },
            onCopyLink = { url ->
                showBottomSheet = false
                pasteDecoder.linkText(url, PasteAction.COPY) { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", text))
                    onShowSnackbar(if ('\n' in text) "Links copied to clipboard" else "Link copied to clipboard")
                }
            },
            onShareLink = { url ->
                showBottomSheet = false
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
