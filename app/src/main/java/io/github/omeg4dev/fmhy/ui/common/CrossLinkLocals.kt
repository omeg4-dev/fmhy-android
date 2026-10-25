package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.omeg4dev.fmhy.data.link.CrossLinks

val LocalCrossLinks = staticCompositionLocalOf<CrossLinks?> { null }

/** Decodes pastebin-hidden links for Copy / Share (Open goes through [LinkOpener]). */
val LocalPasteDecoder = staticCompositionLocalOf<PasteDecoder?> { null }
