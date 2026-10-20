package io.github.omeg4dev.fmhy.browser

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics

internal fun hostOf(url: String): String =
    try { Uri.parse(url).host?.removePrefix("www.").orEmpty() } catch (_: Exception) { "" }

/** Open, non-private, loaded tabs with the most recently active first. Pure, unit-tested. */
fun continueBrowsingRows(tabs: List<TabState>, limit: Int = 3): List<TabState> =
    tabs.filter { !it.isPrivate && it.url.isNotBlank() && it.url != "about:blank" }
        .sortedByDescending { it.lastActiveTime }
        .take(limit)

/** "Continue browsing" card for Home: up to 3 recent tabs. Caller hides it when [continueBrowsingRows] is empty. */
@Composable
fun ContinueBrowsingCard(tabs: List<TabState>, totalTabs: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val rows = continueBrowsingRows(tabs)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            ) {
                Text(
                    "Continue browsing",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    haptics.click()
                    bringBrowserToFront(context, showTabs = true)
                }) { Text("All tabs ($totalTabs)") }
            }
            rows.forEach { tab ->
                val host = hostOf(tab.url)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable {
                            haptics.click()
                            bringBrowserToFront(context, selectTabId = tab.id)
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .then(
                                if (tab.favicon == null) {
                                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                                } else Modifier
                            ),
                    ) {
                        val icon = tab.favicon
                        if (icon != null) {
                            Image(
                                bitmap = icon.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(32.dp),
                            )
                        } else {
                            Text(
                                (host.firstOrNull() ?: tab.title.firstOrNull() ?: '?').uppercaseChar().toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tab.title.ifBlank { host },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            host,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}


