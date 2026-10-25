package io.github.omeg4dev.fmhy.ui.section

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

fun Modifier.jumpHighlight(
    active: Boolean,
    shape: Shape = RoundedCornerShape(12.dp),
): Modifier = composed {
    val primary = MaterialTheme.colorScheme.primary
    val pulseAlpha = remember { Animatable(0.22f) }

    LaunchedEffect(active) {
        if (active) {
            pulseAlpha.snapTo(0.22f)
            repeat(2) {
                pulseAlpha.animateTo(
                    targetValue = 0.40f,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                )
                pulseAlpha.animateTo(
                    targetValue = 0.22f,
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    val deactivationAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (active) snap() else tween(durationMillis = 400),
        label = "jump_highlight_deactivation",
    )

    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply {
            when (outline) {
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Generic -> addPath(outline.path)
            }
        }
        val barWidthPx = 4.dp.toPx()
        val borderWidthPx = 2.dp.toPx()
        val isLtr = layoutDirection == LayoutDirection.Ltr
        val barLeft = if (isLtr) 0f else size.width - barWidthPx

        onDrawWithContent {
            val currentDeactivation = deactivationAlpha
            if (currentDeactivation <= 0f) {
                drawContent()
                return@onDrawWithContent
            }

            val tintAlpha = pulseAlpha.value * currentDeactivation
            val outlineAlpha = currentDeactivation

            // Background accent tint behind content
            if (tintAlpha > 0f) {
                clipPath(path) {
                    drawRect(
                        color = primary.copy(alpha = tintAlpha),
                    )
                }
            }

            // Draw content
            drawContent()

            // 4 dp solid side bar on start edge and 2 dp border along outline
            clipPath(path) {
                drawRect(
                    color = primary.copy(alpha = outlineAlpha),
                    topLeft = Offset(barLeft, 0f),
                    size = Size(barWidthPx, size.height),
                )
                drawPath(
                    path = path,
                    color = primary.copy(alpha = outlineAlpha),
                    style = Stroke(width = borderWidthPx * 2f),
                )
            }
        }
    }
}
