package io.github.omeg4dev.fmhy.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.omeg4dev.fmhy.browser.BrowserActivity
import io.github.omeg4dev.fmhy.browser.OriginBounds
import io.github.omeg4dev.fmhy.browser.browserOpenOptions
import io.github.omeg4dev.fmhy.SectionNavKey
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.OpenLinksWith
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.doc.DocFetcher
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.link.LinkTarget
import io.github.omeg4dev.fmhy.data.link.PasteLinks
import io.github.omeg4dev.fmhy.data.link.PasteResolver
import io.github.omeg4dev.fmhy.data.model.CriticalNote
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.MainActivity
import io.github.omeg4dev.fmhy.nav.AppNavState
import io.github.omeg4dev.fmhy.nav.LocalAppNavOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

data class PendingNote(
    val path: String,
    val originalUrl: String,
    val title: String,
)

data class PendingCriticalWarning(
    val note: CriticalNote,
    val url: String,
    val entryId: String?,
    val originBounds: OriginBounds?,
)

class LinkOpener(
    private val context: Context,
    private val store: DatasetStore,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val onShowSnackbar: (String) -> Unit,
    private val crossLinks: StateFlow<CrossLinks?>? = null,
    private val onOpenInternal: (LinkTarget) -> Unit = {},
    private val pasteResolver: PasteResolver? = null,
    private val userStore: UserStore? = null,
    val docFetcher: DocFetcher? = null,
) {
    val pasteDecoder: PasteDecoder? = pasteResolver?.let { r -> PasteDecoder(coroutineScope) { r.resolve(it) } }

    /** Links decoded from a pastebin page that hides several; the chooser dialog shows them. */
    var pendingPasteChoices by mutableStateOf<List<String>?>(null)
        private set

    var pendingUnsafeUrl by mutableStateOf<String?>(null)
        private set
    var pendingUnsafeReason by mutableStateOf<String?>(null)
        private set
    private var pendingUnsafeEntryId: String? = null
    private var pendingUnsafeBounds: OriginBounds? = null

    var pendingCriticalWarning by mutableStateOf<PendingCriticalWarning?>(null)
        private set

    var pendingNote by mutableStateOf<PendingNote?>(null)
        private set

    fun dismissNote() {
        pendingNote = null
    }

    fun openNoteDirectly(url: String) {
        pendingNote = null
        openUrlDirectly(url)
    }

    fun openUrl(url: String, entryId: String? = null, originBounds: OriginBounds? = null) {
        val target = crossLinks?.value?.resolve(url)
        if (target != null) {
            onOpenInternal(target)
            return
        }
        val noteInfo = extractNoteInfo(url)
        if (noteInfo != null && docFetcher != null) {
            val (path, title) = noteInfo
            pendingNote = PendingNote(path = path, originalUrl = url, title = title)
            return
        }
        if (pasteDecoder != null && PasteLinks.rawUrl(url) != null) {
            pasteDecoder.decode(
                url = url,
                action = PasteAction.OPEN,
                onLinks = { urls ->
                    if (urls.size == 1) openChecked(urls[0], entryId, originBounds) else pendingPasteChoices = urls
                },
                onUseRaw = { openChecked(url, entryId, originBounds) },
            )
            return
        }
        openChecked(url, entryId, originBounds)
    }

    fun choosePasteLink(url: String?) {
        pendingPasteChoices = null
        if (url != null) openChecked(url, null)
    }

    private fun openChecked(url: String, entryId: String? = null, originBounds: OriginBounds? = null) {
        coroutineScope.launch {
            val unsafeWarning = settingsRepository.protectionPrefs.first().unsafeWarning
            val reason = if (unsafeWarning && store.isAvailable) store.unsafeReason(url) else null
            if (reason != null) {
                pendingUnsafeReason = reason
                pendingUnsafeUrl = url
                pendingUnsafeEntryId = entryId
                pendingUnsafeBounds = originBounds
            } else {
                maybeWarnCritical(url, entryId, originBounds)
            }
        }
    }

    fun confirmOpenUnsafe() {
        val url = pendingUnsafeUrl
        val entryId = pendingUnsafeEntryId
        val bounds = pendingUnsafeBounds
        pendingUnsafeUrl = null
        pendingUnsafeReason = null
        pendingUnsafeEntryId = null
        pendingUnsafeBounds = null
        if (url != null) {
            maybeWarnCritical(url, entryId, bounds)
        }
    }

    fun cancelOpenUnsafe() {
        pendingUnsafeUrl = null
        pendingUnsafeReason = null
        pendingUnsafeEntryId = null
        pendingUnsafeBounds = null
    }

    private fun maybeWarnCritical(url: String, entryId: String? = null, originBounds: OriginBounds? = null) {
        coroutineScope.launch {
            val criticalWarning = settingsRepository.protectionPrefs.first().criticalNoteWarning
            if (!criticalWarning || !store.isAvailable) {
                openUrlDirectly(url, entryId, originBounds)
                return@launch
            }
            val note = try {
                store.criticalNoteForUrl(url)
            } catch (_: Exception) {
                null
            }
            if (note == null) {
                openUrlDirectly(url, entryId, originBounds)
                return@launch
            }
            val hidden = try {
                userStore?.hiddenCriticalWarnings().orEmpty()
            } catch (_: Exception) {
                emptyMap()
            }
            if (CriticalWarningPolicy.shouldWarn(criticalWarning, note, hidden)) {
                pendingCriticalWarning = PendingCriticalWarning(
                    note = note,
                    url = url,
                    entryId = entryId,
                    originBounds = originBounds,
                )
            } else {
                openUrlDirectly(url, entryId, originBounds)
            }
        }
    }

    fun confirmCriticalWarning(dontShowAgain: Boolean) {
        val pending = pendingCriticalWarning ?: return
        pendingCriticalWarning = null
        coroutineScope.launch {
            if (dontShowAgain && userStore != null) {
                try {
                    userStore.hideCriticalWarning(pending.note.id, pending.note.hash)
                } catch (_: Exception) {}
            }
            openUrlDirectly(pending.url, pending.entryId, pending.originBounds)
        }
    }

    fun cancelCriticalWarning() {
        pendingCriticalWarning = null
    }

    internal fun openUrlDirectly(
        url: String,
        entryId: String? = null,
        originBounds: OriginBounds? = null,
        recordHistory: Boolean = true,
    ) {
        coroutineScope.launch {
            if (userStore != null && recordHistory) {
                try {
                    if (settingsRepository.dataPrefs.first().keepHistory) {
                        val host = try { url.toUri().host?.removePrefix("www.").orEmpty().ifEmpty { url } } catch (_: Exception) { url }
                        val matching = if (store.isAvailable) {
                            try {
                                store.entriesMatchingHost(host).firstOrNull {
                                    it.url.trimEnd('/') == url.trimEnd('/') ||
                                    it.originalUrl?.trimEnd('/') == url.trimEnd('/')
                                }
                            } catch (_: Exception) { null }
                        } else null
                        userStore.addHistory(url, matching?.id, matching?.name ?: host)
                    }
                } catch (_: Exception) {}
            }

            val openWith = settingsRepository.browserPrefs.first().openLinksWith
            val uri = url.toUri()
            when (openWith) {
                OpenLinksWith.IN_APP -> {
                    try {
                        val options = browserOpenOptions(context, originBounds)
                        val intent = BrowserActivity.intent(context, url, entryId).apply {
                            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            if (context !is Activity) {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        }
                        context.startActivity(intent, options)
                    } catch (_: Exception) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            onShowSnackbar("No browser found to open link")
                        }
                    }
                }
                OpenLinksWith.EXTERNAL -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        onShowSnackbar("No browser found to open link")
                    }
                }
                OpenLinksWith.CUSTOM_TAB -> {
                    try {
                        val customTabsIntent = CustomTabsIntent.Builder()
                            .setShowTitle(true)
                            .build()
                        customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        customTabsIntent.launchUrl(context, uri)
                    } catch (_: ActivityNotFoundException) {
                        onShowSnackbar("No browser found to open link")
                    } catch (_: Exception) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            onShowSnackbar("No browser found to open link")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnsafeWarningDialog(opener: LinkOpener) {
    PasteChoiceDialog(opener)
    opener.pasteDecoder?.let { PasteDecodeUi(it) }
    val fetcher = opener.docFetcher
    if (fetcher != null) {
        NoteSheet(opener, fetcher)
    }
    val reason = opener.pendingUnsafeReason ?: return
    AlertDialog(
        onDismissRequest = { opener.cancelOpenUnsafe() },
        title = { Text("FMHY lists this site as unsafe") },
        text = { Text(reason) },
        confirmButton = {
            TextButton(onClick = { opener.confirmOpenUnsafe() }) {
                Text("Open anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = { opener.cancelOpenUnsafe() }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CriticalNoteDialog(opener: LinkOpener) {
    val pending = opener.pendingCriticalWarning ?: return
    var dontShowAgain by rememberSaveable(pending.note.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { opener.cancelCriticalWarning() },
        title = {
            Text(
                text = pending.note.title.ifBlank { "Warning" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = "Please read this before continuing.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    MarkdownBlocks(
                        markdown = pending.note.textMd,
                        onOpenUrl = { opener.openUrl(it) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain },
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Don't show again",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { opener.confirmCriticalWarning(dontShowAgain) }) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = { opener.cancelCriticalWarning() }) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun PasteChoiceDialog(opener: LinkOpener) {
    val urls = opener.pendingPasteChoices ?: return
    AlertDialog(
        onDismissRequest = { opener.choosePasteLink(null) },
        title = { Text("This link hides ${urls.size} links") },
        text = {
            Column {
                urls.forEach { url ->
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { opener.choosePasteLink(url) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { opener.choosePasteLink(null) }) { Text("Cancel") }
        },
    )
}

private var sharedPasteResolver: PasteResolver? = null

fun pasteResolverFor(container: AppContainer): PasteResolver =
    synchronized(PasteLinks) {
        sharedPasteResolver ?: PasteResolver(
            container.okHttpClient,
            File(container.context.filesDir, "paste-links.json"),
        ).also { sharedPasteResolver = it }
    }

@Composable
fun rememberLinkOpener(
    container: AppContainer,
    onShowSnackbar: (String) -> Unit,
    nav: AppNavState? = LocalAppNavOrNull.current,
): LinkOpener {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(container, onShowSnackbar, nav) {
        LinkOpener(
            context = context,
            store = container.datasetStore,
            settingsRepository = container.settingsRepository,
            coroutineScope = scope,
            onShowSnackbar = onShowSnackbar,
            crossLinks = container.crossLinks,
            onOpenInternal = { target ->
                if (nav != null) {
                    nav.push(SectionNavKey(target.sectionId, anchor = target.headingId))
                } else {
                    // Outside the main navigation: bring up the app instead.
                    context.startActivity(
                        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            pasteResolver = pasteResolverFor(container),
            userStore = container.userStore,
            docFetcher = container.docFetcher,
        )
    }
}

fun extractNoteInfo(url: String): Pair<String, String>? {
    val regex = Regex("""(?:\.vitepress/notes/|docs/\.vitepress/notes/)([^/?#]+\.md)""", RegexOption.IGNORE_CASE)
    val match = regex.find(url.trim()) ?: return null
    val fileName = match.groupValues[1]
    val path = ".vitepress/notes/$fileName"
    val title = formatNoteTitle(fileName)
    return path to title
}

fun formatNoteTitle(fileName: String): String {
    val base = fileName.substringAfterLast('/').removeSuffix(".md")
    return base.split('-', '_', ' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}

