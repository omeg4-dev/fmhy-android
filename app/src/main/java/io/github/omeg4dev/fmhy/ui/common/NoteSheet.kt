package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.data.doc.DocFetcher
import io.github.omeg4dev.fmhy.data.doc.DocResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(
    opener: LinkOpener,
    docFetcher: DocFetcher,
) {
    val note = opener.pendingNote ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var result by remember(note.path) { mutableStateOf<DocResult?>(null) }
    var isLoading by remember(note.path) { mutableStateOf(true) }

    LaunchedEffect(note.path) {
        isLoading = true
        result = docFetcher.get(note.path)
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = { opener.dismissNote() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Header: Icon + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_link_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            // Content
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                }
                result is DocResult.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Could not load note content offline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { opener.openNoteDirectly(note.originalUrl) },
                        ) {
                            Text("Open on GitHub")
                        }
                    }
                }
                result is DocResult.Success -> {
                    val success = result as DocResult.Success
                    MarkdownBlocks(
                        markdown = success.text,
                        onOpenUrl = { url ->
                            opener.openUrl(url)
                        },
                    )
                }
            }
        }
    }
}
