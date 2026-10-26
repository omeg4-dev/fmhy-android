package io.github.omeg4dev.fmhy.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import io.github.omeg4dev.fmhy.browser.download.ActiveSummary
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.data.BookmarksRepository
import io.github.omeg4dev.fmhy.data.ToolbarPosition
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun BrowserToolbar(
    tab: TabState?,
    tabs: List<TabState> = emptyList(),
    tabCount: Int = tabs.size,
    toolbarPosition: ToolbarPosition = ToolbarPosition.TOP,
    isFindMode: Boolean = false,
    findQuery: String = "",
    findActiveIndex: Int = 0,
    findTotalMatches: Int = 0,
    showBlockedCount: Boolean = true,
    isSaved: Boolean = false,
    onShieldClick: () -> Unit = {},
    onAddressClick: () -> Unit = {},
    onTabsClick: () -> Unit = {},
    onReloadClick: () -> Unit = {},
    onStopClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onForwardClick: () -> Unit = {},
    onBackToFmhyClick: () -> Unit = {},
    onNewTabClick: (isPrivate: Boolean) -> Unit = {},
    onFindInPageClick: () -> Unit = {},
    onFindQueryChange: (String) -> Unit = {},
    onFindNext: () -> Unit = {},
    onFindPrevious: () -> Unit = {},
    onFindClose: () -> Unit = {},
    onToggleDesktop: () -> Unit = {},
    onSetZoom: (Float) -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    onSelectTab: (Long) -> Unit = {},
    onCopyLink: () -> Unit = {},
    onReportProblem: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    downloadSummary: ActiveSummary? = null,
    onDownloadsRingClick: () -> Unit = {},
    showVideoButton: Boolean = false,
    onVideoClick: () -> Unit = {},
    /** FMHY status icon (drawn at the start of the address pill); empty when the tab has no state. */
    statusIcon: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var showMenuSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // Inner edge hairline for BOTTOM position (at the top of the toolbar)
        if (toolbarPosition == ToolbarPosition.BOTTOM) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        if (isFindMode) {
            FindInPageBar(
                query = findQuery,
                activeIndex = findActiveIndex,
                totalMatches = findTotalMatches,
                onQueryChange = onFindQueryChange,
                onNext = onFindNext,
                onPrevious = onFindPrevious,
                onClose = onFindClose,
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp) // 56 dp + 4 dp vertical padding each side
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back to FMHY (48 dp target, always enabled): the FMHY logo, never an arrow, so it can't be mistaken for web back.
                IconButton(onClick = onBackToFmhyClick, modifier = Modifier.size(48.dp)) {
                    Image(
                        painter = painterResource(R.drawable.fmhy_logo),
                        contentDescription = "Back to FMHY",
                        modifier = Modifier.size(28.dp).clip(CircleShape),
                    )
                }

                // Forward: only when there is a page to go forward to; slides/fades so the address pill resizes smoothly.
                AnimatedVisibility(
                    visible = tab?.canGoForward == true,
                    enter = fadeIn(Motion.standard()) + expandHorizontally(Motion.standard()),
                    exit = fadeOut(Motion.standard()) + shrinkHorizontally(Motion.standard()),
                ) {
                    IconButton(onClick = onForwardClick, modifier = Modifier.size(48.dp)) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }
                }

                // Address pill (weight 1, swipe tabs, progress line)
                AddressPill(
                    tab = tab,
                    tabs = tabs,
                    showBlockedCount = showBlockedCount,
                    downloadSummary = downloadSummary,
                    onDownloadsRingClick = onDownloadsRingClick,
                    showVideoButton = showVideoButton,
                    onVideoClick = onVideoClick,
                    onAddressClick = onAddressClick,
                    onShieldClick = onShieldClick,
                    onSelectTab = onSelectTab,
                    statusIcon = statusIcon,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Tabs counter: 48 dp clickable box with 26 dp rounded square (8 dp corner) outline 2 dp onSurface
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(onClick = {
                            onTabsClick()
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .border(
                                BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
                                RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tabCount.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Menu button (48 dp target)
                IconButton(
                    onClick = {
                        showMenuSheet = true
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
            }
        }

        // Inner edge hairline for TOP position (at the bottom of the toolbar)
        if (toolbarPosition == ToolbarPosition.TOP) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }

    if (showMenuSheet) {
        val isBlank = tab == null || tab.url.isEmpty() || tab.url == "about:blank"
        BrowserMenuSheet(
            tab = tab,
            isSaved = isSaved,
            hasPage = !isBlank,
            onDismiss = { showMenuSheet = false },
            onDownloads = {
                showMenuSheet = false
                onDownloadsClick()
            },
            onBackToFmhy = {
                showMenuSheet = false
                onBackToFmhyClick()
            },
            onBack = {
                showMenuSheet = false
                onBackClick()
            },
            onForward = {
                showMenuSheet = false
                onForwardClick()
            },
            onReload = {
                showMenuSheet = false
                onReloadClick()
            },
            onStop = {
                showMenuSheet = false
                onStopClick()
            },
            onShare = {
                showMenuSheet = false
                val url = tab?.url.orEmpty()
                if (url.isNotEmpty() && url != "about:blank") {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_TEXT, url)
                        type = "text/plain"
                    }
                    val chooser = Intent.createChooser(sendIntent, null).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    // handled by caller or startActivity
                }
            },
            onSave = {
                showMenuSheet = false
                onToggleBookmark()
            },
            onNewTab = {
                showMenuSheet = false
                onNewTabClick(false)
            },
            onNewPrivateTab = {
                showMenuSheet = false
                onNewTabClick(true)
            },
            onFindInPage = {
                showMenuSheet = false
                onFindInPageClick()
            },
            onToggleDesktop = {
                showMenuSheet = false
                onToggleDesktop()
            },
            onSetZoom = onSetZoom,
            onSetPlaybackSpeed = { speed ->
                showMenuSheet = false
                onSetPlaybackSpeed(speed)
            },
            onCopyLink = {
                showMenuSheet = false
                onCopyLink()
            },
            onOpenInBrowser = {
                showMenuSheet = false
                // caller handles external open
            },
            onSiteSettings = {
                showMenuSheet = false
                onShieldClick()
            },
            onReportProblem = {
                showMenuSheet = false
                onReportProblem()
            },
        )
    }
}

@Composable
private fun AddressPill(
    tab: TabState?,
    tabs: List<TabState>,
    showBlockedCount: Boolean,
    downloadSummary: ActiveSummary? = null,
    onDownloadsRingClick: () -> Unit = {},
    showVideoButton: Boolean = false,
    onVideoClick: () -> Unit = {},
    onAddressClick: () -> Unit,
    onShieldClick: () -> Unit,
    onSelectTab: (Long) -> Unit,
    statusIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val url = tab?.url.orEmpty()
    val isBlank = tab == null || url.isEmpty() || url == "about:blank"
    val isHttp = url.startsWith("http://", ignoreCase = true)

    val host = try {
        if (isBlank) "Search or type link"
        else Uri.parse(url).host ?: url
    } catch (_: Exception) {
        url
    }

    var pillWidthPx by remember { mutableFloatStateOf(0f) }
    val dragOffset = remember { Animatable(0f) }

    val currentIndex = tabs.indexOfFirst { it.id == tab?.id }
    val prevTab = if (currentIndex > 0) tabs[currentIndex - 1] else null
    val nextTab = if (currentIndex in 0 until tabs.size - 1) tabs[currentIndex + 1] else null

    val animatedProgress by animateFloatAsState(
        targetValue = if (tab?.isLoading == true) tab.progress.coerceIn(0.05f, 1f) else 0f,
        animationSpec = Motion.standard(),
        label = "pillProgress",
    )

    val pillShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(pillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .onSizeChanged { pillWidthPx = it.width.toFloat() }
            .pointerInput(tab?.id, tabs) {
                detectTapGestures(onTap = { onAddressClick() })
            }
            .pointerInput(tab?.id, tabs) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        scope.launch {
                            dragOffset.snapTo(dragOffset.value + dragAmount)
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            val threshold = pillWidthPx * 0.3f
                            val current = dragOffset.value
                            if (current > threshold && prevTab != null) {
                                onSelectTab(prevTab.id)
                            } else if (current < -threshold && nextTab != null) {
                                onSelectTab(nextTab.id)
                            }
                            dragOffset.animateTo(0f, Motion.standard())
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragOffset.animateTo(0f, Motion.standard())
                        }
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (tab?.siteBarState != null) 4.dp else 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // FMHY status icon (32 dp touch box, 18 dp glyph) with its own tap popover
            if (!isBlank) {
                statusIcon()
                if (tab?.siteBarState != null) Spacer(modifier = Modifier.width(4.dp))
            }

            // Leading 20 dp site favicon; a blocked page gets a neutral warning, never a site's icon
            if (isBlank) {
                if (tab?.isPrivate == true) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (tab?.interstitialState != null || tab?.showsBlockPage == true) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (tab?.favicon != null && !tab.isPrivate) {
                Image(
                    bitmap = tab.favicon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape),
                )
            } else if (tab?.isPrivate == true) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else if (tab?.siteBarState == null) {
                // Placeholder only when there's no status icon, so the pill stays uncluttered
                Icon(
                    imageVector = Icons.Outlined.Public,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Only flag insecure pages; HTTPS is the normal case and needs no lock
            if (isHttp && url.isNotEmpty() && url != "about:blank") {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Not secure",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Host text with horizontal swipe translation
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds()
            ) {
                Text(
                    text = host,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(dragOffset.value.roundToInt(), 0) },
                )

                // Neighbor tab host sliding in during drag
                if (dragOffset.value > 0 && prevTab != null && pillWidthPx > 0) {
                    val prevHost = try {
                        if (prevTab.url == "about:blank" || prevTab.url.isEmpty()) "New tab"
                        else Uri.parse(prevTab.url).host ?: prevTab.url
                    } catch (_: Exception) {
                        prevTab.url
                    }
                    Text(
                        text = prevHost,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset((dragOffset.value - pillWidthPx).roundToInt(), 0) },
                    )
                } else if (dragOffset.value < 0 && nextTab != null && pillWidthPx > 0) {
                    val nextHost = try {
                        if (nextTab.url == "about:blank" || nextTab.url.isEmpty()) "New tab"
                        else Uri.parse(nextTab.url).host ?: nextTab.url
                    } catch (_: Exception) {
                        nextTab.url
                    }
                    Text(
                        text = nextHost,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset((dragOffset.value + pillWidthPx).roundToInt(), 0) },
                    )
                }
            }

            AnimatedVisibility(
                visible = showVideoButton && !isBlank,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onVideoClick)
                        .semantics { contentDescription = "Download video" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(
                visible = downloadSummary != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                val fraction = downloadSummary?.fraction
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = onDownloadsRingClick)
                        .semantics { contentDescription = "Downloads" },
                    contentAlignment = Alignment.Center,
                ) {
                    if (fraction != null) {
                        CircularProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            // Shield with blocked count badge INSIDE the pill on the right
            if (!isBlank) {
                val isBlockingOn = tab?.sitePrefs?.adblock ?: true
                val shieldIcon = if (isBlockingOn) Icons.Default.Shield else Icons.Default.RemoveModerator

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(onClick = {
                            onShieldClick()
                        }),
                    contentAlignment = Alignment.Center,
                ) {
                    if (showBlockedCount && tab != null && tab.blocked > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    Text(tab.blocked.toString())
                                }
                            }
                        ) {
                            Icon(
                                imageVector = shieldIcon,
                                contentDescription = "Shield",
                                modifier = Modifier.size(20.dp),
                                tint = if (isBlockingOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Icon(
                            imageVector = shieldIcon,
                            contentDescription = "Shield",
                            modifier = Modifier.size(20.dp),
                            tint = if (isBlockingOn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        // Progress line (2 dp line along the pill's bottom inner edge)
        if (tab?.isLoading == true) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(animatedProgress)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun FindInPageBar(
    query: String,
    activeIndex: Int,
    totalMatches: Int,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pill shaped search input
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.width(8.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Find in page",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                },
            )

            if (query.isNotEmpty()) {
                val matchText = if (totalMatches > 0) {
                    "${activeIndex + 1}/$totalMatches"
                } else {
                    "No matches"
                }
                val matchColor = if (totalMatches > 0) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                }
                Text(
                    text = matchText,
                    style = MaterialTheme.typography.bodySmall,
                    color = matchColor,
                )
            }
        }

        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuSheet(
    tab: TabState?,
    isSaved: Boolean,
    hasPage: Boolean = true,
    onDismiss: () -> Unit,
    onDownloads: () -> Unit,
    onBackToFmhy: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onFindInPage: () -> Unit,
    onToggleDesktop: () -> Unit,
    onSetZoom: (Float) -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit,
    onCopyLink: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onSiteSettings: () -> Unit,
    onReportProblem: () -> Unit = {},
) {
    val haptics = rememberHaptics()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            MenuActionRow(
                title = "Back to FMHY",
                onClick = onBackToFmhy,
                leading = {
                    Image(
                        painter = painterResource(R.drawable.fmhy_logo),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).clip(CircleShape),
                    )
                },
            )

            // Top row: 5 icon buttons (Back, Forward, Reload/Stop, Share, Save/Saved)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back
                MenuIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "Back",
                    enabled = tab?.canGoBack == true,
                    onClick = {
                        onBack()
                    },
                )

                // Forward
                MenuIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    label = "Forward",
                    enabled = tab?.canGoForward == true,
                    onClick = {
                        onForward()
                    },
                )

                // Reload or Stop
                if (tab?.isLoading == true) {
                    MenuIconButton(
                        icon = Icons.Default.Close,
                        label = "Stop",
                        enabled = hasPage,
                        onClick = {
                            onStop()
                        },
                    )
                } else {
                    MenuIconButton(
                        icon = Icons.Default.Refresh,
                        label = "Reload",
                        enabled = hasPage,
                        onClick = {
                            onReload()
                        },
                    )
                }

                // Share
                MenuIconButton(
                    icon = Icons.Default.Share,
                    label = "Share",
                    enabled = hasPage,
                    onClick = {
                        onShare()
                    },
                )

                // Save / Saved
                MenuIconButton(
                    icon = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    label = if (isSaved) "Saved" else "Save",
                    enabled = hasPage,
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    onClick = {
                        if (isSaved) haptics.remove() else haptics.success()
                        onSave()
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Action rows (icon + text)
            MenuActionRow(
                icon = Icons.Default.Add,
                title = "New tab",
                onClick = {
                    onNewTab()
                },
            )

            MenuActionRow(
                icon = Icons.Default.Security,
                title = "New private tab",
                onClick = {
                    onNewPrivateTab()
                },
            )

            MenuActionRow(
                icon = Icons.Default.Search,
                title = "Find in page",
                enabled = hasPage,
                onClick = {
                    onFindInPage()
                },
            )

            MenuActionRow(
                icon = Icons.Default.Download,
                title = "Downloads",
                onClick = {
                    onDownloads()
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = hasPage) {
                        haptics.toggle(tab?.desktopSite != true)
                        onToggleDesktop()
                    }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DesktopWindows,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (hasPage) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Desktop site",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (hasPage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }

                Switch(
                    checked = tab?.desktopSite == true,
                    enabled = hasPage,
                    onCheckedChange = {
                        haptics.toggle(!it)
                        onToggleDesktop()
                    },
                )
            }

            if (hasPage) {
                val zoomLevel = tab?.zoomLevel ?: 1f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Zoom",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ZoomLogic.showReset(zoomLevel)) {
                            TextButton(
                                onClick = {
                                    haptics.tick()
                                    onSetZoom(1.0f)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Text(text = "Reset", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        IconButton(
                            onClick = {
                                haptics.tick()
                                onSetZoom(ZoomLogic.stepDown(zoomLevel))
                            },
                            enabled = zoomLevel > ZoomLogic.MIN,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Text(
                                text = "−",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (zoomLevel > ZoomLogic.MIN) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        }
                        Text(
                            text = ZoomLogic.label(zoomLevel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        IconButton(
                            onClick = {
                                haptics.tick()
                                onSetZoom(ZoomLogic.stepUp(zoomLevel))
                            },
                            enabled = zoomLevel < ZoomLogic.MAX,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (zoomLevel < ZoomLogic.MAX) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                        }
                    }
                }
            }

            if (tab?.hasMedia == true) {
                PlaybackSpeedRow(
                    current = tab.playbackSpeed,
                    onSelect = { speed ->
                        onSetPlaybackSpeed(speed)
                    },
                )
            }

            MenuActionRow(
                icon = Icons.Default.ContentCopy,
                title = "Copy link",
                enabled = hasPage,
                onClick = {
                    onCopyLink()
                },
            )

            MenuActionRow(
                icon = Icons.Default.OpenInBrowser,
                title = "Open in another browser",
                enabled = hasPage,
                onClick = {
                    onOpenInBrowser()
                },
            )

            MenuActionRow(
                icon = Icons.Default.Shield,
                title = "Site settings",
                enabled = hasPage,
                onClick = {
                    onSiteSettings()
                },
            )

            if (hasPage) {
                MenuActionRow(
                    icon = Icons.Default.BugReport,
                    title = "Report a problem with this page",
                    enabled = true,
                    onClick = {
                        onReportProblem()
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaybackSpeedRow(
    current: Float,
    onSelect: (Float) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable { open = true }
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Playback speed",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = speedLabel(current),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PLAYBACK_SPEEDS.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = speedLabel(speed),
                            color = if (speed == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(speed)
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuActionRow(
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun MenuActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    MenuActionRow(
        title = title,
        enabled = enabled,
        onClick = onClick,
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        },
    )
}

fun openInExternalBrowser(context: Context, url: String) {
    val uri = Uri.parse(url)
    val intent = Intent(Intent.ACTION_VIEW, uri)
    val pm = context.packageManager
    val resolveInfos = pm.queryIntentActivities(intent, 0)
    val targetIntents = mutableListOf<Intent>()
    for (info in resolveInfos) {
        val packageName = info.activityInfo.packageName
        if (packageName != context.packageName) {
            val target = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
            }
            targetIntents.add(target)
        }
    }
    if (targetIntents.isNotEmpty()) {
        val chooser = Intent.createChooser(targetIntents.removeAt(0), "Open with").apply {
            if (targetIntents.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.toTypedArray())
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } else {
        try {
            val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {}
    }
}