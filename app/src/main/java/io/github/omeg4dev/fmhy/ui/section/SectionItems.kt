package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WarningAmber
import io.github.omeg4dev.fmhy.ui.common.EmptyState
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.data.link.CrossLinks
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Heading
import io.github.omeg4dev.fmhy.data.model.Note
import io.github.omeg4dev.fmhy.data.model.SectionItem
import io.github.omeg4dev.fmhy.ui.common.MarkdownText

@Composable
fun HeadingHeader(
    heading: Heading,
    isHighlighted: Boolean = false,
    isFirstItem: Boolean = false,
    entryCount: Int? = null,
    onOpenUrl: (String) -> Unit = {},
    flag: String? = null,
    sectionId: String? = null,
    modifier: Modifier = Modifier,
) {
    val (splitTitle, url) = remember(heading.title) { CrossLinks.splitLinkTitle(heading.title) }
    val effectiveFlag = flag ?: if (heading.level == 1) flagFor(sectionId, heading.title) else null
    val displayTitle = if (effectiveFlag != null && heading.level == 1) "$effectiveFlag $splitTitle" else splitTitle

    val targetBg = if (isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }
    val animatedBg by animateColorAsState(targetBg, animationSpec = tween(300), label = "heading_bg")

    when (heading.level) {
        1 -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = if (isFirstItem) 12.dp else 32.dp,
                        bottom = 8.dp,
                    )
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (url != null) Modifier.clickable { onOpenUrl(url) } else Modifier),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(animatedBg)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (url != null) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            if (entryCount != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "$entryCount sites",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        2 -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .background(animatedBg)
                    .then(if (url != null) Modifier.clickable { onOpenUrl(url) } else Modifier)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 24.dp,
                        bottom = 6.dp,
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = 18.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(1.5.dp),
                            )
                    )
                    Text(
                        text = splitTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (url != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        else -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .background(animatedBg)
                    .then(if (url != null) Modifier.clickable { onOpenUrl(url) } else Modifier)
                    .padding(
                        start = 28.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 4.dp,
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = splitTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (url != null) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCallout(
    note: Note,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var hasOverflow by remember { mutableStateOf(false) }

    val isWarning = remember(note.textMd) {
        note.textMd.trim().trimStart('*').trimStart().startsWith("warning", ignoreCase = true)
    }

    val containerColor = if (isWarning) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
    }
    val borderColor = if (isWarning) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
    }
    val accentColor = if (isWarning) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val icon = if (isWarning) Icons.Outlined.WarningAmber else Icons.Outlined.Lightbulb
    val label = if (isWarning) "WARNING" else "TIP"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (hasOverflow) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
            Spacer(Modifier.height(6.dp))
            MarkdownText(
                markdown = note.textMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                onTextLayout = { layoutResult ->
                    if (!expanded && layoutResult.hasVisualOverflow) {
                        hasOverflow = true
                    }
                },
            )
            if (hasOverflow) {
                Text(
                    text = if (expanded) "Show less" else "Show more",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoreGroupRow(
    entries: List<Entry>,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "more_icon_rotation",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clickable { expanded = !expanded }
        ) {
            Text(
                text = "More · ${entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }

        if (!expanded) {
            val previewText = remember(entries) {
                entries.joinToString(" · ") { it.name }
            }
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { expanded = true }
            )
        } else {
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                entries.forEach { entry ->
                    AssistChip(
                        onClick = { onOpenUrl(entry.url) },
                        label = { Text(entry.name, style = MaterialTheme.typography.labelLarge) },
                        border = null,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryChipRail(
    chips: List<String>,
    selectedIndex: Int,
    onSelectChip: (Int) -> Unit,
    modifier: Modifier = Modifier,
    railState: LazyListState = rememberLazyListState(),
    onLongClickChip: ((Int) -> Unit)? = null,
) {
    val haptics = rememberHaptics()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in chips.indices) {
            railState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
        }
    }

    LazyRow(
        state = railState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(chips) { index, title ->
            val isSelected = index == selectedIndex
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
                animationSpec = tween(180),
                label = "chip_bg",
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(180),
                label = "chip_fg",
            )
            val border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

            val chipModifier = Modifier
                .minimumInteractiveComponentSize()
                .height(32.dp)
                .clip(CircleShape)
                .then(
                    if (onLongClickChip != null) {
                        Modifier.combinedClickable(
                            onClick = {
                                haptics.tick()
                                onSelectChip(index)
                            },
                            onLongClick = {
                                haptics.longPress()
                                onLongClickChip(index)
                            },
                        )
                    } else {
                        Modifier.clickable {
                            haptics.tick()
                            onSelectChip(index)
                        }
                    }
                )

            Surface(
                shape = CircleShape,
                color = containerColor,
                border = border,
                modifier = chipModifier,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

sealed interface OutlineItem {
    data object ReturnToTop : OutlineItem
    data class CategoryItem(val category: Category) : OutlineItem
    data class SubHeadingItem(val heading: Heading, val relativeLevel: Int) : OutlineItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpToHeadingBottomSheet(
    categories: List<Category>,
    currentCategoryId: String?,
    onDismiss: () -> Unit,
    onSelectHeading: (Heading) -> Unit,
    onReturnToTop: () -> Unit = {},
    headings: List<Heading> = emptyList(),
    sectionId: String? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val outlineItems = remember(categories) {
        buildList {
            add(OutlineItem.ReturnToTop)
            val hasLevel1 = categories.any { it.heading.level == 1 }
            val baseLevel = if (hasLevel1) 1 else 2
            for (category in categories) {
                add(OutlineItem.CategoryItem(category))
                for (sub in category.subHeadings) {
                    val relLevel = (sub.level - baseLevel).coerceAtLeast(1)
                    add(OutlineItem.SubHeadingItem(sub, relLevel))
                }
            }
        }
    }

    val currentCategoryIndex = remember(outlineItems, currentCategoryId) {
        if (currentCategoryId == null) 0
        else {
            val idx = outlineItems.indexOfFirst {
                it is OutlineItem.CategoryItem && it.category.heading.id == currentCategoryId
            }
            if (idx != -1) idx else 0
        }
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentCategoryIndex)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Outline",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                items(
                    count = outlineItems.size,
                    key = { idx ->
                        when (val item = outlineItems[idx]) {
                            is OutlineItem.ReturnToTop -> "return_to_top"
                            is OutlineItem.CategoryItem -> "cat_${item.category.heading.id}"
                            is OutlineItem.SubHeadingItem -> "sub_${item.heading.id}"
                        }
                    }
                ) { idx ->
                    when (val item = outlineItems[idx]) {
                        is OutlineItem.ReturnToTop -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onReturnToTop() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerticalAlignTop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Return to top",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        is OutlineItem.CategoryItem -> {
                            val category = item.category
                            val isCurrentCategory = category.heading.id == currentCategoryId
                            val (splitTitle, _) = remember(category.heading.title) {
                                CrossLinks.splitLinkTitle(category.heading.title)
                            }
                            val flag = remember(category.heading.title, sectionId) {
                                flagFor(sectionId, category.heading.title)
                            }
                            val titleWithFlag = if (flag != null) "$flag $splitTitle" else splitTitle
                            val rowBg = if (isCurrentCategory) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                            val textColor = if (isCurrentCategory) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                            val countColor = if (isCurrentCategory) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(color = rowBg, shape = MaterialTheme.shapes.small)
                                    .clickable { onSelectHeading(category.heading) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = titleWithFlag,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "${category.entryCount} sites",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = countColor,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        is OutlineItem.SubHeadingItem -> {
                            val heading = item.heading
                            val (splitTitle, _) = remember(heading.title) {
                                CrossLinks.splitLinkTitle(heading.title)
                            }
                            val isLevel3 = item.relativeLevel >= 2
                            val indentDp = if (isLevel3) 32.dp else 16.dp
                            val textStyle = if (isLevel3) {
                                MaterialTheme.typography.bodySmall
                            } else {
                                MaterialTheme.typography.bodyMedium
                            }
                            val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .drawBehind {
                                        val strokeWidth = 1.dp.toPx()
                                        val x1 = 12.dp.toPx()
                                        drawLine(
                                            color = outlineVariantColor,
                                            start = Offset(x1, 0f),
                                            end = Offset(x1, size.height),
                                            strokeWidth = strokeWidth,
                                        )
                                        if (isLevel3) {
                                            val x2 = 28.dp.toPx()
                                            drawLine(
                                                color = outlineVariantColor,
                                                start = Offset(x2, 0f),
                                                end = Offset(x2, size.height),
                                                strokeWidth = strokeWidth,
                                            )
                                        }
                                    }
                                    .clickable { onSelectHeading(heading) }
                                    .padding(
                                        start = 12.dp + indentDp,
                                        end = 12.dp,
                                        top = 8.dp,
                                        bottom = 8.dp,
                                    ),
                            ) {
                                Text(
                                    text = splitTitle,
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegendBottomSheet(
    starredOnly: Boolean,
    showIndexes: Boolean,
    onToggleStarredOnly: (Boolean) -> Unit,
    onToggleShowIndexes: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenFullLegend: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Legend",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            val legendItems = listOf(
                R.drawable.ic_marker_index to "Indexes — sites that list other sites",
                R.drawable.ic_marker_section_link to "Section links — opens another part of FMHY",
                R.drawable.ic_marker_star to "Recommended",
                R.drawable.ic_marker_top to "Top pick",
            )

            legendItems.forEach { (drawableRes, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Image(
                        painter = painterResource(drawableRes),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            TextButton(
                onClick = onOpenFullLegend,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text("All icons…")
            }

            Text(
                text = "Options",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
            )

            ListItem(
                headlineContent = {
                    Text("Starred only")
                },
                supportingContent = {
                    Text("Show only sites FMHY recommends (⭐ / 🌟)")
                },
                trailingContent = {
                    Switch(
                        checked = starredOnly,
                        onCheckedChange = null,
                    )
                },
                modifier = Modifier.toggleable(
                    value = starredOnly,
                    role = Role.Switch,
                    onValueChange = onToggleStarredOnly,
                ),
            )

            ListItem(
                headlineContent = {
                    Text("Show indexes")
                },
                supportingContent = {
                    Text("Include 🌐 index sites that list other sites")
                },
                trailingContent = {
                    Switch(
                        checked = showIndexes,
                        onCheckedChange = null,
                    )
                },
                modifier = Modifier.toggleable(
                    value = showIndexes,
                    role = Role.Switch,
                    onValueChange = onToggleShowIndexes,
                ),
            )
        }
    }
}

@Composable
fun FilterEmptyState(
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        icon = Icons.Outlined.StarBorder,
        title = "No starred sites here",
        actionLabel = "Show all sites",
        onAction = onShowAll,
        modifier = modifier,
    )
}

