package io.github.omeg4dev.fmhy.browser

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.omeg4dev.fmhy.FmhyApp
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.theme.FMHYTheme
import kotlinx.coroutines.launch

class BrowserActivity : ComponentActivity() {

    private var isInPipMode by mutableStateOf(false)
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as FmhyApp).container
        val dataUri = intent?.dataString
        val entryId = intent?.getStringExtra(EXTRA_ENTRY_ID)

        val hooks = container.browserHooks as? DefaultBrowserHooks
        hooks?.onShowCustomViewListener = {
            originalOrientation = requestedOrientation
            if (container.tabManager.currentBrowserPrefs.videoAutoRotate) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        hooks?.onHideCustomViewListener = {
            requestedOrientation = originalOrientation
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }

        container.tabManager.onVideoPlayingChanged = { isPlaying ->
            val keepOn = container.tabManager.currentBrowserPrefs.keepScreenOn
            if (isPlaying && keepOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

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
                        isInPip = isInPipMode,
                        onFinishActivity = { finish() },
                    )
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val container = (application as FmhyApp).container
        val prefs = container.tabManager.currentBrowserPrefs
        val hooks = container.browserHooks as? DefaultBrowserHooks
        val activeTab = container.tabManager.getActiveTabRecord()
        val isPlaying = activeTab?.isVideoPlaying == true || hooks?.customView != null
        if (prefs.pictureInPicture && isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {}
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
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
        if (isFinishing && container.tabManager.tabs.value.isEmpty() && container.tabManager.currentPrivacyPrefs.clearDataOnExit) {
            container.tabManager.clearAllBrowsingData()
        }
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
