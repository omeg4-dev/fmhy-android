package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.omeg4dev.fmhy.data.link.PasteResult
import io.github.omeg4dev.fmhy.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PasteAction(val anywayLabel: String) {
    OPEN("Open the paste anyway"),
    COPY("Copy the paste anyway"),
    SHARE("Share the paste anyway"),
}

class PasteFailure(
    val url: String,
    val action: PasteAction,
    internal val onLinks: (List<String>) -> Unit,
    internal val onUseRaw: () -> Unit,
)

/**
 * Decodes pastebin-hidden links with a visible "Decoding hidden link…" state and a failure dialog
 * instead of silently falling back to the undecoded paste.
 */
class PasteDecoder(
    private val scope: CoroutineScope,
    private val resolve: suspend (String) -> PasteResult,
) {
    var decoding by mutableStateOf(false)
        private set
    var failure by mutableStateOf<PasteFailure?>(null)
        private set

    private var currentJob: Job? = null

    /**
     * [onLinks] gets the decoded links; [onUseRaw] runs for a paste that hides nothing, or when the user picks
     * the "... the paste anyway" button after a failure. Nothing runs if the user dismisses the failure dialog.
     */
    fun decode(
        url: String,
        action: PasteAction,
        onLinks: (List<String>) -> Unit,
        onUseRaw: () -> Unit,
    ) {
        currentJob?.cancel()
        failure = null
        decoding = true
        // Started lazily so [currentJob] is set before the body runs (an immediate dispatcher would run it inline).
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                when (val result = resolve(url)) {
                    is PasteResult.Links -> onLinks(result.urls)
                    is PasteResult.NotHidden -> onUseRaw()
                    is PasteResult.Failed -> failure = PasteFailure(url, action, onLinks, onUseRaw)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                failure = PasteFailure(url, action, onLinks, onUseRaw)
            } finally {
                if (currentJob === coroutineContext[Job]) {
                    decoding = false
                }
            }
        }
        currentJob = job
        job.start()
    }

    /** failure → null, decode the same request again (full set of attempts) */
    fun retry() {
        val currentFailure = failure ?: return
        failure = null
        decode(
            url = currentFailure.url,
            action = currentFailure.action,
            onLinks = currentFailure.onLinks,
            onUseRaw = currentFailure.onUseRaw,
        )
    }

    /** failure → null, call its onUseRaw */
    fun useRawAnyway() {
        val currentFailure = failure ?: return
        failure = null
        currentFailure.onUseRaw()
    }

    /** failure → null, nothing else */
    fun dismissFailure() {
        failure = null
    }
}

@Composable
fun rememberPasteDecoder(container: AppContainer): PasteDecoder {
    val scope = rememberCoroutineScope()
    return remember(container) {
        PasteDecoder(scope) { pasteResolverFor(container).resolve(it) }
    }
}

@Composable
fun PasteDecodeUi(decoder: PasteDecoder) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(decoder.decoding) {
        visible = false
        if (decoder.decoding) {
            delay(300)
            visible = true
        }
    }

    if (visible) {
        val density = LocalDensity.current
        val offsetY = with(density) { (-96).dp.roundToPx() }
        Popup(
            alignment = Alignment.BottomCenter,
            offset = IntOffset(0, offsetY),
            properties = PopupProperties(focusable = false),
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Decoding hidden link…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    val failure = decoder.failure
    if (failure != null) {
        AlertDialog(
            onDismissRequest = { decoder.dismissFailure() },
            title = { Text("Could not decode this link") },
            confirmButton = {
                TextButton(onClick = { decoder.retry() }) {
                    Text("Try again")
                }
            },
            dismissButton = {
                TextButton(onClick = { decoder.useRawAnyway() }) {
                    Text(failure.action.anywayLabel)
                }
            },
        )
    }
}
