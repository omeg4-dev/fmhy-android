package io.github.omeg4dev.fmhy.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.EntryKind
import io.github.omeg4dev.fmhy.data.model.Tier

enum class Platform(val word: String, @DrawableRes val icon: Int) {
    WINDOWS("Windows", R.drawable.ic_platform_windows),
    MACOS("macOS", R.drawable.ic_platform_macos),
    LINUX("Linux", R.drawable.ic_platform_linux),
    ANDROID("Android", R.drawable.ic_platform_android),
    IOS("iOS", R.drawable.ic_platform_ios),
    WEB("Web", R.drawable.ic_platform_web),
    DOCKER("Docker", R.drawable.ic_platform_docker),
}

private val PLATFORM_NAME_MAP = mapOf(
    "Windows" to Platform.WINDOWS,
    "macOS" to Platform.MACOS,
    "Mac" to Platform.MACOS,
    "Linux" to Platform.LINUX,
    "Android" to Platform.ANDROID,
    "iOS" to Platform.IOS,
    "Web" to Platform.WEB,
    "Docker" to Platform.DOCKER,
)

private val PLATFORM_REGEX = Regex(
    """(?<![a-z]\s)\b(Windows|macOS|Mac|Linux|Android|iOS|Web|Docker)(?=,|[\t ]/|$)"""
)

private fun isOnlyPlatformWords(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    val tokens = trimmed.split(',', '/').map { it.trim() }.filter { it.isNotEmpty() }
    return tokens.isNotEmpty() && tokens.all { it in PLATFORM_NAME_MAP }
}

fun findPlatformSpans(text: String): List<Triple<IntRange, Platform, String>> {
    val searchStartIndex = if (isOnlyPlatformWords(text)) {
        0
    } else {
        val firstSlash = text.indexOf(" / ")
        if (firstSlash == -1) return emptyList()
        firstSlash + 3
    }

    return PLATFORM_REGEX.findAll(text, searchStartIndex).mapNotNull { match ->
        val group = match.groups[1] ?: return@mapNotNull null
        val word = group.value
        val platform = PLATFORM_NAME_MAP[word] ?: return@mapNotNull null
        Triple(group.range, platform, word)
    }.toList()
}

@DrawableRes
fun socialIconFor(label: String): Int? {
    return when (label.trim().lowercase()) {
        "discord" -> R.drawable.ic_link_discord
        "github" -> R.drawable.ic_link_github
        "gitlab" -> R.drawable.ic_link_gitlab
        "source code", "source" -> R.drawable.ic_link_source
        "telegram" -> R.drawable.ic_link_telegram
        "subreddit", "reddit" -> R.drawable.ic_link_reddit
        "x" -> R.drawable.ic_link_x
        "mastodon" -> R.drawable.ic_link_mastodon
        "bluesky" -> R.drawable.ic_link_bluesky
        ".onion", "onion" -> R.drawable.ic_link_onion
        else -> null
    }
}

@Composable
internal fun EntryMarker(
    entry: Entry,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val markers = buildList {
        if (entry.tier == Tier.TOP) {
            add(R.drawable.ic_marker_top to "Top pick")
        } else if (entry.tier == Tier.STAR) {
            add(R.drawable.ic_marker_star to "Recommended")
        }
        if (entry.kind == EntryKind.INDEX) {
            add(R.drawable.ic_marker_index to "Index")
        } else if (entry.kind == EntryKind.SECTION_LINK) {
            add(R.drawable.ic_marker_section_link to "Link to another section")
        }
    }
    if (markers.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((iconRes, desc) in markers) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = desc,
                modifier = Modifier.size(size),
            )
        }
    }
}
