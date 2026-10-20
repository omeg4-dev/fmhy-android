package io.github.omeg4dev.fmhy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.safety.Verdict
import io.github.omeg4dev.fmhy.theme.FMHYTheme
import io.github.omeg4dev.fmhy.ui.check.VerdictCard
import io.github.omeg4dev.fmhy.ui.check.extractFirstUrl
import io.github.omeg4dev.fmhy.ui.common.UnsafeWarningDialog
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener

const val EXTRA_CHECK_LINK_INITIAL = "io.github.omeg4dev.fmhy.CHECK_LINK_INITIAL"

class CheckLinkActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as FmhyApp).container
        val rawText = when (intent?.action) {
            Intent.ACTION_SEND -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            }
            Intent.ACTION_PROCESS_TEXT -> {
                intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
                    ?: intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            }
            else -> intent?.dataString
        } ?: ""

        val extractedUrl = extractFirstUrl(rawText)

        setContent {
            val appearance by container.settingsRepository.appearance.collectAsStateWithLifecycle(initialValue = Appearance())
            FMHYTheme(appearance) {
                CheckLinkBottomSheet(
                    url = extractedUrl,
                    container = container,
                    onDismiss = { finish() },
                    onOpenInFmhy = {
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            putExtra(EXTRA_CHECK_LINK_INITIAL, extractedUrl)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(mainIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun CheckLinkBottomSheet(
    url: String,
    container: io.github.omeg4dev.fmhy.di.AppContainer,
    onDismiss: () -> Unit,
    onOpenInFmhy: () -> Unit,
) {
    BackHandler { onDismiss() }

    val linkOpener = rememberLinkOpener(container, onShowSnackbar = {})
    val isInitialized by container.isInitialized.collectAsStateWithLifecycle()
    val datasetChanges by container.datasetStore.changes.collectAsStateWithLifecycle(initialValue = 0L)
    var verdict by remember { mutableStateOf<Verdict?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    LaunchedEffect(url, isInitialized, datasetChanges) {
        if (container.datasetStore.isAvailable && url.isNotEmpty()) {
            isChecking = true
            verdict = container.safeGuard.check(url)
            isChecking = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent dismiss when clicking sheet itself */ },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Drag handle pill
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(2.dp),
                        )
                )

                Spacer(Modifier.height(16.dp))

                if (!container.datasetStore.isAvailable) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "FMHY data is still loading\u2026",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (verdict != null) {
                    VerdictCard(
                        verdict = verdict!!,
                        onOpenUrl = { linkOpener.openUrl(it) },
                        onOpenInFmhy = onOpenInFmhy,
                    )
                } else if (isChecking) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    UnsafeWarningDialog(linkOpener)
}
