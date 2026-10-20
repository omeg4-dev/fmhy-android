package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.ui.graphics.vector.ImageVector

fun sectionIcon(id: String): ImageVector = when (id) {
    "ai" -> Icons.Outlined.AutoAwesome
    "audio", "audio-tools" -> Icons.Outlined.Headphones
    "developer-tools" -> Icons.Outlined.Code
    "downloading" -> Icons.Outlined.Download
    "educational", "educational-tools" -> Icons.Outlined.School
    "file-tools" -> Icons.Outlined.FolderOpen
    "gaming" -> Icons.Outlined.SportsEsports
    "gaming-tools" -> Icons.Outlined.VideogameAsset
    "image-tools" -> Icons.Outlined.Image
    "internet-tools" -> Icons.Outlined.Language
    "linux-macos" -> Icons.Outlined.Terminal
    "misc" -> Icons.Outlined.Category
    "mobile" -> Icons.Outlined.PhoneAndroid
    "non-english" -> Icons.Outlined.Translate
    "privacy" -> Icons.Outlined.Shield
    "reading", "beginners-guide" -> Icons.Outlined.AutoStories
    "faq" -> Icons.AutoMirrored.Outlined.HelpOutline
    "backups" -> Icons.Outlined.Backup
    "news-and-posts" -> Icons.Outlined.Newspaper
    "social-media-tools" -> Icons.Outlined.Forum
    "storage" -> Icons.Outlined.Inventory2
    "system-tools" -> Icons.Outlined.Memory
    "text-tools" -> Icons.Outlined.TextFields
    "torrenting" -> Icons.Outlined.Hub
    "video" -> Icons.Outlined.Movie
    "video-tools" -> Icons.Outlined.VideoSettings
    else -> Icons.Outlined.Folder
}
