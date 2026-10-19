package io.github.omeg4dev.fmhy.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import io.github.omeg4dev.fmhy.SectionNavKey
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.link.LinkTarget
import io.github.omeg4dev.fmhy.data.link.PasteLinks
import io.github.omeg4dev.fmhy.data.link.PasteResolver
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.nav.LocalAppNav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class LinkOpener(
    private val context: Context,
    private val store: DatasetStore,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val onShowSnackbar: (String) -> Unit,
    private val crossLinks: StateFlow<CrossLinks?>? = null,
    private val onOpenInternal: (LinkTarget) -> Unit = {},
    private val pasteResolver: PasteResolver? = null,
) {
    /** Links decoded from a pastebin page that hides several; the chooser dialog shows them. */
    var pendingPasteChoices by mutableStateOf<List<String>?>(null)
        private set

    var pendingUnsafeUrl by mutableStateOf<String?>(null)
        private set
    var pendingUnsafeReason by mutableStateOf<String?>(null)
        private set

    fun openUrl(url: String) {
        val target = crossLinks?.value?.resolve(url)
        if (target != null) {
            onOpenInternal(target)
            return
        }
        if (pasteResolver != null && PasteLinks.rawUrl(url) != null) {
            coroutineScope.launch {
                onShowSnackbar("Decoding hidden link…")
                val urls = pasteResolver.resolve(url)
                when {
                    urls == null -> openChecked(url)       // not decodable: show the paste itself
                    urls.size == 1 -> openChecked(urls[0])
                    else -> pendingPasteChoices = urls
                }
            }
            return
        }
        openChecked(url)
    }

    fun choosePasteLink(url: String?) {
        pendingPasteChoices = null
        if (url != null) openChecked(url)
    }

    private fun openChecked(url: String) {
        coroutineScope.launch {
            val reason = if (store.isAvailable) store.unsafeReason(url) else null
            if (reason != null) {
                pendingUnsafeReason = reason
                pendingUnsafeUrl = url
            } else {
                openUrlDirectly(url)
            }
        }
    }

    fun confirmOpenUnsafe() {
        val url = pendingUnsafeUrl
        pendingUnsafeUrl = null
        pendingUnsafeReason = null
        if (url != null) {
            openUrlDirectly(url)
        }
    }

    fun cancelOpenUnsafe() {
        pendingUnsafeUrl = null
        pendingUnsafeReason = null
    }

    private fun openUrlDirectly(url: String) {
        coroutineScope.launch {
            val openExternal = settingsRepository.openInExternalBrowser.first()
            val uri = url.toUri()
            if (openExternal) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    onShowSnackbar("No browser found to open link")
                }
            } else {
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

@Composable
fun UnsafeWarningDialog(opener: LinkOpener) {
    PasteChoiceDialog(opener)
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

private fun pasteResolverFor(container: AppContainer): PasteResolver =
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
): LinkOpener {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = LocalAppNav.current
    return remember(container, onShowSnackbar, nav) {
        LinkOpener(
            context = context,
            store = container.datasetStore,
            settingsRepository = container.settingsRepository,
            coroutineScope = scope,
            onShowSnackbar = onShowSnackbar,
            crossLinks = container.crossLinks,
            onOpenInternal = { target ->
                nav.push(SectionNavKey(target.sectionId, anchor = target.headingId))
            },
            pasteResolver = pasteResolverFor(container),
        )
    }
}

