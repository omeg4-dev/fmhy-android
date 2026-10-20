package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Deprecated("Merged into BrowserPage", ReplaceWith("BrowserPage(viewModel, onBack, modifier = modifier, extraRows = extraRows)"))
@Composable
fun PagesPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    BrowserPage(
        viewModel = viewModel,
        onBack = onBack,
        modifier = modifier,
        extraRows = extraRows,
    )
}
