package io.github.omeg4dev.fmhy.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val FmhyBlue = Color(0xFF5D99DA)      // swarm-500, used on dark backgrounds
val FmhyBlueDeep = Color(0xFF3A69BB)  // swarm-700, used on light backgrounds (contrast)

/** FMHY's light blue for small brand touches; darker variant on light themes. */
@Composable
fun fmhyBlue(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) FmhyBlue else FmhyBlueDeep
