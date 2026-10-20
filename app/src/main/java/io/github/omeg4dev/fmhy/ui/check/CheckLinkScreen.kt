package io.github.omeg4dev.fmhy.ui.check

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.safety.SafeGuard
import io.github.omeg4dev.fmhy.data.safety.SafetyStatus
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.ui.common.CrossfadeContent
import io.github.omeg4dev.fmhy.ui.common.EmptyState
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.enterStagger
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RecentCheck(
    val url: String,
    val status: SafetyStatus,
    val ts: Long = System.currentTimeMillis(),
)

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
    val haptics = rememberHaptics()
    val coroutineScope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var verdict by remember { mutableStateOf<Verdict?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    var clipboardHost by remember { mutableStateOf<String?>(null) }
    var clipboardUrl by remember { mutableStateOf<String?>(null) }

    var recentChecks by remember { mutableStateOf<List<RecentCheck>>(emptyList()) }

    // Load recent checks from UserStore
    LaunchedEffect(Unit) {
        val rawJson = container.userStore.get("check_recent")
        if (!rawJson.isNullOrEmpty()) {
            try {
                recentChecks = Json.decodeFromString<List<RecentCheck>>(rawJson)
            } catch (_: Exception) {}
        }
    }

    fun saveRecentCheck(checkedUrl: String, verdictStatus: SafetyStatus) {
        if (verdictStatus == SafetyStatus.INVALID) return
        val updated = listOf(RecentCheck(checkedUrl, verdictStatus, System.currentTimeMillis())) +
            recentChecks.filterNot { it.url.equals(checkedUrl, ignoreCase = true) }
        val trimmed = updated.take(10)
        recentChecks = trimmed
        coroutineScope.launch {
            try {
                container.userStore.put("check_recent", Json.encodeToString(trimmed))
            } catch (_: Exception) {}
        }
    }

    fun clearRecents() {
        recentChecks = emptyList()
        coroutineScope.launch {
            try {
                container.userStore.put("check_recent", "[]")
            } catch (_: Exception) {}
        }
    }

    fun runImmediateCheck(targetUrl: String) {
        val trimmed = targetUrl.trim()
        if (trimmed.isEmpty()) {
            verdict = null
            isChecking = false
            return
        }
        if (!container.datasetStore.isAvailable) return

        coroutineScope.launch {
            isChecking = true
            val v = container.safeGuard.check(trimmed)
            verdict = v
            isChecking = false
            saveRecentCheck(trimmed, v.status)

            when (v.status) {
                SafetyStatus.FMHY_SITE,
                SafetyStatus.STARRED,
                SafetyStatus.LISTED,
                SafetyStatus.DOMAIN_LISTED -> haptics.confirm()
                SafetyStatus.UNSAFE,
                SafetyStatus.POTENTIALLY_UNSAFE -> haptics.longPress()
                else -> haptics.tick()
            }
        }
    }

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

    // While typing, check debounced 400 ms
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            verdict = null
            isChecking = false
            return@LaunchedEffect
        }
        delay(400)
        if (container.datasetStore.isAvailable) {
            isChecking = true
            val v = container.safeGuard.check(trimmed)
            verdict = v
            isChecking = false
            saveRecentCheck(trimmed, v.status)
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

            // URL input field with trailing filled tonal "Check" button
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
                        runImmediateCheck(query)
                    }
                ),
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear input")
                            }
                        }
                        FilledTonalButton(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                runImmediateCheck(query)
                            },
                            modifier = Modifier.height(40.dp),
                        ) {
                            Text("Check")
                        }
                    }
                },
            )

            // Paste chip: when clipboard holds a URL and the field is empty
            if (clipboardHost != null && query.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                AssistChip(
                    onClick = {
                        val target = clipboardUrl ?: clipboardHost ?: ""
                        query = target
                        runImmediateCheck(target)
                    },
                    label = { Text("Paste $clipboardHost") },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // Status: dataset not ready
            if (!container.datasetStore.isAvailable) {
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Loading FMHY data\u2026",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (isChecking) {
                // Status: checking
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Checking\u2026",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Results: VerdictCard entering with enterStagger, CrossfadeContent when switching
            if (verdict != null) {
                Spacer(Modifier.height(16.dp))
                CrossfadeContent(
                    targetState = verdict,
                    contentKey = { it?.input },
                ) { currentVerdict ->
                    if (currentVerdict != null) {
                        VerdictCard(
                            verdict = currentVerdict,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .enterStagger(key = currentVerdict.input),
                            onOpenUrl = { linkOpener.openUrl(it) },
                            onShowSnackbar = onShowSnackbar,
                            onCheckHost = { host ->
                                query = host
                                runImmediateCheck(host)
                            },
                        )
                    }
                }
            }

            // When query is empty: Recent checks or EmptyState
            if (query.isEmpty() && verdict == null) {
                Spacer(Modifier.height(16.dp))
                if (recentChecks.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Recent checks",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = { clearRecents() }) {
                            Text("Clear")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        recentChecks.forEach { item ->
                            val (iconVector, iconTint) = when (item.status) {
                                SafetyStatus.FMHY_SITE -> Icons.Default.Verified to MaterialTheme.colorScheme.primary
                                SafetyStatus.STARRED -> Icons.Default.Star to Color(0xFFFFC14D)
                                SafetyStatus.LISTED -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.secondary
                                SafetyStatus.DOMAIN_LISTED -> Icons.Default.Domain to MaterialTheme.colorScheme.secondary
                                SafetyStatus.POTENTIALLY_UNSAFE -> Icons.Default.Warning to Color(0xFFF57C00)
                                SafetyStatus.UNSAFE -> Icons.Default.Dangerous to MaterialTheme.colorScheme.error
                                SafetyStatus.SEARCH_ENGINE -> Icons.Default.Search to MaterialTheme.colorScheme.outline
                                SafetyStatus.NO_DATA -> Icons.AutoMirrored.Filled.HelpOutline to MaterialTheme.colorScheme.outline
                                SafetyStatus.INVALID -> Icons.Default.LinkOff to MaterialTheme.colorScheme.outline
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        query = item.url
                                        runImmediateCheck(item.url)
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = item.url,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                } else {
                    EmptyState(
                        icon = Icons.Outlined.TravelExplore,
                        title = "Check any link",
                        body = "Paste a link to see what FMHY says about it. You can also share links to this app from any other app.",
                    )
                }
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
