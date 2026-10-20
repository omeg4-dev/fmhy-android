package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.foundation.rememberScrollState
import io.github.omeg4dev.fmhy.data.link.PasteResolver
import io.github.omeg4dev.fmhy.data.link.PasteLinks
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier

fun parseHighlight(text: String, highlightColor: Color): AnnotatedString {
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
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = highlightColor)) {
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
    LaunchedEffect(userStore?.changes, showBottomSheet) {
        if (showBottomSheet && userStore != null && followState == null) {
            followsList = userStore.follows()
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
            val isFollowing = followsList.any {
                it.sectionId == entry.sectionId && (it.headingId == (entry.headingId ?: "") || it.headingId == "")
            }
            FollowState(categoryTitle = categoryTitle, isFollowing = isFollowing)
        } else null
    }

    val effectiveToggleFollow: (() -> Unit)? = remember(onToggleFollow, effectiveFollowState, userStore, entry.sectionId, entry.headingId) {
        onToggleFollow ?: if (userStore != null && effectiveFollowState != null) {
            {
                scope.launch {
                    val targetHeadingId = entry.headingId ?: ""
                    if (effectiveFollowState.isFollowing) {
                        userStore.unfollow(entry.sectionId, targetHeadingId)
                        onShowSnackbar("Unfollowed ${effectiveFollowState.categoryTitle}")
                    } else {
                        userStore.follow(entry.sectionId, targetHeadingId, effectiveFollowState.categoryTitle)
                        onShowSnackbar("Following ${effectiveFollowState.categoryTitle}")
                    }
                }
            }
        } else null
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val displayName = remember(nameHl, entry.name, primaryColor) {
        if (!nameHl.isNullOrEmpty() && nameHl.contains('\u0002')) {
            parseHighlight(nameHl, primaryColor)
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
        onSurfaceVariant,
    ) {
        val baseDesc = if (!descHl.isNullOrEmpty() && descHl.contains('\u0002')) {
            parseHighlight(descHl, primaryColor)
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            // Tap shows the details (all sites on the line, mirrors, links); hold opens the site directly.
            .combinedClickable(
                onClick = {
                    haptics.click()
                    showBottomSheet = true
                },
                onLongClick = {
                    haptics.longPress()
                    onOpenUrl(entry.url)
                },
                onClickLabel = "Show details",
                onLongClickLabel = "Open site",
            ),
        color = Color.Transparent,
    ) {
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

                if (isNew) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }

                if (alternatives.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .semantics { contentDescription = "${alternatives.size} more on this line" }
                            .clickable {
                                haptics.click()
                                showBottomSheet = true
                            },
                    ) {
                        Text(
                            text = "+${alternatives.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }

                if (entry.mirrors.isNotEmpty()) {
                    // "7Movies, 2, 3" as on fmhy.net: the comma hugs the name, so no row spacing here.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.offset(x = (-6).dp),
                    ) {
                        entry.mirrors.forEach { mirror ->
                            Text(
                                text = ",",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val mirrorIcon = socialIconFor(mirror.label)
                            val mirrorModifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onOpenUrl(mirror.url) }
                                .padding(start = 4.dp, end = 1.dp, top = 2.dp, bottom = 2.dp)
                            if (mirrorIcon != null) {
                                Icon(
                                    painter = painterResource(mirrorIcon),
                                    contentDescription = mirror.label,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = mirrorModifier.size(18.dp),
                                )
                            } else {
                                Text(
                                    text = mirror.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = mirrorModifier
                                        .semantics { contentDescription = "Mirror ${mirror.label}" },
                                )
                            }
                        }
                    }
                }

                if (entry.isBase64) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = "Hidden link, decoded",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }

                val bookmarksState = LocalBookmarks.current
                val isPrimarySaved = entry.id in bookmarksState.savedIds
                val isAnyAlternativeSaved = alternatives.any { it.id in bookmarksState.savedIds }
                if (isPrimarySaved || isAnyAlternativeSaved) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable {
                                if (alternatives.isNotEmpty()) {
                                    showBottomSheet = true
                                } else {
                                    bookmarksState.toggle(entry)
                                    onShowSnackbar("Removed from Saved")
                                }
                            },
                    )
                }

                if (isCrossLink) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }

                if (trailingContent != null) {
                    Spacer(Modifier.weight(1f))
                    trailingContent()
                }
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
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (folderLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = folderLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (!notePreview.isNullOrEmpty()) {
                        Text(
                            text = notePreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    val pasteResolver = LocalPasteResolver.current
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
                scope.launch {
                    val text = resolvedLinkText(url, pasteResolver, onShowSnackbar)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("URL", text))
                    onShowSnackbar(if ('\n' in text) "Links copied to clipboard" else "Link copied to clipboard")
                }
            },
            onShareLink = { url ->
                showBottomSheet = false
                scope.launch {
                    val text = resolvedLinkText(url, pasteResolver, onShowSnackbar)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailBottomSheet(
    entry: Entry,
    alternatives: List<Entry> = emptyList(),
    history: List<ChangeItem>? = null,
    onShowInSection: (() -> Unit)? = null,
    followState: FollowState? = null,
    onToggleFollow: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
    onShowSnackbar: (String) -> Unit = {},
    savedInfo: SavedInfo? = null,
    onEditNote: ((String) -> Unit)? = null,
    onMoveToFolder: ((Long?) -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bookmarksState = LocalBookmarks.current
    val haptics = rememberHaptics()
    val isSaved = entry.id in bookmarksState.savedIds || savedInfo != null
    val currentSavedInfo = savedInfo ?: bookmarksState.savedInfo(entry.id) ?: (if (isSaved) SavedInfo() else null)
    val editNoteAction = onEditNote ?: { note: String -> bookmarksState.setNote(entry.id, note) }
    val moveToFolderAction = onMoveToFolder ?: { folderId: Long? -> bookmarksState.moveToFolder(entry.id, folderId) }

    var showFolderPicker by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteInput by remember(currentSavedInfo?.note) { mutableStateOf(currentSavedInfo?.note.orEmpty()) }
    var newFolderName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            if (alternatives.isEmpty()) {
                val crossLinks = LocalCrossLinks.current
                val host = remember(entry.url, crossLinks) {
                    // A link to another FMHY page opens in the app: say so instead of "reddit.com".
                    val target = crossLinks?.resolve(entry.url)
                    if (target != null) {
                        val heading = crossLinks.headingTitle(target)
                        if (heading != null && heading != entry.name) "Opens in the app · $heading" else "Opens in the app"
                    } else try {
                        Uri.parse(entry.url).host?.removePrefix("www.").orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                }

                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EntryMarker(entry, size = 22.dp, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    if (host.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = host,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (entry.description.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Action row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Open
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                haptics.click()
                                onOpenUrl(entry.url)
                            },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Open", style = MaterialTheme.typography.labelMedium)
                    }

                    // Copy
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                haptics.confirm()
                                onCopyLink(entry.url)
                            },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy link")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Copy", style = MaterialTheme.typography.labelMedium)
                    }

                    // Share
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                haptics.click()
                                onShareLink(entry.url)
                            },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Share", style = MaterialTheme.typography.labelMedium)
                    }

                    // Save / Saved
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                haptics.click()
                                bookmarksState.toggle(entry)
                                onShowSnackbar(if (isSaved) "Removed from Saved" else "Saved")
                                onDismiss()
                            },
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isSaved) "Saved" else "Save",
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(if (isSaved) "Saved" else "Save", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (isSaved && currentSavedInfo != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "My FMHY",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AssistChip(
                                onClick = { showFolderPicker = true },
                                label = { Text(currentSavedInfo.folderName ?: "No folder") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                            )
                        }

                        Spacer(Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    noteInput = currentSavedInfo.note
                                    showNoteDialog = true
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (currentSavedInfo.note.isNotBlank()) currentSavedInfo.note else "Add note…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (currentSavedInfo.note.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                val origUrl = entry.originalUrl
                if (entry.isBase64 && !origUrl.isNullOrEmpty()) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text("Open original (base64)") },
                        supportingContent = { Text(origUrl, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                        modifier = Modifier.clickable { onOpenUrl(origUrl) }
                    )
                }

                if (entry.mirrors.isNotEmpty()) {
                    Text(
                        text = "Mirrors",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    entry.mirrors.forEach { mirror ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("Mirror ${mirror.label}") },
                            supportingContent = { Text(mirror.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable { onOpenUrl(mirror.url) }
                        )
                    }
                }

                if (entry.extraLinks.isNotEmpty()) {
                    Text(
                        text = "Extra Links",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    entry.extraLinks.forEach { extra ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(extra.label) },
                            supportingContent = { Text(extra.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable { onOpenUrl(extra.url) }
                        )
                    }
                }
            } else {
                val allSites = listOf(entry) + alternatives
                allSites.forEachIndexed { index, site ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = if (index == 1) 12.dp else 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }

                    val siteHost = try {
                        Uri.parse(site.url).host?.removePrefix("www.").orEmpty()
                    } catch (_: Exception) {
                        ""
                    }
                    val isSiteSaved = site.id in bookmarksState.savedIds

                    if (index > 0) {
                        // Alternatives on the same line: one compact row, small icon actions.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EntryMarker(site, size = 16.dp, modifier = Modifier.padding(end = 8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = site.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (siteHost.isNotEmpty()) {
                                    Text(
                                        text = siteHost,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    haptics.click()
                                    onOpenUrl(site.url)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open ${site.name}", modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = {
                                    haptics.confirm()
                                    onCopyLink(site.url)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy link", modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = {
                                    haptics.click()
                                    onShareLink(site.url)
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
                            }
                            IconButton(
                                onClick = {
                                    haptics.click()
                                    bookmarksState.toggle(site)
                                    onShowSnackbar(if (isSiteSaved) "Removed from Saved" else "Saved")
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = if (isSiteSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = if (isSiteSaved) "Saved" else "Save",
                                    tint = if (isSiteSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                EntryMarker(site, size = 20.dp, modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    text = site.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                            if (siteHost.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = siteHost,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        // Action row for THIS site
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Open
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        haptics.click()
                                        onOpenUrl(site.url)
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open")
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("Open", style = MaterialTheme.typography.labelSmall)
                            }

                            // Copy
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        haptics.confirm()
                                        onCopyLink(site.url)
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy link")
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                            }

                            // Share
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        haptics.click()
                                        onShareLink(site.url)
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share")
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("Share", style = MaterialTheme.typography.labelSmall)
                            }

                            // Save
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        haptics.click()
                                        bookmarksState.toggle(site)
                                        onShowSnackbar(if (isSiteSaved) "Removed from Saved" else "Saved")
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isSiteSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = if (isSiteSaved) "Saved" else "Save",
                                        tint = if (isSiteSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(if (isSiteSaved) "Saved" else "Save", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    val siteOrigUrl = site.originalUrl
                    if (site.isBase64 && !siteOrigUrl.isNullOrEmpty()) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text("Open original (base64)") },
                            supportingContent = { Text(siteOrigUrl, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                            modifier = Modifier.clickable { onOpenUrl(siteOrigUrl) }
                        )
                    }

                    // Mirrors for this site
                    if (site.mirrors.isNotEmpty()) {
                        Text(
                            text = "Mirrors",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                        site.mirrors.forEach { mirror ->
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Mirror ${mirror.label}") },
                                supportingContent = { Text(mirror.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.clickable { onOpenUrl(mirror.url) }
                            )
                        }
                    }
                }

                // Shared description / extra links below once
                val sharedDesc = entry.description.ifEmpty {
                    alternatives.firstOrNull { it.description.isNotEmpty() }?.description.orEmpty()
                }
                if (sharedDesc.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = sharedDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }

                // Every site's own links (e.g. uBO Lite's Note), each once; an alternative's are tagged with its name.
                val sharedExtras = allSites
                    .flatMap { site -> site.extraLinks.map { site to it } }
                    .distinctBy { (_, link) -> link.url }
                if (sharedExtras.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Extra Links",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    sharedExtras.forEach { (site, extra) ->
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(if (site === entry) extra.label else "${extra.label} · ${site.name}") },
                            supportingContent = { Text(extra.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.clickable { onOpenUrl(extra.url) }
                        )
                    }
                }
            }

            if (onShowInSection != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Segment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = {
                        Text("Show in section", style = MaterialTheme.typography.titleSmall)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onShowInSection()
                        }
                        .padding(horizontal = 8.dp),
                )
            }

            if (followState != null && onToggleFollow != null) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        Icon(
                            imageVector = if (followState.isFollowing) Icons.Default.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = if (followState.isFollowing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    headlineContent = {
                        Text(
                            text = if (followState.isFollowing) "Following ${followState.categoryTitle}" else "Follow ${followState.categoryTitle}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                    trailingContent = {
                        if (followState.isFollowing) {
                            FilledTonalButton(onClick = onToggleFollow) {
                                Text("Following")
                            }
                        } else {
                            Button(onClick = onToggleFollow) {
                                Text("Follow")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleFollow() }
                        .padding(horizontal = 8.dp),
                )
            }

            if (!history.isNullOrEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = "History",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
                history.forEach { item ->
                    val dateStr = DateFormatters.formatHistoryDate(item.ts)
                    when (item.kind) {
                        ChangeKind.ADDED -> {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Added $dateStr") },
                            )
                        }
                        ChangeKind.STARRED -> {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Starred $dateStr") },
                            )
                        }
                        ChangeKind.UNSTARRED -> {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Unstarred $dateStr") },
                            )
                        }
                        ChangeKind.EDITED -> {
                            var isExpanded by rememberSaveable { mutableStateOf(false) }
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = {
                                    Text(
                                        text = if (item.detail.isNotEmpty()) "Changed $dateStr — was: ${item.detail}" else "Changed $dateStr",
                                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.clickable { isExpanded = !isExpanded },
                            )
                        }
                        ChangeKind.URL_CHANGED -> {
                            val host = try { Uri.parse(item.url).host?.removePrefix("www.") ?: item.url } catch (_: Exception) { item.url }
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("New link: $host · $dateStr") },
                            )
                        }
                        ChangeKind.REMOVED -> {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Removed $dateStr") },
                            )
                        }
                        ChangeKind.RENAMED -> {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Renamed $dateStr") },
                            )
                        }
                        ChangeKind.MOVED -> {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text("Moved $dateStr") },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFolderPicker) {
        AlertDialog(
            onDismissRequest = { showFolderPicker = false },
            title = { Text("Move to folder") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    ListItem(
                        headlineContent = { Text("No folder") },
                        modifier = Modifier.clickable {
                            moveToFolderAction(null)
                            showFolderPicker = false
                        }
                    )
                    bookmarksState.folders.forEach { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            modifier = Modifier.clickable {
                                moveToFolderAction(folder.id)
                                showFolderPicker = false
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ListItem(
                        headlineContent = { Text("+ New folder…", color = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            showFolderPicker = false
                            newFolderName = ""
                            showNewFolderDialog = true
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFolderPicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = newFolderName.trim()
                        if (trimmed.isNotEmpty()) {
                            bookmarksState.createFolder(trimmed) { createdFolder ->
                                moveToFolderAction(createdFolder.id)
                            }
                        }
                        showNewFolderDialog = false
                    },
                    enabled = newFolderName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("Note") },
            text = {
                Column {
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = {
                            if (it.length <= 500) {
                                noteInput = it
                            }
                        },
                        label = { Text("Note (max 500 chars)") },
                        supportingText = {
                            Text(
                                text = "${noteInput.length} / 500",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        },
                        maxLines = 5,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        editNoteAction(noteInput.trim())
                        showNoteDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/** The link to copy or share: a pastebin-hidden link is decoded first (several links → one per line). */
private suspend fun resolvedLinkText(
    url: String,
    resolver: PasteResolver?,
    onShowSnackbar: (String) -> Unit,
): String {
    if (resolver == null || PasteLinks.rawUrl(url) == null) return url
    onShowSnackbar("Decoding hidden link…")
    return resolver.resolve(url)?.joinToString("\n") ?: url
}
