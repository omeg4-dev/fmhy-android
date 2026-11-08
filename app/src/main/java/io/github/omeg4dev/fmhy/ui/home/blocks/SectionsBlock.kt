package io.github.omeg4dev.fmhy.ui.home.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.ui.common.displayTitle
import io.github.omeg4dev.fmhy.ui.common.ListEntrance
import io.github.omeg4dev.fmhy.ui.common.enterStaggerOrdered
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import io.github.omeg4dev.fmhy.ui.home.SectionGroup

fun LazyListScope.sectionsBlock(
    sectionGroups: List<SectionGroup>,
    entrance: ListEntrance,
    unreadPosts: Int,
    hiddenSectionsCount: Int,
    showingHiddenSections: Boolean,
    onToggleShowHidden: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onNavigateToSection: (String) -> Unit,
    onNavigateToReader: (String, String) -> Unit,
    onNavigateToPosts: () -> Unit,
) {
    sectionGroups.forEach { group ->
        item(key = "sections-header-${group.title}") {
            Text(
                text = group.title ?: "Sections",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .animateItem()
                    .enterStaggerOrdered(entrance)
                    .padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
            )
        }

        item(key = "sections-list-${group.title}") {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .animateItem()
                    .enterStaggerOrdered(entrance)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    group.sections.forEachIndexed { index, section ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .clickable {
                                    when (section.id) {
                                        "audio-tools" -> onOpenUrl("https://fmhy.net/audio#audio-tools")
                                        "educational-tools" -> onOpenUrl("https://fmhy.net/educational#educational-tools")
                                        "beginners-guide" -> onNavigateToReader("beginners-guide.md", "Beginner's guide")
                                        "faq" -> onNavigateToReader("other/FAQ.md", "FAQ")
                                        "backups" -> onNavigateToReader("other/backups.md", "Backups & mirrors")
                                        "news-and-posts" -> onNavigateToPosts()
                                        else -> onNavigateToSection(section.id)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = sectionIcon(section.id),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = section.displayTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (section.id == "news-and-posts" && unreadPosts > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "New",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                if (section.description.isNotBlank()) {
                                    Text(
                                        text = section.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (section.entryCount > 0) {
                                    Text(
                                        text = "${"%,d".format(section.entryCount)} sites",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (hiddenSectionsCount > 0) {
        item(key = "hidden-sections-toggle") {
            val text = if (showingHiddenSections) {
                "$hiddenSectionsCount hidden sections · Hide"
            } else {
                "$hiddenSectionsCount hidden sections · Show"
            }
            Surface(
                onClick = onToggleShowHidden,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
