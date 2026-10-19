package io.github.omeg4dev.fmhy.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import kotlinx.coroutines.launch

/** "FMHY" spelled out: each initial grows the rest of its word. */
private val WORDS = listOf("F" to "ree", "M" to "edia", "H" to "eck", "Y" to "eah")

/** Logo colours (the glitch triangle's cyan and magenta) around the theme's violet. */
private val SWEEP = listOf(Color(0xFF00E5FF), Color(0xFF7C5CFF), Color(0xFFFF3EC9), Color(0xFFFFC14D), Color(0xFF00E5FF))

/**
 * The Home title. Tap: a colourful sweep runs across the letters with a small bounce.
 * Hold: toggles spelling out "FreeMediaHeckYeah", staying expanded until held again. Survives rotation.
 */
@Composable
fun FmhyWordmark(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val sweep = remember { Animatable(0f) }      // 0 = plain, 0..1 = gradient travelling across
    val bounce = remember { Animatable(1f) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    fun playSweep() {
        scope.launch {
            sweep.snapTo(0.001f)
            sweep.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
            sweep.snapTo(0f)
        }
        scope.launch {
            bounce.snapTo(0.92f)
            bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }

    val baseColor = LocalContentColor.current
    val style: TextStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    val textStyle = style.copy(color = baseColor)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                val progress = sweep.value
                if (progress == 0f && expanded) {
                    // Spelled out: the whole word in the logo colours.
                    drawRect(
                        brush = Brush.horizontalGradient(SWEEP.dropLast(1)),
                        blendMode = BlendMode.SrcIn,
                    )
                } else if (progress > 0f) {
                    // One gradient band, twice the word's width, slides across all letters.
                    val band = size.width * 1.5f
                    val shift = -band + progress * (size.width + band)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = SWEEP,
                            start = Offset(shift, 0f),
                            end = Offset(shift + band, size.height),
                            tileMode = TileMode.Clamp,
                        ),
                        blendMode = BlendMode.SrcIn,
                    )
                }
            }
            .semantics { contentDescription = if (expanded) "FreeMediaHeckYeah" else "FMHY" }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        haptics.click()
                        playSweep()
                    },
                    onLongPress = {
                        haptics.longPress()
                        expanded = !expanded
                        playSweep()
                    },
                )
            },
    ) {
        WORDS.forEachIndexed { index, (initial, rest) ->
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
