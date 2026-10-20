package io.github.omeg4dev.fmhy.browser

import android.app.PictureInPictureParams
import android.content.ComponentCallbacks2
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

    private var showCustomViewListener: ((android.view.View) -> Unit)? = null
    private var hideCustomViewListener: (() -> Unit)? = null
    private var videoPlayingListener: ((Boolean) -> Unit)? = null

    @Suppress("DEPRECATION")
    private val trimMemoryCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                val container = (application as FmhyApp).container
                container.tabManager.onTrimMemory()
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) {}

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {
            val container = (application as FmhyApp).container
            container.tabManager.onTrimMemory()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerComponentCallbacks(trimMemoryCallbacks)

        val container = (application as FmhyApp).container
        val dataUri = intent?.dataString
        val entryId = intent?.getStringExtra(EXTRA_ENTRY_ID)

        val hooks = container.browserHooks as? DefaultBrowserHooks
        val showListener: (android.view.View) -> Unit = {
            originalOrientation = requestedOrientation
            if (container.tabManager.currentBrowserPrefs.videoAutoRotate) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            if (container.tabManager.currentBrowserPrefs.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        showCustomViewListener = showListener
        hooks?.onShowCustomViewListener = showListener

        val hideListener: () -> Unit = {
            requestedOrientation = originalOrientation
            if (container.tabManager.isVideoPlaying() != true || !container.tabManager.currentBrowserPrefs.keepScreenOn) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        }
        hideCustomViewListener = hideListener
        hooks?.onHideCustomViewListener = hideListener

        val videoListener: (Boolean) -> Unit = { isPlaying ->
            val keepOn = container.tabManager.currentBrowserPrefs.keepScreenOn
            if (isPlaying && keepOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        videoPlayingListener = videoListener
        container.tabManager.onVideoPlayingChanged = videoListener

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

    override fun onPause() {
        super.onPause()
        val container = (application as FmhyApp).container
        container.tabManager.onActivityPause()
    }

    override fun onResume() {
        super.onResume()
        val container = (application as FmhyApp).container
        container.tabManager.attachActivity(this, container.tabManager.isAppDark)
        container.tabManager.onActivityResume()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            val container = (application as FmhyApp).container
            container.tabManager.onTrimMemory()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val container = (application as FmhyApp).container
        val url = intent.dataString
        if (!url.isNullOrBlank()) {
            val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
            val newTab = intent.getBooleanExtra(EXTRA_NEW_TAB, true)
            container.tabManager.linkQueue.offerOrExecute(
                QueuedLink(url = url, entryId = entryId, newTab = newTab)
            ) { link, select ->
                container.tabManager.openQueuedLink(link, select)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterComponentCallbacks(trimMemoryCallbacks)
        val container = (application as FmhyApp).container
        val hooks = container.browserHooks as? DefaultBrowserHooks
        if (hooks?.onShowCustomViewListener === showCustomViewListener) {
            hooks?.onShowCustomViewListener = null
        }
        if (hooks?.onHideCustomViewListener === hideCustomViewListener) {
            hooks?.onHideCustomViewListener = null
        }
        if (container.tabManager.onVideoPlayingChanged === videoPlayingListener) {
            container.tabManager.onVideoPlayingChanged = null
        }
        if (isFinishing && container.tabManager.tabs.value.isEmpty() && container.tabManager.currentPrivacyPrefs.clearDataOnExit) {
            container.tabManager.clearAllBrowsingData()
        }
        container.tabManager.detachActivity(this)
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
