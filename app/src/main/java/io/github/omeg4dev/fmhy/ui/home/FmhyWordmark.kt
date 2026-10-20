package io.github.omeg4dev.fmhy.ui.home

import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable as FloatAnimatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** "FMHY" spelled out: each initial grows the rest of its word. */
private val WORDS = listOf("F" to "ree", "M" to "edia", "H" to "eck", "Y" to "eah")

/** Logo colours: cyan, violet, magenta, amber, one per word F/M/H/Y. */
private val WORD_COLORS = listOf(
    Color(0xFF00E5FF),
    Color(0xFF7C5CFF),
    Color(0xFFFF3EC9),
    Color(0xFFFFC14D),
)

/**
 * The Home title. Tap: a colourful wave runs across the letters with a small bounce.
 * Hold: toggles spelling out "FreeMediaHeckYeah", staying expanded until held again. Survives rotation.
 */
@Composable
fun FmhyWordmark(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val bounce = remember { FloatAnimatable(1f) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val baseColor = LocalContentColor.current

    val wordColors = remember {
        List(4) { Animatable(baseColor) }
    }

    LaunchedEffect(expanded, baseColor) {
        wordColors.forEachIndexed { index, anim ->
            val target = if (expanded) WORD_COLORS[index] else baseColor
            anim.snapTo(target)
        }
    }

    fun playSweep(isExpanded: Boolean) {
        scope.launch {
            wordColors.forEachIndexed { index, anim ->
                launch {
                    delay(index * 180L)
                    val idle = if (isExpanded) WORD_COLORS[index] else baseColor
                    anim.snapTo(baseColor)
                    anim.animateTo(WORD_COLORS[index], tween(350))
                    anim.animateTo(idle, tween(350))
                }
            }
        }
        scope.launch {
            bounce.snapTo(0.92f)
            bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }

    val style: TextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .semantics { contentDescription = if (expanded) "FreeMediaHeckYeah" else "FMHY" }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        haptics.tick()
                        playSweep(expanded)
                    },
                    onLongPress = {
                        haptics.longPress()
                        expanded = !expanded
                        playSweep(expanded)
                    },
                )
            },
    ) {
        WORDS.forEachIndexed { index, (initial, rest) ->
            val color = wordColors[index].value
            val textStyle = style.copy(color = color)
            Text(
                text = initial,
                style = textStyle,
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandHorizontally(tween(260, delayMillis = index * 70)) + fadeIn(tween(260, delayMillis = index * 70)),
                exit = shrinkHorizontally(tween(220, delayMillis = (3 - index) * 50)) + fadeOut(tween(160)),
            ) {
                Text(
                    text = rest,
                    style = textStyle,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
