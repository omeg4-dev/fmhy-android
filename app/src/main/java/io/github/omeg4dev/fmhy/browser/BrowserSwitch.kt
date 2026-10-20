package io.github.omeg4dev.fmhy.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.MainActivity
import io.github.omeg4dev.fmhy.ui.common.Motion
import io.github.omeg4dev.fmhy.ui.common.rememberHaptics

const val EXTRA_SELECT_TAB = "select_tab"
const val EXTRA_SHOW_TABS = "show_tabs"

/** Brings the existing browser instance to the front without opening a new tab. */
fun bringBrowserToFront(context: Context, selectTabId: Long? = null, showTabs: Boolean = false) {
    val intent = Intent(context, BrowserActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (selectTabId != null) putExtra(EXTRA_SELECT_TAB, selectTabId)
        if (showTabs) putExtra(EXTRA_SHOW_TABS, true)
    }
    context.startActivity(intent)
}

/** Brings FMHY (MainActivity) to the front. The browser and its tabs stay alive. */
fun bringFmhyToFront(context: Context) {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/** "Browser · N" pill for the Home and Saved top bars. Visible only when [tabCount] > 0. */
@Composable
fun BrowserSwitchChip(tabCount: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    AnimatedVisibility(
        visible = tabCount > 0,
        enter = fadeIn(Motion.enter()) + scaleIn(Motion.enter(), initialScale = 0.8f),
        exit = fadeOut(Motion.exit()) + scaleOut(Motion.exit(), targetScale = 0.8f),
        modifier = modifier,
    ) {
        Surface(
            onClick = {
                haptics.click()
                bringBrowserToFront(context)
            },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .height(32.dp)
                .semantics { contentDescription = "Open browser, $tabCount tabs" },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp),
            ) {
                Icon(Icons.Outlined.Tab, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Browser · $tabCount", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
