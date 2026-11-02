package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private const val CHECK_LINK_PATH_DATA =
    "M10.5,2 L3.5,5 V11 C3.5,15.5 6.5,19.4 10.5,20.8 " +
    "M10.5,2 L17.5,5 V9.5 " +
    "M12.2,15.5 A3.8,3.8 0 1 0 19.8,15.5 A3.8,3.8 0 1 0 12.2,15.5 " +
    "M18.7,18.2 L21.5,21"

val CheckLinkIcon: ImageVector
    get() {
        val current = _checkLink
        if (current != null) {
            return current
        }
        val vector = ImageVector.Builder(
            name = "CheckLink",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(CHECK_LINK_PATH_DATA).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()
        _checkLink = vector
        return vector
    }

private var _checkLink: ImageVector? = null
