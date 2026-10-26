package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.DatasetStore
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.user.Follow
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.ui.common.FollowTree
import io.github.omeg4dev.fmhy.ui.common.LocalAppContainer
import io.github.omeg4dev.fmhy.ui.common.LocalSnackbarHost
import io.github.omeg4dev.fmhy.ui.common.SectionFollowState
import io.github.omeg4dev.fmhy.ui.common.pinItem
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.rememberIsPinned
import io.github.omeg4dev.fmhy.ui.common.sectionIcon
import io.github.omeg4dev.fmhy.ui.common.unpinItem
import kotlinx.coroutines.launch

fun shouldShowFollowingPin(
    isFollowed: Boolean,
    isExcluded: Boolean,
    isPinned: Boolean,
): Boolean = !isExcluded && (isFollowed || isPinned)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowingScreen(
    userStore: UserStore,
    datasetStore: DatasetStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val container = LocalAppContainer.current
    val haptics = rememberHaptics()
    val snackbarHostState = remember { SnackbarHostState() }

    val sections by produceState(initialValue = emptyList<Section>(), datasetStore) {
        value = try {
            datasetStore.sections()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val allHeadings by produceState(initialValue = emptyList<Heading>(), datasetStore) {
        value = try {
            datasetStore.allHeadings()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val subsectionsBySection = remember(allHeadings) {
        val grouped = allHeadings.groupBy { it.sectionId }
        grouped.mapValues { (_, secHeadings) ->
            val hasLevel1 = secHeadings.any { it.level == 1 }
            val topLevel = when {
                hasLevel1 -> 1
                secHeadings.any { it.level == 2 } -> 2
                else -> 0
            }
            if (topLevel == 0) emptyList() else secHeadings.filter { it.level == topLevel }
        }
    }

    val subsectionIdsBySection = remember(subsectionsBySection, sections) {
        sections.associate { sec ->
            sec.id to (subsectionsBySection[sec.id]?.map { it.id } ?: emptyList())
        }
    }

    val userChanges by userStore.changes.collectAsStateWithLifecycle(initialValue = 0L)
    val follows by produceState(initialValue = emptyList<Follow>(), userChanges) {
        value = try {
            userStore.follows()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val followTree = remember(follows) { FollowTree(follows) }

    val followedCount = remember(followTree, subsectionIdsBySection) {
        followTree.followedCount(subsectionIdsBySection)
    }

    var expandedSections by rememberSaveable { mutableStateOf(setOf<String>()) }

    val headerText = if (followedCount == 0) {
        "No sections followed yet"
    } else if (followedCount == 1) {
        "1 section followed"
    } else {
        "$followedCount sections followed"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Following") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = modifier,
    ) { innerPadding ->
        CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            item {
                SettingsSection(title = headerText) {
                    if (sections.isEmpty()) {
                        Text(
                            text = "No sections available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        sections.forEachIndexed { index, section ->
                            if (index > 0) SettingsDivider()

                            val subHeadings = subsectionsBySection[section.id].orEmpty()
                            val subIds = subsectionIdsBySection[section.id].orEmpty()
                            val state = followTree.sectionState(section.id, subIds)
                            val isExpanded = section.id in expandedSections
                            val isSectionFollowed = state != SectionFollowState.NONE
                            val isSectionPinned = rememberIsPinned(container, section.id, "")
                            val showSectionPin = shouldShowFollowingPin(
                                isFollowed = isSectionFollowed,
                                isExcluded = false,
                                isPinned = isSectionPinned,
                            )

                            ListItem(
                                leadingContent = {
                                    Icon(
                                        imageVector = sectionIcon(section.id),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                },
                                supportingContent = when (state) {
                                    SectionFollowState.PARTIAL -> {
                                        {
                                            Text(
                                                text = "Partly followed",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    SectionFollowState.ALL -> {
                                        {
                                            Text(
                                                text = "All subsections",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    SectionFollowState.NONE -> null
                                },
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        if (showSectionPin) {
                                            IconButton(
                                                onClick = {
                                                    val app = container ?: return@IconButton
                                                    scope.launch {
                                                        if (isSectionPinned) {
                                                            unpinItem(
                                                                container = app,
                                                                snackbarHostState = snackbarHostState,
                                                                haptics = haptics,
                                                                sectionId = section.id,
                                                                headingId = "",
                                                                title = section.title,
                                                            )
                                                        } else {
                                                            pinItem(
                                                                container = app,
                                                                snackbarHostState = snackbarHostState,
                                                                sectionId = section.id,
                                                                headingId = "",
                                                                title = section.title,
                                                            )
                                                        }
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    imageVector = if (isSectionPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                    contentDescription = if (isSectionPinned) "Unpin" else "Pin to Home",
                                                    tint = if (isSectionPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = state != SectionFollowState.NONE,
                                            onCheckedChange = { checked ->
                                                scope.launch {
                                                    if (checked) {
                                                        userStore.followSection(section.id, section.title)
                                                    } else {
                                                        userStore.unfollowSection(section.id)
                                                    }
                                                }
                                            },
                                        )
                                        if (subHeadings.isNotEmpty()) {
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (subHeadings.isNotEmpty()) {
                                            expandedSections = if (isExpanded) {
                                                expandedSections - section.id
                                            } else {
                                                expandedSections + section.id
                                            }
                                        }
                                    },
                            )

                            if (subHeadings.isNotEmpty()) {
                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut(),
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f))
                                            .padding(start = 24.dp),
                                    ) {
                                        subHeadings.forEachIndexed { subIndex, heading ->
                                            if (subIndex > 0) SettingsDivider()
                                            val subTitle = CrossLinks.splitLinkTitle(heading.title).first
                                            val isFollowed = followTree.isSubsectionFollowed(section.id, heading.id)
                                            val isExcluded = follows.any { it.sectionId == section.id && it.headingId == heading.id && it.excluded }
                                            val isSubPinned = rememberIsPinned(container, section.id, heading.id)
                                            val showSubPin = shouldShowFollowingPin(
                                                isFollowed = isFollowed,
                                                isExcluded = isExcluded,
                                                isPinned = isSubPinned,
                                            )

                                            ListItem(
                                                headlineContent = {
                                                    Text(
                                                        text = subTitle,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                },
                                                trailingContent = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        if (showSubPin) {
                                                            IconButton(
                                                                onClick = {
                                                                    val app = container ?: return@IconButton
                                                                    scope.launch {
                                                                        if (isSubPinned) {
                                                                            unpinItem(
                                                                                container = app,
                                                                                snackbarHostState = snackbarHostState,
                                                                                haptics = haptics,
                                                                                sectionId = section.id,
                                                                                headingId = heading.id,
                                                                                title = subTitle,
                                                                            )
                                                                        } else {
                                                                            pinItem(
                                                                                container = app,
                                                                                snackbarHostState = snackbarHostState,
                                                                                sectionId = section.id,
                                                                                headingId = heading.id,
                                                                                title = subTitle,
                                                                            )
                                                                        }
                                                                    }
                                                                },
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (isSubPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                                    contentDescription = if (isSubPinned) "Unpin" else "Pin to Home",
                                                                    tint = if (isSubPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                )
                                                            }
                                                        }
                                                        Switch(
                                                            checked = isFollowed,
                                                            onCheckedChange = { on ->
                                                                scope.launch {
                                                                    userStore.setSubsectionFollowed(
                                                                        section.id,
                                                                        heading.id,
                                                                        "${section.title} › $subTitle",
                                                                        on,
                                                                    )
                                                                }
                                                            },
                                                        )
                                                    }
                                                },
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        scope.launch {
                                                            userStore.setSubsectionFollowed(
                                                                section.id,
                                                                heading.id,
                                                                "${section.title} › $subTitle",
                                                                !isFollowed,
                                                            )
                                                        }
                                                    },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            extraRows()
        }
    }
}
}
