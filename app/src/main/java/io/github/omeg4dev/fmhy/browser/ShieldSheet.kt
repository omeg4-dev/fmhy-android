package io.github.omeg4dev.fmhy.browser

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Javascript
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.browser.adblock.AdblockStatus
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.BrowserPrefs
import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.ui.settings.SwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldSheet(
    tab: TabState,
    adblockStatus: AdblockStatus,
    blockingPrefs: BlockingPrefs,
    protectionPrefs: ProtectionPrefs,
    browserPrefs: BrowserPrefs,
    isAppDark: Boolean,
    onDismiss: () -> Unit,
    onUpdateSitePref: (update: (io.github.omeg4dev.fmhy.data.user.SitePrefs) -> io.github.omeg4dev.fmhy.data.user.SitePrefs) -> Unit,
    onResetDefaults: () -> Unit,
    onHideElement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val host = try {
        Uri.parse(tab.url).host.orEmpty()
    } catch (_: Exception) {
        ""
    }

    val sitePrefs = tab.sitePrefs
    val hasOverrides = sitePrefs != null && (
        sitePrefs.adblock != null ||
        sitePrefs.popups != null ||
        sitePrefs.javascript != null ||
        sitePrefs.desktop != null ||
        sitePrefs.dark != null
    )

    // Effective values
    val adblockOn = sitePrefs?.adblock ?: blockingPrefs.adblock
    val popupsBlockerOn = sitePrefs?.popups?.let { !it } ?: protectionPrefs.popupBlocker
    val jsOn = sitePrefs?.javascript ?: browserPrefs.javascript
    val desktopOn = sitePrefs?.desktop ?: (tab.desktopSite || browserPrefs.desktopByDefault)
    val darkOn = sitePrefs?.dark ?: (browserPrefs.forceDark && isAppDark)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = host.ifEmpty { "This site" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            targetState = tab.blocked,
                            label = "blockedCount",
                        ) { count ->
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (adblockOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (adblockOn) "blocked on this page" else "Ad blocking is off",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (hasOverrides) {
                    TextButton(onClick = onResetDefaults) {
                        Text("Reset to defaults")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Switches for THIS SITE using settings SwitchRow
            SwitchRow(
                icon = Icons.Default.Shield,
                title = "Ad blocking",
                checked = adblockOn,
                onChange = { v -> onUpdateSitePref { it.copy(adblock = v) } },
            )

            SwitchRow(
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                title = "Block pop-ups",
                checked = popupsBlockerOn,
                onChange = { v -> onUpdateSitePref { it.copy(popups = !v) } },
            )

            SwitchRow(
                icon = Icons.Default.Javascript,
                title = "JavaScript",
                checked = jsOn,
                onChange = { v -> onUpdateSitePref { it.copy(javascript = v) } },
            )

            SwitchRow(
                icon = Icons.Default.DesktopWindows,
                title = "Desktop site",
                checked = desktopOn,
                onChange = { v -> onUpdateSitePref { it.copy(desktop = v) } },
            )

            SwitchRow(
                icon = Icons.Default.DarkMode,
                title = "Dark page",
                checked = darkOn,
                onChange = { v -> onUpdateSitePref { it.copy(dark = v) } },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Button "Hide an element..."
            Button(
                onClick = onHideElement,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hide an element…")
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Footer
            val updatedText = formatRelativeTime(adblockStatus.lastUpdated)
            Text(
                text = "Filter lists: ${adblockStatus.ruleCount} rules · updated $updatedText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun formatRelativeTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return "never"
    val diffMs = System.currentTimeMillis() - timestamp
    if (diffMs < 0) return "just now"
    val secs = diffMs / 1000
    if (secs < 60) return "just now"
    val mins = secs / 60
    if (mins < 60) return "${mins}m ago"
    val hours = mins / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    return "${days}d ago"
}