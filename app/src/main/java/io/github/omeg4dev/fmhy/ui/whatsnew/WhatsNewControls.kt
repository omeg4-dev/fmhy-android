package io.github.omeg4dev.fmhy.ui.whatsnew

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.model.ChangeKind

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummaryLine(
    counts: Map<ChangeKind, Int>,
    selectedTypes: Set<ChangeKind>,
    onSelectKind: (ChangeKind) -> Unit,
) {
    val order = listOf(
        ChangeKind.ADDED,
        ChangeKind.RENAMED,
        ChangeKind.URL_CHANGED,
        ChangeKind.MOVED,
        ChangeKind.STARRED,
        ChangeKind.UNSTARRED,
        ChangeKind.REMOVED,
        ChangeKind.EDITED,
    )

    val activeSegments = order.mapNotNull { kind ->
        val count = counts[kind] ?: 0
        if (count > 0) kind to count else null
    }

    if (activeSegments.isEmpty()) return

    // One scrollable line: each segment keeps a 48 dp touch target without wrapping into tall gaps.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        activeSegments.forEachIndexed { index, (kind, count) ->
            val look = kind.style()
            val text = when (kind) {
                ChangeKind.ADDED -> "$count new"
                ChangeKind.RENAMED -> "$count renamed"
                ChangeKind.URL_CHANGED -> if (count == 1) "1 new link" else "$count new links"
                ChangeKind.MOVED -> "$count moved"
                ChangeKind.STARRED -> "$count starred"
                ChangeKind.UNSTARRED -> "$count unstarred"
                ChangeKind.REMOVED -> "$count removed"
                ChangeKind.EDITED -> "$count edited"
            }

            val isSelected = selectedTypes == setOf(kind)
            Surface(
                onClick = { onSelectKind(kind) },
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) look.containerColor else MaterialTheme.colorScheme.surface,
                modifier = Modifier.defaultMinSize(minHeight = 28.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = look.contentColor,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            if (index < activeSegments.lastIndex) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}
