package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

fun parseMarkdownToAnnotatedString(
    text: String,
    linkColor: Color,
    onOpenUrl: ((String) -> Unit)? = null,
): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        // Matches ![alt](url), **bold**, `code`, *italic*, or [label](url)
        val pattern = Regex("""(!\[(.*?)\]\((.*?)\))|(\*\*(.*?)\*\*)|(`(.*?)`)|(\*(.*?)\*)|(\[(.*?)\]\((.*?)\))""")
        val matches = pattern.findAll(text)
        for (match in matches) {
            if (match.range.first > idx) {
                append(text.substring(idx, match.range.first))
            }
            val imageAlt = match.groups[2]?.value
            val boldText = match.groups[5]?.value
            val inlineCode = match.groups[7]?.value
            val italicText = match.groups[9]?.value
            val linkLabel = match.groups[11]?.value
            val linkUrl = match.groups[12]?.value

            when {
                imageAlt != null -> {
                    if (imageAlt.isNotBlank()) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = linkColor)) {
                            append("[$imageAlt]")
                        }
                    }
                }
                boldText != null -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(boldText)
                    }
                }
                inlineCode != null -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = linkColor.copy(alpha = 0.12f),
                        )
                    ) {
                        append(" $inlineCode ")
                    }
                }
                italicText != null -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(italicText)
                    }
                }
                linkLabel != null && linkUrl != null -> {
                    val linkStyles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium,
                        )
                    )
                    if (onOpenUrl != null) {
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = linkUrl,
                                styles = linkStyles,
                                linkInteractionListener = { onOpenUrl(linkUrl) },
                            )
                        ) {
                            append(linkLabel)
                        }
                    } else {
                        withLink(
                            LinkAnnotation.Url(
                                url = linkUrl,
                                styles = linkStyles,
                            )
                        ) {
                            append(linkLabel)
                        }
                    }
                }
            }
            idx = match.range.last + 1
        }
        if (idx < text.length) {
            append(text.substring(idx))
        }
    }
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
    val linkColor = MaterialTheme.colorScheme.primary
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
