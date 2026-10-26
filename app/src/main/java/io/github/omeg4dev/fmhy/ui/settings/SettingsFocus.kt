package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind

val LocalSettingFocus = compositionLocalOf<String?> { null }

@Composable
fun ProvideSettingFocus(
    settingId: String?,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSettingFocus provides settingId, content = content)
}

fun Modifier.settingHighlight(settingId: String?): Modifier = composed {
    val focus = LocalSettingFocus.current
    if (settingId != null && settingId == focus) {
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(settingId) {
            alpha.animateTo(
                targetValue = 0.9f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            )
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            )
        }
        val highlightColor = MaterialTheme.colorScheme.primaryContainer
        this.drawBehind {
            drawRect(color = highlightColor.copy(alpha = alpha.value))
        }
    } else {
        this
    }
}

fun focusSectionIndex(sectionSettingIds: List<List<String>>, focus: String?): Int? {
    if (focus == null) return null
    val index = sectionSettingIds.indexOfFirst { focus in it }
    return if (index >= 0) index else null
}

/**
 * Creates and remembers a [LazyListState] that automatically scrolls to the section containing
 * the focused setting (from [LocalSettingFocus]), if any.
 *
 * Convention for later parts: each page's [androidx.compose.foundation.lazy.LazyColumn] has one
 * `item {}` per section, in the order of the [sectionSettingIds] list they pass.
 */
@Composable
fun rememberFocusedListState(sectionSettingIds: List<List<String>>): LazyListState {
    val state = rememberLazyListState()
    val focus = LocalSettingFocus.current
    LaunchedEffect(focus) {
        if (focus != null) {
            val targetIndex = focusSectionIndex(sectionSettingIds, focus)
            if (targetIndex != null) {
                state.animateScrollToItem(targetIndex)
            }
        }
    }
    return state
}
