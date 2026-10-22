package io.github.omeg4dev.fmhy.ui.whatsnew

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.nav.LocalBottomBarPadding
import io.github.omeg4dev.fmhy.ui.common.enterStagger

/** Tile order on the hub: the interesting kinds first, small edits last and muted. */
private val HUB_KINDS: List<ChangeKind> = listOf(
    ChangeKind.ADDED, ChangeKind.REMOVED,
    ChangeKind.STARRED, ChangeKind.UNSTARRED,
    ChangeKind.MOVED, ChangeKind.RENAMED,
    ChangeKind.URL_CHANGED, ChangeKind.EDITED,
)

private fun periodShort(p: WhatsNewPeriod): String = when (p) {
    WhatsNewPeriod.WEEK -> "Week"
    WhatsNewPeriod.MONTH -> "Month"
    WhatsNewPeriod.QUARTER -> "Quarter"
}

@Composable
fun WhatsNewSearchField(
    text: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search changes") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None,
            imeAction = ImeAction.Search,
        ),
    )
}

/** Landing screen of What's new: search, All changes, one tile per kind, period, scope shortcuts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewHub(
    state: WhatsNewUiState,
    onSetPeriod: (WhatsNewPeriod) -> Unit,
    onOpenAll: () -> Unit,
    onOpenKind: (ChangeKind) -> Unit,
    onOpenScope: (WhatsNewScope) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 4.dp,
            bottom = LocalBottomBarPadding.current + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "period") {
            val periods = WhatsNewPeriod.entries
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                periods.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = state.period == p,
                        onClick = { onSetPeriod(p) },
                        shape = SegmentedButtonDefaults.itemShape(i, periods.size),
                    ) { Text(periodShort(p)) }
                }
            }
        }
        item(key = "all") {
            Card(
                onClick = onOpenAll,
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth().enterStagger(0),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("All changes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (state.isLoading) "Loading…" else "${state.totalCards} in the ${state.period.label.lowercase()}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
            }
        }
        items(HUB_KINDS.chunked(2), key = { it.first().name }) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEachIndexed { idx, kind ->
                    KindTile(
                        kind = kind,
                        count = state.counts[kind] ?: 0,
                        loading = state.isLoading,
                        onClick = { onOpenKind(kind) },
                        modifier = Modifier
                            .weight(1f)
                            .enterStagger(1 + HUB_KINDS.indexOf(kind) / 2),
                    )
                }
            }
        }
        item(key = "scopes") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                if (state.hasFollows) {
                    ScopeShortcut(
                        label = "Following",
                        icon = { Icon(Icons.Outlined.NotificationsNone, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        onClick = { onOpenScope(WhatsNewScope.Following) },
                        modifier = Modifier.weight(1f),
                    )
                }
                ScopeShortcut(
                    label = "Saved",
                    icon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    onClick = { onOpenScope(WhatsNewScope.Saved) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun KindTile(
    kind: ChangeKind,
    count: Int,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = kind.style()
    val muted = kind == ChangeKind.EDITED
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = look.containerColor, contentColor = look.contentColor),
        modifier = modifier.alpha(if (muted || (count == 0 && !loading)) 0.7f else 1f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).background(look.contentColor.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(look.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (loading) "–" else "$count",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(look.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = look.explanation.substringBefore(" —"),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                color = look.contentColor.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun ScopeShortcut(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
