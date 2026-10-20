package io.github.omeg4dev.fmhy.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CalloutBox(
    type: String,
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val normType = type.trim().uppercase()
    val isWarning = normType == "WARNING"
    val isDanger = normType == "DANGER"
    val isTip = normType == "TIP"

    val containerColor = when {
        isWarning || isDanger -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        isTip -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    }
    val borderColor = when {
        isWarning || isDanger -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        isTip -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
    }
    val accentColor = when {
        isWarning || isDanger -> MaterialTheme.colorScheme.error
        isTip -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val icon = when {
        isWarning -> Icons.Outlined.WarningAmber
        isDanger -> Icons.Outlined.ErrorOutline
        isTip -> Icons.Outlined.Lightbulb
        else -> Icons.Outlined.Info
    }
    val label = when {
        isWarning -> "WARNING"
        isDanger -> "DANGER"
        isTip -> "TIP"
        normType == "NOTE" -> "NOTE"
        else -> "INFO"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp),
                )
                val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() && !it.equals(label, ignoreCase = true) }
                val headerText = if (cleanTitle != null) "$label — $cleanTitle" else label
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}
