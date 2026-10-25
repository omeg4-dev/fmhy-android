package io.github.omeg4dev.fmhy.browser

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.data.SearchEngine
import io.github.omeg4dev.fmhy.data.ToolbarPosition
import io.github.omeg4dev.fmhy.data.model.Entry
import io.github.omeg4dev.fmhy.data.model.Tier
import io.github.omeg4dev.fmhy.data.search.SearchEngine as DatasetSearchEngine
import io.github.omeg4dev.fmhy.ui.common.CrossfadeContent

@Composable
fun BrowserEditMode(
    initialText: String,
    searchEngine: SearchEngine,
    datasetSearchEngine: DatasetSearchEngine,
    toolbarPosition: ToolbarPosition = ToolbarPosition.TOP,
    autofocus: Boolean = false,
    onNavigate: (url: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cleanInitial = if (initialText == "about:blank") "" else initialText

    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = cleanInitial,
                selection = TextRange(0, cleanInitial.length),
            )
        )
    }

    val focusRequester = remember { FocusRequester() }
    var suggestions by remember { mutableStateOf<List<Entry>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (autofocus) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(textFieldValue.text) {
        val query = textFieldValue.text.trim()
        if (query.isNotEmpty()) {
            try {
                val results = datasetSearchEngine.search(query, limit = 5)
                suggestions = results.groups.flatMap { it.hits.map { hit -> hit.entry } }.take(5)
            } catch (_: Exception) {
                suggestions = emptyList()
            }
        } else {
            suggestions = emptyList()
        }
    }

    val onSubmit = {
        val resolved = UrlInput.resolve(textFieldValue.text, searchEngine)
        if (resolved.isNotEmpty()) {
            onNavigate(resolved)
        }
    }

    val addressPillField = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .height(64.dp)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Pill-shaped text field: 44 dp tall, full rounded, surfaceContainerHighest, no outline
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.width(8.dp))

                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = "Search or type link",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    },
                )

                if (textFieldValue.text.isNotEmpty()) {
                    IconButton(
                        onClick = { textFieldValue = TextFieldValue("") },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (toolbarPosition == ToolbarPosition.TOP) {
            addressPillField()
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }

        // Main content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val isTyping = textFieldValue.text.trim().isNotEmpty()

            CrossfadeContent(targetState = isTyping) { typing ->
                if (typing) {
                    // Search suggestions list
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(suggestions) { entry ->
                            SuggestionItem(
                                entry = entry,
                                onClick = { onNavigate(entry.url) },
                            )
                            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                        }

                        if (textFieldValue.text.trim().isNotEmpty()) {
                            item {
                                SearchEngineSuggestionItem(
                                    text = textFieldValue.text.trim(),
                                    engine = searchEngine,
                                    onClick = { onSubmit() },
                                )
                            }
                        }
                    }
                } else {
                    // Empty background when not typing
                }
            }
        }

        if (toolbarPosition == ToolbarPosition.BOTTOM) {
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            addressPillField()
        }
    }
}

@Composable
private fun SuggestionItem(
    entry: Entry,
    onClick: () -> Unit,
) {
    val host = try { Uri.parse(entry.url).host.orEmpty() } catch (_: Exception) { "" }
    val isStarred = entry.tier == Tier.STAR || entry.tier == Tier.TOP

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_platform_web),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isStarred) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Image(
                        painter = painterResource(R.drawable.ic_marker_star),
                        contentDescription = "Starred",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (host.isNotEmpty()) {
                Text(
                    text = host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchEngineSuggestionItem(
    text: String,
    engine: SearchEngine,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "Search ${engine.label} for “$text”",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
