package io.github.omeg4dev.fmhy.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.ui.graphics.vector.ImageVector

data class HomeBlockInfo(
    val title: String,
    val summary: String,
    val icon: ImageVector,
)

fun homeBlockInfo(block: HomeBlock): HomeBlockInfo = when (block) {
    HomeBlock.SEARCH -> HomeBlockInfo(
        title = "Search",
        summary = "The search bar",
        icon = Icons.Default.Search,
    )
    HomeBlock.NEW_SINCE -> HomeBlockInfo(
        title = "New since last visit",
        summary = "A card with the number of sites added since you last opened the app",
        icon = Icons.Outlined.NewReleases,
    )
    HomeBlock.RANDOM_PICK -> HomeBlockInfo(
        title = "Random pick",
        summary = "One random recommended site",
        icon = Icons.Default.Casino,
    )
    HomeBlock.PINNED -> HomeBlockInfo(
        title = "Pinned categories",
        summary = "Tiles for the sections and categories you pinned",
        icon = Icons.Default.PushPin,
    )
    HomeBlock.NEW_IN_PINNED -> HomeBlockInfo(
        title = "New in pinned categories",
        summary = "Sites added to your pinned categories since your last visit",
        icon = Icons.Default.Notifications,
    )
    HomeBlock.FAVOURITES -> HomeBlockInfo(
        title = "Favourites",
        summary = "Your latest saved sites",
        icon = Icons.Default.Star,
    )
    HomeBlock.SUGGESTIONS -> HomeBlockInfo(
        title = "Suggestions",
        summary = "A random pick plus sites from the sections you open most",
        icon = Icons.Default.AutoAwesome,
    )
    HomeBlock.SECTIONS -> HomeBlockInfo(
        title = "Sections list",
        summary = "All FMHY sections, grouped like the website",
        icon = Icons.AutoMirrored.Filled.List,
    )
}
