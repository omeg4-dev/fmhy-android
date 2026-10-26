package io.github.omeg4dev.fmhy.ui.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Badge text for the Browser item: hidden at 0 tabs, "9+" above 9. */
fun browserBadgeText(count: Int): String? = when {
    count <= 0 -> null
    count > 9 -> "9+"
    else -> count.toString()
}

/**
 * The bottom bar's "Browser" action item. Not a destination (never selected): tap opens the browser,
 * hold opens the tab menu. Mirrors the metrics of M3's NavigationBarItem (64x32 indicator pill,
 * 24 dp icon, labelMedium label) because that composable has no long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.BrowserBarItem(
    tabCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showLabel: Boolean = true,
    heightDp: Int = 80,
) {
    val interaction = remember { MutableInteractionSource() }
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val badge = browserBadgeText(tabCount)
    Column(
        modifier = Modifier
            .weight(1f)
            .height(heightDp.dp)
            .padding(bottom = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Browser" + if (tabCount > 0) ", $tabCount " + if (tabCount == 1) "tab" else "tabs" else ""
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(32.dp)
                .clip(CircleShape)
                .indication(interaction, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            BadgedBox(
                badge = {
                    if (badge != null) {
                        Badge { Text(badge) }
                    }
                },
            ) {
                Icon(Icons.Outlined.Tab, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text("Browser", style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
        }
    }
}
