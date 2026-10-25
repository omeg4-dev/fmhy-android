package io.github.omeg4dev.fmhy.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.browser.formatRelativeTime
import io.github.omeg4dev.fmhy.ui.common.Motion

/**
 * App-wide status progress line and chips floating under the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusNoticeOverlay(
    notices: StatusNotices,
    modifier: Modifier = Modifier,
) {
    val line by notices.line.collectAsStateWithLifecycle()
    val jobs by notices.jobs.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(line) {
        if (line == null) {
            showSheet = false
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        NoticeSlot(line) { state ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state) {
                    is ProgressLineUiState.Running -> {
                        val f = state.fraction
                        if (f != null) {
                            LinearProgressIndicator(
                                progress = { f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clearAndSetSemantics {
                                        progressBarRangeInfo = ProgressBarRangeInfo(f, 0f..1f)
                                        stateDescription = state.a11y
                                    },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clearAndSetSemantics {
                                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                                        stateDescription = state.a11y
                                    },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        if (state.stalled) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .widthIn(max = 420.dp)
                                    .heightIn(min = 32.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = state.text,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .clickable { showSheet = true },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = notices::keepWaiting) {
                                        Text("Keep waiting")
                                    }
                                    TextButton(onClick = notices::cancel) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .widthIn(max = 420.dp)
                                    .heightIn(min = 32.dp)
                                    .clickable { showSheet = true }
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = state.text,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    is ProgressLineUiState.Done -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .widthIn(max = 420.dp)
                                .heightIn(min = 32.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = state.text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    is ProgressLineUiState.Problem -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .widthIn(max = 420.dp)
                                .heightIn(min = 32.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = state.text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clickable { showSheet = true },
                                )
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = notices::retry) {
                                    Text("Retry")
                                }
                                IconButton(onClick = notices::dismiss) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val currentLine = line
                if (currentLine is ProgressLineUiState.Problem) {
                    Text(
                        text = currentLine.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (currentLine.details.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            currentLine.details.forEach { detail ->
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = {
                            notices.retry()
                            showSheet = false
                        }) {
                            Text("Retry")
                        }
                    }
                } else {
                    jobs.forEach { job ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = job.title,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val allSteps = stepNames(job.kind)
                            allSteps.forEachIndexed { idx, name ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    when {
                                        idx < job.stepIndex -> {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Done",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        idx == job.stepIndex -> {
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .semantics { contentDescription = "In progress" },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                            val currentStepText = if (job.fraction != null) {
                                                "${job.label} · ${job.percent}%"
                                            } else {
                                                job.label
                                            }
                                            Text(
                                                text = currentStepText,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Outlined.Circle,
                                                contentDescription = "Not started",
                                                tint = MaterialTheme.colorScheme.outlineVariant,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            val lastUp = remember(job.kind) { notices.lastUpdated(job.kind) }
                            Text(
                                text = "Last updated ${formatRelativeTime(lastUp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { notices.cancel(job.kind) }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }
            }
        }
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
