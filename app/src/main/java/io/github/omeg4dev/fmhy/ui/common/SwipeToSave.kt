package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.model.Entry

@Suppress("DEPRECATION")
@Composable
fun SwipeToSave(
    entry: Entry,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val actions = rememberSaveActions()
    val isSaved = actions.isSaved(entry)
    val haptics = rememberHaptics()
    // The dismiss state keeps its first confirmValueChange lambda; read the current actions/entry through
    // updated state, otherwise every swipe acts on the saved set from the first composition (always "save").
    val currentActions by rememberUpdatedState(actions)
    val currentEntry by rememberUpdatedState(entry)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v != SwipeToDismissBoxValue.Settled) {
                currentActions.toggle(currentEntry)
            }
            false
        },
        positionalThreshold = { it * 0.35f },
    )

    val isPastThreshold = dismissState.targetValue != SwipeToDismissBoxValue.Settled
    var lastPastThreshold by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isPastThreshold) {
        if (lastPastThreshold != null && lastPastThreshold != isPastThreshold) {
            haptics.tick()
        }
        lastPastThreshold = isPastThreshold
    }

    val iconScale by animateFloatAsState(
        targetValue = if (isPastThreshold) 1f else 0.8f,
        animationSpec = Motion.standard(Motion.SHORT),
        label = "swipeIconScale",
    )

    val containerColor = if (!isSaved) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (!isSaved) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val icon = if (!isSaved) Icons.Filled.BookmarkAdd else Icons.Outlined.BookmarkRemove
    val label = if (!isSaved) "Save" else "Remove"

    val a11yModifier = if (enabled) {
        Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(
                    label = if (isSaved) "Remove from Saved" else "Save",
                    action = {
                        actions.toggle(entry)
                        true
                    },
                )
            )
        }
    } else {
        Modifier
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                val isStart = direction == SwipeToDismissBoxValue.StartToEnd
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(containerColor)
                        .padding(horizontal = 24.dp),
                    contentAlignment = if (isStart) Alignment.CenterStart else Alignment.CenterEnd,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isStart) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor,
                            )
                        } else {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = contentColor,
                            )
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.then(a11yModifier),
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled,
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            content()
        }
    }
}
