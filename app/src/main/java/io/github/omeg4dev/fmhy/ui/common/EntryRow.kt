package io.github.omeg4dev.fmhy.ui.common

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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    nameHl: String? = null,
    descHl: String? = null,
    onOpenUrl: (String) -> Unit,
    onShowSnackbar: (String) -> Unit = {},
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

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
            .combinedClickable(
                onClick = { onOpenUrl(entry.url) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showBottomSheet = true
                },
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

                entry.mirrors.forEach { mirror ->
                    Text(
                        text = ",",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = mirror.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onOpenUrl(mirror.url) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .semantics { contentDescription = "Mirror ${mirror.label}" },
                    )
                }

                if (entry.isBase64) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = "Hidden link, decoded",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }

                if (entry.id in LocalBookmarks.current.savedIds) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = "Saved",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
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
        }
    }

    if (showBottomSheet) {
        val context = LocalContext.current
        EntryDetailBottomSheet(
            entry = entry,
            onDismiss = { showBottomSheet = false },
            onOpenUrl = { url ->
                showBottomSheet = false
                onOpenUrl(url)
            },
            onCopyLink = { url ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                onShowSnackbar("Link copied to clipboard")
                showBottomSheet = false
            },
            onShareLink = { url ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                context.startActivity(Intent.createChooser(intent, "Share ${entry.name}"))
                showBottomSheet = false
            },
            onShowSnackbar = onShowSnackbar,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailBottomSheet(
    entry: Entry,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopyLink: (String) -> Unit,
    onShareLink: (String) -> Unit,
    onShowSnackbar: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bookmarksState = LocalBookmarks.current
    val isSaved = entry.id in bookmarksState.savedIds

    val host = remember(entry.url) {
        try {
            Uri.parse(entry.url).host?.removePrefix("www.").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleLarge,
                )
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
                        onClick = { onOpenUrl(entry.url) },
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
                        onClick = { onCopyLink(entry.url) },
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
                        onClick = { onShareLink(entry.url) },
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
        }
    }
}
