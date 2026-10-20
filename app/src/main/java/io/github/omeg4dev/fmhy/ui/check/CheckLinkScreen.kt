package io.github.omeg4dev.fmhy.ui.check

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import kotlinx.coroutines.delay
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckLinkScreen(
    initialQuery: String = "",
    container: AppContainer,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var verdict by remember { mutableStateOf<Verdict?>(null) }

    var clipboardHost by remember { mutableStateOf<String?>(null) }
    var clipboardUrl by remember { mutableStateOf<String?>(null) }

    // Read clipboard on screen start only
    LaunchedEffect(Unit) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            val text = item?.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                val extracted = extractFirstUrl(text)
                if (looksLikeUrl(extracted)) {
                    val norm = SafeGuard.normalizeUrl(extracted)
                    val host = if (norm != null) {
                        try { URI(norm).host?.lowercase()?.removePrefix("www.") } catch (_: Exception) { null }
                    } else null
                    if (!host.isNullOrEmpty()) {
                        clipboardHost = host
                        clipboardUrl = extracted
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore clipboard errors
        }
    }

    // Autofocus when initial is empty
    LaunchedEffect(Unit) {
        if (initialQuery.isEmpty()) {
            focusRequester.requestFocus()
        }
    }

    // While typing, check debounced 300 ms
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            verdict = null
            return@LaunchedEffect
        }
        delay(300)
        if (container.datasetStore.isAvailable) {
            verdict = container.safeGuard.check(trimmed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check a link") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            // URL input field
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text("Enter a link like example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                ),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
            )

            // Suggestion chip "Paste <host>" when clipboard has a URL
            if (clipboardHost != null) {
                Spacer(Modifier.height(8.dp))
                SuggestionChip(
                    onClick = {
                        query = clipboardUrl ?: clipboardHost ?: ""
                    },
                    label = { Text("Paste $clipboardHost") },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Result VerdictCard
            if (verdict != null) {
                Spacer(Modifier.height(16.dp))
                VerdictCard(
                    verdict = verdict!!,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onOpenUrl = { linkOpener.openUrl(it) },
                    onShowSnackbar = onShowSnackbar,
                )
            }

            // Help text below
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Share any link to FMHY, or select a link and pick \u201cCheck with FMHY\u201d.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    UnsafeWarningDialog(linkOpener)
}
