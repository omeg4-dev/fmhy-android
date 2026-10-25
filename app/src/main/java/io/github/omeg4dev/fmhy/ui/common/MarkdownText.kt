package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow

val LocalMarkdownLinkColor = compositionLocalOf<Color?> { null }

fun parseMarkdownToAnnotatedString(
    text: String,
    linkColor: Color,
    onOpenUrl: ((String) -> Unit)? = null,
): AnnotatedString {
    return parseMarkdownInline(text, linkColor, onOpenUrl)
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onOpenUrl: ((String) -> Unit)? = null,
) {
    val linkColor = LocalMarkdownLinkColor.current ?: MaterialTheme.colorScheme.primary
    val annotated = parseMarkdownToAnnotatedString(markdown, linkColor, onOpenUrl)
    Text(
        text = annotated,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = onTextLayout,
    )
}
