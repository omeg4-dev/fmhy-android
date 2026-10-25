package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Tier

@Composable
internal fun WebsiteEntryContent(
    entry: Entry,
    displayName: AnnotatedString,
    descriptionText: String,
    alternatives: List<Entry>,
    isNew: Boolean,
    isCrossLink: Boolean,
    updateLine: String?,
    folderLabel: String?,
    notePreview: String?,
    trailingContent: (@Composable () -> Unit)?,
    onOpenUrl: (String) -> Unit,
    onShowBottomSheet: () -> Unit,
    saveActions: SaveActions,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer

    val bookmarksState = LocalBookmarks.current
    val isSaved = entry.id in bookmarksState.savedIds || alternatives.any { it.id in bookmarksState.savedIds }

    val (annotated, inlineMap) = remember(
        entry.id,
        entry.name,
        entry.tier,
        entry.kind,
        entry.isBase64,
        entry.mirrors,
        entry.extraLinks,
        displayName,
        descriptionText,
        alternatives,
        isNew,
        isCrossLink,
        isSaved,
        primaryColor,
        onSurface,
        onSurfaceVariant,
        primaryContainer,
        onPrimaryContainer,
        secondaryContainer,
        onSecondaryContainer,
        onOpenUrl,
        onShowBottomSheet,
        saveActions,
    ) {
        val inlineMap = mutableMapOf<String, InlineTextContent>()

        // Pre-build platform inline contents
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

        // Pre-build badges inline contents
        if (isNew) {
            inlineMap["badge:new"] = InlineTextContent(
                Placeholder(2.6.em, 1.3.em, PlaceholderVerticalAlign.TextCenter)
            ) {
                Surface(
                    shape = CircleShape,
                    color = primaryContainer,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = onSurface,
                        )
                    }
                }
            }
        }

        if (alternatives.isNotEmpty()) {
            val digits = alternatives.size.toString().length
            inlineMap["badge:alternatives"] = InlineTextContent(
                Placeholder((1.4f + 0.6f * digits).em, 1.3.em, PlaceholderVerticalAlign.TextCenter)
            ) {
                Surface(
                    shape = CircleShape,
                    color = secondaryContainer,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onShowBottomSheet() }
                        .semantics { contentDescription = "${alternatives.size} more on this line" },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+${alternatives.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = onSecondaryContainer,
                        )
                    }
                }
            }
        }

        if (entry.isBase64) {
            inlineMap["badge:base64"] = InlineTextContent(
                Placeholder(0.9.em, 0.9.em, PlaceholderVerticalAlign.TextCenter)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = "Hidden link, decoded",
                    tint = onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (isSaved) {
            inlineMap["badge:saved"] = InlineTextContent(
                Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)
            ) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = "Saved",
                    tint = primaryColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            if (alternatives.isNotEmpty()) {
                                onShowBottomSheet()
                            } else {
                                saveActions.unsave(entry)
                            }
                        },
                )
            }
        }

        if (isCrossLink) {
            inlineMap["badge:crosslink"] = InlineTextContent(
                Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val segments = websiteSegments(
            name = entry.name,
            description = descriptionText,
            mirrors = entry.mirrors,
            extraLinks = entry.extraLinks,
        )

        val firstDashIndex = segments.indexOfFirst { it is WebSegment.Separator && it.text == " - " }
        val markerInsertIndex = if (firstDashIndex == -1) segments.size else firstDashIndex

        val annotatedString = buildAnnotatedString {
            // Before the name: tier & kind markers
            val markers = buildList {
                if (entry.tier == Tier.TOP) {
                    add(R.drawable.ic_marker_top to "Top pick")
                } else if (entry.tier == Tier.STAR) {
                    add(R.drawable.ic_marker_star to "Recommended")
                }
                if (entry.kind == EntryKind.INDEX) {
                    add(R.drawable.ic_marker_index to "Index")
                } else if (entry.kind == EntryKind.SECTION_LINK) {
                    add(R.drawable.ic_marker_section_link to "Link to another section")
                }
            }
            for ((idx, marker) in markers.withIndex()) {
                val key = "marker:$idx"
                inlineMap[key] = InlineTextContent(
                    Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)
                ) {
                    Image(
                        painter = painterResource(marker.first),
                        contentDescription = marker.second,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                appendInlineContent(key, alternateText = marker.second)
                append(" ")
            }

            fun emitMarkers() {
                if (isNew) {
                    append(" ")
                    appendInlineContent("badge:new", alternateText = "NEW")
                }
                if (alternatives.isNotEmpty()) {
                    append(" ")
                    appendInlineContent("badge:alternatives", alternateText = "+${alternatives.size}")
                }
                if (entry.isBase64) {
                    append(" ")
                    appendInlineContent("badge:base64", alternateText = "Hidden link, decoded")
                }
                if (isSaved) {
                    append(" ")
                    appendInlineContent("badge:saved", alternateText = "Saved")
                }
                if (isCrossLink) {
                    append(" ")
                    appendInlineContent("badge:crosslink", alternateText = "")
                }
            }

            var mirrorIndex = 0
            for (idx in 0 until segments.size) {
                if (idx == markerInsertIndex) {
                    emitMarkers()
                }
                when (val seg = segments[idx]) {
                    is WebSegment.Name -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = primaryColor)) {
                            append(displayName)
                        }
                    }
                    is WebSegment.Mirror -> {
                        val iconRes = socialIconFor(seg.label)
                        if (iconRes != null) {
                            val key = "mirror:$mirrorIndex"
                            inlineMap[key] = InlineTextContent(
                                Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.TextCenter)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .clickable { onOpenUrl(seg.url) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(iconRes),
                                        contentDescription = seg.label,
                                        tint = primaryColor,
                                        modifier = Modifier.fillMaxSize(0.8f),
                                    )
                                }
                            }
                            appendInlineContent(key, alternateText = seg.label)
                        } else {
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = seg.url,
                                    styles = TextLinkStyles(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold)),
                                    linkInteractionListener = { onOpenUrl(seg.url) }
                                )
                            ) {
                                append(seg.label)
                            }
                        }
                        mirrorIndex++
                    }
                    is WebSegment.Separator -> {
                        withStyle(SpanStyle(color = onSurfaceVariant)) {
                            append(seg.text)
                        }
                    }
                    is WebSegment.Description -> {
                        val baseDesc = parseHighlight(seg.text, highlightColor = onPrimaryContainer, background = primaryContainer)
                        val platformSpans = findPlatformSpans(baseDesc.text)
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
                    }
                    is WebSegment.Link -> {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = seg.url,
                                styles = TextLinkStyles(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)),
                                linkInteractionListener = { onOpenUrl(seg.url) }
                            )
                        ) {
                            append(seg.label)
                        }
                    }
                    is WebSegment.Social -> {
                        val iconRes = socialIconFor(seg.label)
                        if (iconRes != null) {
                            val key = "link:${seg.index}"
                            inlineMap[key] = InlineTextContent(
                                Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.TextCenter)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .clickable { onOpenUrl(seg.url) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(iconRes),
                                        contentDescription = seg.label,
                                        tint = primaryColor,
                                        modifier = Modifier.fillMaxSize(0.8f),
                                    )
                                }
                            }
                        }
                        appendInlineContent("link:${seg.index}", alternateText = seg.label)
                    }
                }
            }

            if (markerInsertIndex == segments.size) {
                emitMarkers()
            }

            if (descriptionText.isBlank() && isCrossLink) {
                withStyle(SpanStyle(color = onSurfaceVariant)) {
                    if (segments.none { it is WebSegment.Separator && it.text == " - " }) {
                        append(" - ")
                    }
                    append("Opens in the app")
                }
            }
        }

        annotatedString to inlineMap
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "•",
                style = MaterialTheme.typography.bodyLarge,
                color = onSurfaceVariant,
                modifier = Modifier.width(14.dp),
            )
            Text(
                text = annotated,
                style = MaterialTheme.typography.bodyLarge,
                color = onSurface,
                inlineContent = inlineMap,
                modifier = Modifier.weight(1f),
            )
            if (trailingContent != null) {
                trailingContent()
            }
        }

        if (!updateLine.isNullOrEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = updateLine,
                style = MaterialTheme.typography.labelMedium,
                color = primaryColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 14.dp),
            )
        }

        if (folderLabel != null || !notePreview.isNullOrEmpty()) {
            EntryFolderNoteMetadata(
                folderLabel = folderLabel,
                notePreview = notePreview,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}
