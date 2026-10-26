package io.github.omeg4dev.fmhy.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.nav.BottomBarStyle
import io.github.omeg4dev.fmhy.nav.Tab
import io.github.omeg4dev.fmhy.nav.metrics

@Composable
fun AppBottomBar(
    style: BottomBarStyle,
    current: Tab,
    browserTabCount: Int,
    onSelect: (Tab) -> Unit,
    onBrowserClick: () -> Unit,
    onBrowserLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = style.metrics()

    val barItems: @Composable RowScope.() -> Unit = {
        NavigationBarItem(
            selected = current == Tab.BROWSE,
            onClick = { onSelect(Tab.BROWSE) },
            icon = {
                Icon(
                    imageVector = if (current == Tab.BROWSE) Icons.Filled.Explore else Icons.Outlined.Explore,
                    contentDescription = "Browse",
                )
            },
            label = { Text("Browse", maxLines = 1) },
            alwaysShowLabel = metrics.alwaysShowLabel,
        )
        NavigationBarItem(
            selected = current == Tab.SEARCH,
            onClick = { onSelect(Tab.SEARCH) },
            icon = {
                Icon(
                    imageVector = if (current == Tab.SEARCH) Icons.Filled.Search else Icons.Outlined.Search,
                    contentDescription = "Search",
                )
            },
            label = { Text("Search", maxLines = 1) },
            alwaysShowLabel = metrics.alwaysShowLabel,
        )
        BrowserBarItem(
            tabCount = browserTabCount,
            onClick = onBrowserClick,
            onLongClick = onBrowserLongClick,
            showLabel = metrics.alwaysShowLabel,
            heightDp = metrics.heightDp,
        )
        NavigationBarItem(
            selected = current == Tab.SAVED,
            onClick = { onSelect(Tab.SAVED) },
            icon = {
                Icon(
                    imageVector = if (current == Tab.SAVED) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = "Saved",
                )
            },
            label = { Text("Saved", maxLines = 1) },
            alwaysShowLabel = metrics.alwaysShowLabel,
        )
    }

    when (style) {
        BottomBarStyle.CLASSIC -> {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = modifier,
                content = barItems,
            )
        }
        BottomBarStyle.ROUNDED_TOP -> {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                content = barItems,
            )
        }
        BottomBarStyle.FLOATING -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shadowElevation = 6.dp,
                    tonalElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth(),
                ) {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        windowInsets = WindowInsets(0),
                        modifier = Modifier.height(72.dp),
                        content = barItems,
                    )
                }
            }
        }
        BottomBarStyle.SELECTED_LABEL_ONLY -> {
            val navBarInset = NavigationBarDefaults.windowInsets.asPaddingValues().calculateBottomPadding()
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                windowInsets = NavigationBarDefaults.windowInsets,
                modifier = modifier.height(64.dp + navBarInset),
                content = barItems,
            )
        }
    }
}
