package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

fun parseMarkdownToAnnotatedString(text: String, linkColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        // Matches **bold** or [label](url)
        val pattern = Regex("""(\*\*(.*?)\*\*)|(\[(.*?)\]\((.*?)\))""")
        val matches = pattern.findAll(text)
        for (match in matches) {
            if (match.range.first > idx) {
                append(text.substring(idx, match.range.first))
            }
            val boldText = match.groups[2]?.value
            val linkLabel = match.groups[4]?.value
            val linkUrl = match.groups[5]?.value

            if (boldText != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(boldText)
                }
            } else if (linkLabel != null && linkUrl != null) {
                withLink(
                    LinkAnnotation.Url(
                        url = linkUrl,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium,
                            )
                        )
                    )
                ) {
                    append(linkLabel)
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
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = parseMarkdownToAnnotatedString(markdown, linkColor)
    Text(
        text = annotated,
        modifier = modifier,
        color = color,
    )
}
