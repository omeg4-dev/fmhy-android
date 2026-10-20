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
import io.github.omeg4dev.fmhy.browser.media.BackgroundMediaService
import io.github.omeg4dev.fmhy.browser.media.shouldKeepPlaying
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
    private var pipStateListener: (() -> Unit)? = null

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
        if (savedInstanceState != null) {
            originalOrientation = savedInstanceState.getInt(STATE_ORIGINAL_ORIENTATION, originalOrientation)
        }

        val container = (application as FmhyApp).container
        val dataUri = intent?.dataString
        val entryId = intent?.getStringExtra(EXTRA_ENTRY_ID)

        val hooks = container.browserHooks as? DefaultBrowserHooks
        val showListener: (android.view.View) -> Unit = {
            originalOrientation = requestedOrientation
            val aspect = container.tabManager.getActiveTabRecord()?.toState()?.videoAspect
            requestedOrientation = fullscreenRequestedOrientation(
                container.tabManager.currentBrowserPrefs.fullscreenOrientation,
                aspect,
            ).let { target -> if (target == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) originalOrientation else target }
            if (container.tabManager.currentBrowserPrefs.keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            window.decorView.post { updatePipParams() }
        }
        showCustomViewListener = showListener
        hooks?.onShowCustomViewListener = showListener

        val hideListener: () -> Unit = {
            requestedOrientation = originalOrientation
            if (container.tabManager.isVideoPlaying() != true || !container.tabManager.currentBrowserPrefs.keepScreenOn) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            window.decorView.post { updatePipParams() }
        }
        hideCustomViewListener = hideListener
        hooks?.onHideCustomViewListener = hideListener

        val pipListener: () -> Unit = { updatePipParams() }
        pipStateListener = pipListener

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
        container.tabManager.onPipStateChanged = pipListener
        lifecycleScope.launch {
            container.settingsRepository.browserPrefs.collect { updatePipParams() }
        }

        lifecycleScope.launch {
            container.tabManager.restoreTabsIfNeeded(dataUri, entryId)
            if (savedInstanceState == null) handleSwitchExtras(intent)
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

    /** True while something is playing that picture-in-picture could show. */
    private fun isPipCandidatePlaying(): Boolean {
        val container = (application as FmhyApp).container
        val hooks = container.browserHooks as? DefaultBrowserHooks
        return container.tabManager.getActiveTabRecord()?.isVideoPlaying == true || hooks?.customView != null
    }

    private fun buildPipParams(): PictureInPictureParams.Builder {
        val container = (application as FmhyApp).container
        val record = container.tabManager.getActiveTabRecord()
        val ratio = pipRatio(record?.videoWidth ?: 0, record?.videoHeight ?: 0)
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(ratio.num, ratio.den))
        try {
            val rect = android.graphics.Rect()
            val wv = record?.webView
            if (wv != null && wv.getGlobalVisibleRect(rect) && !rect.isEmpty) {
                builder.setSourceRectHint(rect)
            }
        } catch (_: Exception) {}
        return builder
    }

    /** Keeps the system's picture-in-picture params (shape, auto-enter) in sync with playback and prefs. */
    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val prefs = (application as FmhyApp).container.tabManager.currentBrowserPrefs
            val builder = buildPipParams()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(prefs.pictureInPicture && prefs.autoPip && isPipCandidatePlaying())
            }
            setPictureInPictureParams(builder.build())
        } catch (_: Exception) {}
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // API 31+ enters on its own via setAutoEnterEnabled; this is the path for API 26-30.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        val prefs = (application as FmhyApp).container.tabManager.currentBrowserPrefs
        if (prefs.pictureInPicture && prefs.autoPip && isPipCandidatePlaying() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(buildPipParams().build())
            } catch (_: Exception) {}
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_ORIGINAL_ORIENTATION, originalOrientation)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
    }

    /**
     * The browser left the foreground. Keeps the current tab playing (and starts the media
     * notification) when background playback is on; otherwise pauses as usual. A video that is
     * showing in the picture-in-picture window keeps playing too.
     */
    private fun enterBackground() {
        val tabManager = (application as FmhyApp).container.tabManager
        val playing = tabManager.getActiveTabRecord()?.isVideoPlaying == true
        val keep = shouldKeepPlaying(tabManager.currentBrowserPrefs.backgroundPlayback, playing, isFinishing)
        val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
        tabManager.onActivityPause(keepPlaying = keep || (inPip && playing && !isFinishing))
        if (keep) BackgroundMediaService.start(this)
    }

    override fun onPause() {
        super.onPause()
        enterBackground()
    }

    override fun onStop() {
        super.onStop()
        // Also covers closing the picture-in-picture window, which stops the activity without a new pause.
        enterBackground()
    }

    override fun onResume() {
        super.onResume()
        val container = (application as FmhyApp).container
        container.tabManager.attachActivity(this, container.tabManager.isAppDark)
        container.tabManager.onActivityResume()
        BackgroundMediaService.stop(this)
        updatePipParams()
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
        if (url.isNullOrBlank()) {
            if (container.tabManager.linkQueue.isRestored.get()) handleSwitchExtras(intent)
        } else {
            val entryId = intent.getStringExtra(EXTRA_ENTRY_ID)
            val newTab = intent.getBooleanExtra(EXTRA_NEW_TAB, true)
            container.tabManager.linkQueue.offerOrExecute(
                QueuedLink(url = url, entryId = entryId, newTab = newTab)
            ) { link, select ->
                container.tabManager.openQueuedLink(link, select)
            }
        }
    }

    /** Home card extras: select a specific tab and/or open the tabs sheet. Consumed once. */
    private fun handleSwitchExtras(intent: Intent?) {
        if (intent == null) return
        val tabManager = (application as FmhyApp).container.tabManager
        val tabId = intent.getLongExtra(EXTRA_SELECT_TAB, -1L)
        if (tabId >= 0) {
            intent.removeExtra(EXTRA_SELECT_TAB)
            tabManager.selectTab(tabId)
        }
        if (intent.getBooleanExtra(EXTRA_SHOW_TABS, false)) {
            intent.removeExtra(EXTRA_SHOW_TABS)
            tabManager.requestShowTabs()
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
        if (container.tabManager.onPipStateChanged === pipStateListener) {
            container.tabManager.onPipStateChanged = null
        }
        if (isFinishing && container.tabManager.tabs.value.isEmpty() && container.tabManager.currentPrivacyPrefs.clearDataOnExit) {
            container.tabManager.clearAllBrowsingData()
        }
        container.tabManager.detachActivity(this)
    }

    companion object {
        private const val STATE_ORIGINAL_ORIENTATION = "original_orientation"
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
