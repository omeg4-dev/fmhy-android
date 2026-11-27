package io.github.omeg4dev.fmhy.ui.common

import java.time.LocalDate
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.github.omeg4dev.fmhy.data.model.ChangeItem
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier

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

/** Splits an entry's heading path ("Section › Category › Sub"); compiled once, rows use it while scrolling. */
val HEADING_PATH_SEPARATOR = Regex("""\s*[›>]\s*""")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryRow(
    entry: Entry,
    modifier: Modifier = Modifier,
    alternatives: List<Entry> = emptyList(),
    nameHl: String? = null,
    descHl: String? = null,
    newAddedOn: LocalDate? = null,
    updateLine: String? = null,
    history: List<ChangeItem>? = null,
    onShowInSection: (() -> Unit)? = null,
    siteAction: RowAction = RowAction.OPEN_SITE,
    followState: FollowState? = null,
    onToggleFollow: (() -> Unit)? = null,
    isPinned: Boolean? = null,
    onTogglePin: (() -> Unit)? = null,
    onOpenUrl: (String) -> Unit,
    onShowSnackbar: (String) -> Unit = {},
    folderLabel: String? = null,
    notePreview: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val saveActions = rememberSaveActions(onShowSnackbar = onShowSnackbar)

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
                appendInlineContent("platform:${platform.name}", alternateText = inlineAltText(word, "Platform"))
                cursor = range.last + 1
            }
            if (cursor < baseDesc.length) {
                append(baseDesc.subSequence(cursor, baseDesc.length))
            }

            val (socialLinks, otherLinks) = entry.extraLinks.withIndex().partition {
                linkIconFor(it.value) != null
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
                appendInlineContent("link:$index", alternateText = inlineAltText(link.label, "Link"))
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
            val iconRes = linkIconFor(link) ?: continue
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
                        tint = linkIconTint(link.url),
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
    val effectiveSiteAction = if (siteAction == RowAction.SHOW_IN_SECTION && onShowInSection == null) RowAction.OPEN_SITE else siteAction
    val gestures = rowGestures(swapTapHold, effectiveSiteAction)

    fun performRowAction(action: RowAction) {
        when (action) {
            RowAction.OPEN_SITE -> onOpenUrl(entry.url)
            RowAction.OPEN_MENU -> showBottomSheet = true
            RowAction.SHOW_IN_SECTION -> onShowInSection?.invoke()
        }
    }

    fun labelOf(action: RowAction): String = when (action) {
        RowAction.OPEN_SITE -> "Open site"
        RowAction.OPEN_MENU -> "Show details"
        RowAction.SHOW_IN_SECTION -> "Show in section"
    }

    val altActionLabel = labelOf(gestures.onHold)
    val altAction: () -> Boolean = {
        performRowAction(gestures.onHold)
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
                    performRowAction(gestures.onTap)
                },
                onLongClick = {
                    haptics.longPress()
                    performRowAction(gestures.onHold)
                },
                onClickLabel = labelOf(gestures.onTap),
                onLongClickLabel = labelOf(gestures.onHold),
            ),
        color = Color.Transparent,
    ) {
        if (websiteStyle) {
            WebsiteEntryContent(
                entry = entry,
                displayName = displayName,
                descriptionText = if (!descHl.isNullOrEmpty() && descHl.contains('\u0002')) descHl else entry.description,
                alternatives = alternatives,
                newAddedOn = newAddedOn,
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
                        newAddedOn = newAddedOn,
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

    EntryDetailSheetHost(
        entry = entry,
        visible = showBottomSheet,
        onDismiss = { showBottomSheet = false },
        onOpenUrl = onOpenUrl,
        alternatives = alternatives,
        history = history,
        onShowInSection = onShowInSection,
        followState = followState,
        onToggleFollow = onToggleFollow,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onShowSnackbar = onShowSnackbar,
    )
}
