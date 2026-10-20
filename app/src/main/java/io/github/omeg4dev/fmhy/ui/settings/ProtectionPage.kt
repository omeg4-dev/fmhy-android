package io.github.omeg4dev.fmhy.ui.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Deprecated("Merged into BlockingPage", ReplaceWith("BlockingPage(viewModel, onBack, modifier = modifier, extraRows = extraRows)"))
@Composable
fun ProtectionPage(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    BlockingPage(
        viewModel = viewModel,
        onBack = onBack,
        modifier = modifier,
        extraRows = extraRows,
    )
}
