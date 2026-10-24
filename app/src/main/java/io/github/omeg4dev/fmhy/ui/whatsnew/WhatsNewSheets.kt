package io.github.omeg4dev.fmhy.ui.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.ui.common.EntryRow
import io.github.omeg4dev.fmhy.ui.common.SwipeToSave
import io.github.omeg4dev.fmhy.ui.common.sectionIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegendBottomSheet(
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "What the colours mean",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            val kinds = listOf(
                ChangeKind.ADDED,
                ChangeKind.REMOVED,
                ChangeKind.RENAMED,
                ChangeKind.URL_CHANGED,
                ChangeKind.MOVED,
                ChangeKind.STARRED,
                ChangeKind.UNSTARRED,
                ChangeKind.EDITED,
            )
            kinds.forEach { kind ->
                val look = kind.style()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(look.containerColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = look.icon,
                            contentDescription = null,
                            tint = look.contentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = look.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = look.contentColor,
                        )
                        Text(
                            text = look.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChooseSectionsBottomSheet(
    availableSections: List<Section>,
    selectedSections: Set<String>,
    countsBySection: Map<String, Int>,
    onToggleSection: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Choose sections",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(availableSections, key = { it.id }) { section ->
                    val isChecked = section.id in selectedSections
                    val count = countsBySection[section.id] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleSection(section.id) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = sectionIcon(section.id),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (count > 0) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleSection(section.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReplacementsBottomSheet(
    card: ChangeCard,
    viewModel: WhatsNewViewModel,
    onOpenUrl: (String) -> Unit,
    onShowSnackbar: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var replacements by remember { mutableStateOf<List<Entry>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(card.key) {
        isLoading = true
        replacements = viewModel.getReplacements(card)
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Replacements for ${card.name}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Other sites in this category",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val list = replacements.orEmpty()
                if (list.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No replacements found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(list, key = { it.id }) { entry ->
                            SwipeToSave(entry = entry) {
                                EntryRow(
                                    entry = entry,
                                    onOpenUrl = onOpenUrl,
                                    onShowSnackbar = onShowSnackbar,
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}
