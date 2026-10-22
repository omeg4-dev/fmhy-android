package io.github.omeg4dev.fmhy.ui.whatsnew

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.omeg4dev.fmhy.data.model.ChangeKind
import io.github.omeg4dev.fmhy.theme.LocalIsDarkTheme

data class ChangeTypeLook(
    val kind: ChangeKind,
    val label: String,
    val icon: ImageVector,
    val explanation: String,
    val contentColor: Color,
    val containerColor: Color,
)

object ChangeTypeStyle {

    fun label(kind: ChangeKind): String = when (kind) {
        ChangeKind.ADDED -> "New"
        ChangeKind.REMOVED -> "Removed"
        ChangeKind.RENAMED -> "Renamed"
        ChangeKind.URL_CHANGED -> "New link"
        ChangeKind.MOVED -> "Moved"
        ChangeKind.STARRED -> "Starred"
        ChangeKind.UNSTARRED -> "Unstarred"
        ChangeKind.EDITED -> "Edited"
    }

    fun explanation(kind: ChangeKind): String = when (kind) {
        ChangeKind.ADDED -> "Added to FMHY"
        ChangeKind.REMOVED -> "Taken off FMHY — tap to see replacements"
        ChangeKind.RENAMED -> "Same site, new name"
        ChangeKind.URL_CHANGED -> "Same site, new address"
        ChangeKind.MOVED -> "Moved to another section"
        ChangeKind.STARRED -> "Now a recommended pick"
        ChangeKind.UNSTARRED -> "No longer starred"
        ChangeKind.EDITED -> "Description or extra links changed"
    }

    fun icon(kind: ChangeKind): ImageVector = when (kind) {
        ChangeKind.ADDED -> Icons.Rounded.Add
        ChangeKind.REMOVED -> Icons.Rounded.Remove
        ChangeKind.RENAMED -> Icons.Rounded.DriveFileRenameOutline
        ChangeKind.URL_CHANGED -> Icons.Rounded.Link
        ChangeKind.MOVED -> Icons.AutoMirrored.Rounded.DriveFileMove
        ChangeKind.STARRED -> Icons.Rounded.Star
        ChangeKind.UNSTARRED -> Icons.Rounded.StarOutline
        ChangeKind.EDITED -> Icons.Rounded.Edit
    }

    @Composable
    fun look(kind: ChangeKind): ChangeTypeLook {
        val colorScheme = MaterialTheme.colorScheme
        val isDark = LocalIsDarkTheme.current

        val rawBase = when (kind) {
            ChangeKind.ADDED -> Color(0xFF2E7D32)
            ChangeKind.REMOVED -> Color(0xFFE53935)
            ChangeKind.RENAMED -> Color(0xFF1E88E5)
            ChangeKind.URL_CHANGED -> Color(0xFF00897B)
            ChangeKind.MOVED -> Color(0xFF7E57C2)
            ChangeKind.STARRED -> Color(0xFFF9A825)
            ChangeKind.UNSTARRED -> colorScheme.outline
            ChangeKind.EDITED -> Color(0xFFEF6C00)
        }

        // Removed stays a clear red; the other kinds are nudged toward the accent so they sit in the theme
        val isRemoved = kind == ChangeKind.REMOVED
        val base = if (isRemoved) rawBase else lerp(rawBase, colorScheme.primary, 0.20f)
        val contentColor = if (isDark) {
            lerp(base, Color.White, if (isRemoved) 0.20f else 0.35f)
        } else {
            lerp(base, Color.Black, if (isRemoved) 0.15f else 0.25f)
        }
        val containerColor = base.copy(alpha = if (isRemoved) 0.20f else 0.16f).compositeOver(colorScheme.surface)

        return ChangeTypeLook(
            kind = kind,
            label = label(kind),
            icon = icon(kind),
            explanation = explanation(kind),
            contentColor = contentColor,
            containerColor = containerColor,
        )
    }
}

@Composable
fun ChangeKind.style(): ChangeTypeLook = ChangeTypeStyle.look(this)

fun ChangeKind.label(): String = ChangeTypeStyle.label(this)

fun ChangeKind.explanation(): String = ChangeTypeStyle.explanation(this)

fun ChangeKind.icon(): ImageVector = ChangeTypeStyle.icon(this)
