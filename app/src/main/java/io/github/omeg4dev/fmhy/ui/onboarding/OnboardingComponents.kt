package io.github.omeg4dev.fmhy.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RecommendedPill(
    modifier: Modifier = Modifier,
    text: String = "Recommended",
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun OptionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    extraContent: (@Composable () -> Unit)? = null,
    isRecommended: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingBadge: String? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isRecommended) {
                        RecommendedPill()
                    }
                    if (trailingBadge != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape,
                        ) {
                            Text(
                                text = trailingBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (extraContent != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    extraContent()
                }
            }
        }
    }
}

/**
 * Animated FMHY wordmark: each letter scales 0.6->1 and fades in with 80 ms stagger and spring.
 * Uses the flat per-word colors from FmhyWordmark.kt.
 */
@Composable
fun AnimatedFmhyWordmark(
    modifier: Modifier = Modifier,
) {
    val words = remember {
        listOf(
            "F" to Color(0xFF00E5FF),
            "M" to Color(0xFF7C5CFF),
            "H" to Color(0xFFFF3EC9),
            "Y" to Color(0xFFFFC14D),
        )
    }

    Row(
        modifier = modifier.semantics { contentDescription = "FMHY" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        words.forEachIndexed { index, (letter, color) ->
            val anim = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(index * 80L)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = 0.6f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
            val scale = 0.6f + 0.4f * anim.value
            val alpha = anim.value.coerceIn(0f, 1f)

            Text(
                text = letter,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = color,
                ),
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            )
        }
    }
}

private data class ConfettiParticle(
    val color: Color,
    val isCircle: Boolean,
    val sizeDp: Float,
    val vxDp: Float,
    val vyDp: Float,
    val rotationSpeed: Float,
)

/**
 * 96 dp primary circle with a check that draws itself (Path trim 0->1 over 500 ms)
 * and a one-shot confetti burst (≈40 small rounded rects/circles in theme colours)
 * flying out with gravity over 1.2 s.
 */
@Composable
fun DoneCelebration(
    modifier: Modifier = Modifier,
    onCheckFinished: () -> Unit = {},
) {
    val haptics = rememberHaptics()
    val checkProgress = remember { Animatable(0f) }
    val confettiProgress = remember { Animatable(0f) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    LaunchedEffect(Unit) {
        launch {
            checkProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = Motion.EmphasizedDecelerate),
            )
            haptics.success()
            onCheckFinished()
        }
        launch {
            confettiProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1200, easing = LinearEasing),
            )
        }
    }

    val particles = remember(primaryColor, secondaryColor, tertiaryColor) {
        val colors = listOf(primaryColor, secondaryColor, tertiaryColor)
        val rnd = kotlin.random.Random(1337)
        List(40) { index ->
            val color = colors[index % colors.size]
            val isCircle = index % 2 == 0
            val size = 6f + rnd.nextFloat() * 4f // 6..10 dp
            val angle = rnd.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 90f + rnd.nextFloat() * 180f // dp/s
            val vx = kotlin.math.cos(angle) * speed
            val vy = kotlin.math.sin(angle) * speed - 60f // initial upward push
            val rotationSpeed = (rnd.nextFloat() - 0.5f) * 720f
            ConfettiParticle(color, isCircle, size, vx, vy, rotationSpeed)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current

        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerPx = Offset(size.width / 2f, size.height / 2f)
            val circleRadiusPx = with(density) { 48.dp.toPx() } // 96 dp circle diameter

            // 1. One-shot confetti burst
            val t = confettiProgress.value * 1.2f // 0..1.2 s
            if (t < 1.2f) {
                val alpha = (1f - (t / 1.2f)).coerceIn(0f, 1f)
                val gravity = with(density) { 260.dp.toPx() } // downward gravity

                particles.forEach { p ->
                    val particleSizePx = with(density) { p.sizeDp.dp.toPx() }
                    val vxPx = with(density) { p.vxDp.dp.toPx() }
                    val vyPx = with(density) { p.vyDp.dp.toPx() }

                    val x = centerPx.x + vxPx * t
                    val y = centerPx.y + vyPx * t + 0.5f * gravity * t * t
                    val pColor = p.color.copy(alpha = alpha)

                    if (p.isCircle) {
                        drawCircle(
                            color = pColor,
                            radius = particleSizePx / 2f,
                            center = Offset(x, y),
                        )
                    } else {
                        rotate(degrees = p.rotationSpeed * t, pivot = Offset(x, y)) {
                            drawRoundRect(
                                color = pColor,
                                topLeft = Offset(x - particleSizePx / 2f, y - particleSizePx / 3f),
                                size = Size(particleSizePx, particleSizePx * 0.6f),
                                cornerRadius = CornerRadius(3f, 3f),
                            )
                        }
                    }
                }
            }

            // 2. 96 dp primary circle
            drawCircle(
                color = primaryColor,
                radius = circleRadiusPx,
                center = centerPx,
            )

            // 3. Animated checkmark trim (0->1 over 500 ms)
            if (checkProgress.value > 0.001f) {
                val checkWidthPx = with(density) { 42.dp.toPx() }
                val checkHeightPx = with(density) { 30.dp.toPx() }
                val checkLeft = centerPx.x - checkWidthPx * 0.5f
                val checkTop = centerPx.y - checkHeightPx * 0.45f

                val p1 = Offset(checkLeft, checkTop + checkHeightPx * 0.48f)
                val p2 = Offset(checkLeft + checkWidthPx * 0.36f, checkTop + checkHeightPx)
                val p3 = Offset(checkLeft + checkWidthPx, checkTop)

                val fullPath = Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                }

                val pathMeasure = PathMeasure()
                pathMeasure.setPath(fullPath, false)
                val trimmedPath = Path()
                pathMeasure.getSegment(0f, pathMeasure.length * checkProgress.value, trimmedPath, true)

                drawPath(
                    path = trimmedPath,
                    color = onPrimaryColor,
                    style = Stroke(
                        width = with(density) { 5.dp.toPx() },
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}
