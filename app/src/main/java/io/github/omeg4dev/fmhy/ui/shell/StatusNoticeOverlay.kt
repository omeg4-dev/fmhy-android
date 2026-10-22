package io.github.omeg4dev.fmhy.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.SyncBanner

/**
 * Sync / ad-block pills floating over the top of every screen. Takes no layout space of its own;
 * only the pills themselves catch touches. Place it as the last-but-onboarding child of the root Box.
 */
@Composable
fun StatusNoticeOverlay(notices: StatusNotices, modifier: Modifier = Modifier) {
    val sync by notices.sync.collectAsStateWithLifecycle()
    val adblock by notices.adblock.collectAsStateWithLifecycle()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NoticeSlot(sync) { banner ->
            SyncBanner(
                text = banner.text,
                isLoading = banner.isLoading,
                isError = banner.isError,
                onDismiss = notices::dismissSync,
            )
        }
        NoticeSlot(adblock) { setup -> AdblockPill(setup, onRetry = notices::retryAdblock) }
    }
}

/** Keeps the last non-null value while the exit animation runs. */
@Composable
private fun <T : Any> NoticeSlot(value: T?, content: @Composable (T) -> Unit) {
    var shown: T? = remember { null }
    if (value != null) shown = value
    AnimatedVisibility(
        visible = value != null,
        enter = fadeIn(Motion.enter()) + slideInVertically(Motion.enter()) { -it },
        exit = fadeOut(Motion.exit()) + slideOutVertically(Motion.exit()) { -it },
    ) {
        shown?.let { content(it) }
    }
}

@Composable
private fun AdblockPill(setup: AdblockSetupUiState, onRetry: () -> Unit) {
    val failed = setup is AdblockSetupUiState.Failed
    val containerColor = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        shape = CircleShape,
        color = containerColor,
        shadowElevation = 2.dp,
        modifier = Modifier
            .height(36.dp)
            .then(if (failed) Modifier.clickable(onClick = onRetry) else Modifier),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            when (setup) {
                is AdblockSetupUiState.Downloading -> {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp), color = contentColor)
                    Text(
                        text = if (setup.progress != null) {
                            "Downloading ad-block lists · ${(setup.progress * 100).toInt()}%"
                        } else {
                            "Downloading ad-block lists…"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                    )
                }
                is AdblockSetupUiState.Ready -> {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
                    Text("Ad blocking ready", style = MaterialTheme.typography.labelMedium, color = contentColor)
                }
                is AdblockSetupUiState.Failed -> {
                    Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp), tint = contentColor)
                    Text(
                        "Ad-block lists failed to download · Retry",
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor,
                    )
                }
            }
        }
    }
}
