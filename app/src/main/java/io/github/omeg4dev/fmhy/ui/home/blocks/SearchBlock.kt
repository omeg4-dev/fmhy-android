package io.github.omeg4dev.fmhy.ui.home.blocks

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.ui.search.SearchBarShape
import io.github.omeg4dev.fmhy.ui.search.searchBarBorder
import io.github.omeg4dev.fmhy.ui.search.searchBarContainerColor

fun LazyListScope.searchBlock(
    totalEntries: Int,
    onNavigateToSearch: () -> Unit,
    /** True when another block sits above Search: it then gets a headline like the other blocks. */
    showHeadline: Boolean = false,
) {
    item(key = "search-pill") {
        if (showHeadline) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        Surface(
            onClick = onNavigateToSearch,
            shape = SearchBarShape,
            color = searchBarContainerColor(),
            border = searchBarBorder(focused = false),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Search ${"%,d".format(totalEntries)} sites",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
