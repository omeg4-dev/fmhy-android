package io.github.omeg4dev.fmhy.browser

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.theme.FMHYTheme
import kotlinx.coroutines.launch

class BrowserActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as FmhyApp).container
        val dataUri = intent?.dataString
        val entryId = intent?.getStringExtra(EXTRA_ENTRY_ID)

        lifecycleScope.launch {
            container.tabManager.restoreTabsIfNeeded(dataUri, entryId)
        }

        setContent {
            val appearance by container.settingsRepository.appearance.collectAsStateWithLifecycle(initialValue = Appearance())
            val isSystemDark = isSystemInDarkTheme()
            val dark = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            DisposableEffect(dark, appearance.themeMode) {
                val statusBarStyle = when (appearance.themeMode) {
                    ThemeMode.SYSTEM -> SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                    ThemeMode.LIGHT -> SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    ThemeMode.DARK -> SystemBarStyle.dark(Color.TRANSPARENT)
                }
                val navBarStyle = when (appearance.themeMode) {
                    ThemeMode.SYSTEM -> SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                    ThemeMode.LIGHT -> SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    ThemeMode.DARK -> SystemBarStyle.dark(Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = statusBarStyle,
                    navigationBarStyle = navBarStyle,
                )
                container.tabManager.attachActivity(this@BrowserActivity, dark)
                onDispose {}
            }

            FMHYTheme(appearance) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BrowserScreen(
                        tabManager = container.tabManager,
                        adblockService = container.adblockService,
                        settingsRepository = container.settingsRepository,
                        bookmarksRepository = container.bookmarks,
                        datasetSearchEngine = container.searchEngine,
                        browserHooks = container.browserHooks,
                        isAppDark = dark,
                        onFinishActivity = { finish() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = (application as FmhyApp).container
        container.tabManager.attachActivity(this, container.tabManager.isAppDark)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val container = (application as FmhyApp).container
        val url = intent.dataString ?: return
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
        val newTab = intent.getBooleanExtra(EXTRA_NEW_TAB, true)

        if (!newTab) {
            val existing = container.tabManager.findTabByUrl(url)
            if (existing != null) {
                container.tabManager.selectTab(existing.id)
                return
            }
        }
        container.tabManager.openTab(url = url, entryId = entryId, isPrivate = false, select = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        val container = (application as FmhyApp).container
        container.tabManager.detachActivity()
    }

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
        const val EXTRA_NEW_TAB = "new_tab"

        fun intent(context: Context, url: String, entryId: String? = null, newTab: Boolean = true): Intent {
            return Intent(context, BrowserActivity::class.java).apply {
                data = Uri.parse(url)
                putExtra(EXTRA_ENTRY_ID, entryId)
                putExtra(EXTRA_NEW_TAB, newTab)
            }
        }
    }
}
