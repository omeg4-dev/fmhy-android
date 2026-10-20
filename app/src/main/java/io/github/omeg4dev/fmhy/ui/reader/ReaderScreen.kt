package io.github.omeg4dev.fmhy.ui.reader

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.doc.DocResult
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.ui.common.MarkdownBlocks
import io.github.omeg4dev.fmhy.ui.common.ReaderStyle
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.School
import androidx.compose.ui.text.style.TextAlign
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.rememberCoroutineScope
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.ui.common.EntryDetailBottomSheet
import io.github.omeg4dev.fmhy.ui.posts.PostBody
import io.github.omeg4dev.fmhy.ui.posts.PostHeader
import io.github.omeg4dev.fmhy.ui.posts.PostSiteRow
import io.github.omeg4dev.fmhy.ui.posts.PostsListing
import io.github.omeg4dev.fmhy.ui.posts.PostsReadState
import kotlinx.coroutines.launch

fun readerStyleForPath(path: String): ReaderStyle {
    val clean = path.trim().removePrefix("/").removePrefix("docs/").trimStart('/')
    return when {
        clean == "beginners-guide.md" || clean.endsWith("/beginners-guide.md") -> ReaderStyle.GUIDE
        clean.startsWith("posts/") -> ReaderStyle.POST
        else -> ReaderStyle.DOC
    }
}

fun fmhyWebUrlForPath(path: String): String {
    val clean = path.trim()
        .removePrefix("/")
        .removePrefix("docs/")
        .trimStart('/')
        .removeSuffix(".md")
    return "https://fmhy.net/$clean"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    path: String,
    title: String,
    container: AppContainer,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)
    val webUrl = remember(path) { fmhyWebUrlForPath(path) }
    val style = remember(path) { readerStyleForPath(path) }

    var result by remember(path) { mutableStateOf<DocResult?>(null) }
    var isLoading by remember(path) { mutableStateOf(true) }
    var reloadTrigger by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var sheetEntry by remember(path) { mutableStateOf<Entry?>(null) }

    LaunchedEffect(path) {
        if (style == ReaderStyle.POST) {
            try { PostsReadState.markRead(container.userStore, path) } catch (_: Exception) {}
        }
    }

    LaunchedEffect(path, reloadTrigger) {
        isLoading = true
        result = container.docFetcher.get(path)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open on fmhy.net") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showMenu = false
                                linkOpener.openUrl(webUrl)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                showMenu = false
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, title)
                                    putExtra(Intent.EXTRA_TEXT, webUrl)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share $title"))
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                result is DocResult.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Could not load document",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = (result as DocResult.Error).message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { reloadTrigger++ }
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                result is DocResult.Success -> {
                    val success = result as DocResult.Success
                    val scrollState = rememberScrollState()
                    val bottomPadding = LocalBottomBarPadding.current

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(bottom = bottomPadding + 16.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 640.dp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 8.dp),
                            ) {
                                if (success.stale) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CloudOff,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "Offline · showing cached copy",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            )
                                        }
                                    }
                                }

                                if (style == ReaderStyle.GUIDE) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp, bottom = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(56.dp),
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Outlined.School,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(32.dp),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            text = "Beginner's guide",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "Start here: the basics of staying safe and finding things on FMHY.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                        val date = success.frontMatter["date"]
                                        if (!date.isNullOrBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = date,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                    }
                                }

                                if (style == ReaderStyle.POST) {
                                    val baseName = path.substringAfterLast('/').removeSuffix(".md")
                                    val fmDate = success.frontMatter["date"]
                                    val monthLabel = PostsListing.formatDateText(
                                        baseName, fmDate, PostsListing.parseDate(baseName, fmDate),
                                    )
                                    PostHeader(title = title, monthLabel = monthLabel)
                                    PostBody(
                                        markdown = success.text,
                                        onOpenUrl = { url -> linkOpener.openUrl(url) },
                                        onSiteTap = { row: PostSiteRow ->
                                            scope.launch {
                                                val match = try {
                                                    findEntryForUrl(container.datasetStore, row.url)
                                                } catch (_: Exception) { null }
                                                if (match != null) sheetEntry = match else linkOpener.openUrl(row.url)
                                            }
                                        },
                                    )
                                } else {
                                    MarkdownBlocks(
                                        markdown = success.text,
                                        onOpenUrl = { url ->
                                            linkOpener.openUrl(url)
                                        },
                                        readerStyle = style,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    sheetEntry?.let { entry ->
        EntryDetailBottomSheet(
            entry = entry,
            onDismiss = { sheetEntry = null },
            onOpenUrl = { linkOpener.openUrl(it) },
            onCopyLink = { url ->
                sheetEntry = null
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                onShowSnackbar("Link copied to clipboard")
            },
            onShareLink = { url ->
                sheetEntry = null
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                context.startActivity(Intent.createChooser(intent, "Share ${entry.name}"))
            },
            onShowSnackbar = onShowSnackbar,
        )
    }

    UnsafeWarningDialog(linkOpener)
}

/** Finds the FMHY entry whose URL matches [url] (normalised), or null. */
private suspend fun findEntryForUrl(store: DatasetStore, url: String): Entry? {
    if (!store.isAvailable) return null
    val host = url.substringAfter("://").substringBefore('/').substringBefore('?').lowercase().removePrefix("www.")
    if (host.isBlank()) return null
    val target = DatasetStore.normalizeEntryUrl(url)
    return store.entriesMatchingHost(host).firstOrNull {
        DatasetStore.normalizeEntryUrl(it.url) == target ||
            it.originalUrl?.let { o -> DatasetStore.normalizeEntryUrl(o) == target } == true
    }
}
