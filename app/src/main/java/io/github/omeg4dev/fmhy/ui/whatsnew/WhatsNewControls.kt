package io.github.omeg4dev.fmhy.ui.whatsnew

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.model.ChangeKind

@Composable
internal fun FilterBar(
    state: WhatsNewUiState,
    sectionTitles: Map<String, String>,
    onSetPeriod: (WhatsNewPeriod) -> Unit,
    onToggleType: (ChangeKind) -> Unit,
    onClearTypes: () -> Unit,
    onSetTypes: (Set<ChangeKind>) -> Unit,
    onSetScope: (WhatsNewScope) -> Unit,
    onOpenSectionsSheet: () -> Unit,
    onManageFollowing: () -> Unit = {},
) {
    var periodMenuExpanded by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var scopeMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 1. Period Pill
        Box {
            FilterDropdownPill(
                label = state.period.label,
                active = state.period != WhatsNewPeriod.WEEK,
                onClick = { periodMenuExpanded = true },
            )
            DropdownMenu(
                expanded = periodMenuExpanded,
                onDismissRequest = { periodMenuExpanded = false },
            ) {
                WhatsNewPeriod.entries.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.label) },
                        trailingIcon = if (state.period == p) {
                            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null,
                        onClick = {
                            periodMenuExpanded = false
                            onSetPeriod(p)
                        },
                    )
                }
            }
        }

        // 2. Type Pill
        val typeLabel = when {
            state.types.isEmpty() || state.types.size == ChangeKind.entries.size -> "All types"
            state.types == WhatsNewGrouping.DEFAULT_TYPES -> "All but edits"
            state.types.size == 1 -> state.types.first().label()
            else -> "${state.types.size} types"
        }
        Box {
            FilterDropdownPill(
                label = typeLabel,
                active = state.types.isNotEmpty() && state.types != WhatsNewGrouping.DEFAULT_TYPES,
                onClick = { typeMenuExpanded = true },
            )
            DropdownMenu(
                expanded = typeMenuExpanded,
                onDismissRequest = { typeMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("All types") },
                    trailingIcon = if (state.types.isEmpty()) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        onClearTypes()
                    },
                )
                DropdownMenuItem(
                    text = { Text("All but edits") },
                    trailingIcon = if (state.types == WhatsNewGrouping.DEFAULT_TYPES) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        onSetTypes(WhatsNewGrouping.DEFAULT_TYPES)
                    },
                )
                HorizontalDivider()
                ChangeKind.entries.forEach { kind ->
                    val count = state.counts[kind] ?: 0
                    val isSelected = kind in state.types
                    if (count > 0 || isSelected) {
                        val look = kind.style()
                        DropdownMenuItem(
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(look.containerColor),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = look.icon,
                                        contentDescription = null,
                                        tint = look.contentColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
                            text = { Text(look.label) },
                            trailingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null,
                                    )
                                }
                            },
                            onClick = {
                                onToggleType(kind)
                            },
                        )
                    }
                }
            }
        }

        // 3. Scope Pill
        val scopeLabel = when (val s = state.scope) {
            is WhatsNewScope.All -> "All sections"
            is WhatsNewScope.Following -> "Following"
            is WhatsNewScope.Saved -> "Saved"
            is WhatsNewScope.Sections -> {
                if (s.sections.size == 1) {
                    val secId = s.sections.first()
                    sectionTitles[secId] ?: "1 section"
                } else {
                    "${s.sections.size} sections"
                }
            }
        }
        Box {
            FilterDropdownPill(
                label = scopeLabel,
                active = state.scope !is WhatsNewScope.All,
                onClick = { scopeMenuExpanded = true },
            )
            DropdownMenu(
                expanded = scopeMenuExpanded,
                onDismissRequest = { scopeMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("All sections") },
                    trailingIcon = if (state.scope is WhatsNewScope.All) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        scopeMenuExpanded = false
                        onSetScope(WhatsNewScope.All)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Following") },
                    enabled = state.hasFollows,
                    trailingIcon = if (state.scope is WhatsNewScope.Following) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        scopeMenuExpanded = false
                        onSetScope(WhatsNewScope.Following)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Saved sites") },
                    trailingIcon = if (state.scope is WhatsNewScope.Saved) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = {
                        scopeMenuExpanded = false
                        onSetScope(WhatsNewScope.Saved)
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Choose sections…") },
                    onClick = {
                        scopeMenuExpanded = false
                        onOpenSectionsSheet()
                    },
                )
            }
        }

        if (state.scope is WhatsNewScope.Following) {
            TextButton(
                onClick = onManageFollowing,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Text(
                    text = "Manage",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun FilterDropdownPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

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
