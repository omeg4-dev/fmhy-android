package io.github.omeg4dev.fmhy.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.data.model.Section
import io.github.omeg4dev.fmhy.data.search.QueryEdit
import io.github.omeg4dev.fmhy.data.search.Token
import io.github.omeg4dev.fmhy.data.search.TokenKind
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics
import io.github.omeg4dev.fmhy.ui.common.sectionIcon

/** Keyboard setup of the search field: no autocorrect or auto-capitalisation (they rewrite `in:video` and
 *  site names while typing), spaces stay available. */
internal val SearchKeyboardOptions = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Text,
    imeAction = ImeAction.Search,
)

internal val SearchBarShape = RoundedCornerShape(26.dp)

@Composable
internal fun searchBarContainerColor(): Color =
    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f).compositeOver(MaterialTheme.colorScheme.surface)

@Composable
internal fun searchBarBorder(focused: Boolean = false): BorderStroke {
    val borderColor = if (focused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    }
    val animatedColor by animateColorAsState(
        targetValue = borderColor,
        label = "searchBarBorderColor",
    )
    val width = if (focused) 2.dp else 1.dp
    return BorderStroke(width, animatedColor)
}

/**
 * The search bar: parameter pills (`in:video`, `is:starred`, `site:x`, `-word`) followed by the free text.
 * [value] is the field's text ([SENTINEL] + free text, see [applyFieldEdit]); [pills] are the committed
 * parameters of the query. Tapping a pill removes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchBarField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    pills: List<Token>,
    sections: List<Section>,
    onRemovePill: (Token) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    showBack: Boolean,
    onBack: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(pills.size, value.text) { scroll.scrollTo(scroll.maxValue) }
    val hasContent = pills.isNotEmpty() || value.free.isNotEmpty()

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = SearchKeyboardOptions,
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) },
            decorationBox = { inner ->
                // Pills and text wrap; the field grows to three lines, then scrolls vertically.
                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp).verticalScroll(scroll)) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        pills.forEach { t ->
                            ParamPill(
                                label = pillLabel(t, sections),
                                icon = pillIcon(t, sections),
                                onRemove = { onRemovePill(t) },
                            )
                        }
                        Box(
                            modifier = Modifier.widthIn(min = 120.dp).heightIn(min = 28.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (!hasContent) {
                                Text(
                                    "Search FMHY...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    }
                }
            },
        )
        if (hasContent) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Clear, contentDescription = "Clear search")
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
    }
}

@Composable
internal fun ParamPill(
    label: String,
    icon: ImageVector?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Surface(
        onClick = {
            haptics.remove()
            onRemove()
        },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .heightIn(min = 28.dp, max = 28.dp)
            .semantics { contentDescription = "$label, tap to remove" },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = if (icon != null) 8.dp else 10.dp, end = 6.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

internal fun pillIcon(token: Token, sections: List<Section>): ImageVector? = when {
    token.kind == TokenKind.EXCLUDE -> Icons.Outlined.RemoveCircleOutline
    token.key == "in" -> {
        val ids = QueryEdit.resolveSections(token.value, sections)
        if (ids.size == 1) sectionIcon(ids.first()) else Icons.Outlined.Tune
    }
    token.key == "is" -> when (token.value) {
        "starred" -> Icons.Filled.Star
        "top" -> Icons.Outlined.WorkspacePremium
        "b64" -> Icons.Outlined.VisibilityOff
        else -> null
    }
    token.key == "site" -> Icons.Outlined.Language
    else -> null
}
