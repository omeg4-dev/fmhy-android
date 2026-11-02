package io.github.omeg4dev.fmhy.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.ui.common.Motion

@Composable
fun StatusNoticeOverlay(
    notices: StatusNotices,
    modifier: Modifier = Modifier,
) {
    val line by notices.line.collectAsStateWithLifecycle()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter,
    ) {
        val state = line
        if (state is ProgressLineUiState.Running) {
            val f = state.fraction
            if (f != null) {
                LinearProgressIndicator(
                    progress = { f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
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
                        .height(2.dp)
                        .clearAndSetSemantics {
                            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                            stateDescription = state.a11y
                        },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }

        NoticeSlot(line as? ProgressLineUiState.Done) { doneState ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = doneState.text,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
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
