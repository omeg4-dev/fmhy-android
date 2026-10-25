package io.github.omeg4dev.fmhy

import android.content.Intent
import android.graphics.Color
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.HomePrefs
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.theme.FMHYTheme
import io.github.omeg4dev.fmhy.ui.common.GesturePrefs
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    private val intentFlow = MutableSharedFlow<Intent>(replay = 1, extraBufferCapacity = 5)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val container = (application as FmhyApp).container

        handleIntent(intent, container)
        intent?.let { intentFlow.tryEmit(it) }

        setContent {
            val appearance by container.settingsRepository.appearance.collectAsStateWithLifecycle(initialValue = Appearance())
            val homePrefs by container.settingsRepository.homePrefs.collectAsStateWithLifecycle(initialValue = HomePrefs())
            val gesturePrefs = remember(homePrefs.swipeToBookmark, homePrefs.swapTapHold) {
                GesturePrefs(
                    swipeToBookmark = homePrefs.swipeToBookmark,
                    swapTapHold = homePrefs.swapTapHold,
                )
            }
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
                onDispose {}
            }

            FMHYTheme(appearance, gesturePrefs) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainNavigation(intentFlow = intentFlow)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, (application as FmhyApp).container)
        intentFlow.tryEmit(intent)
    }

    private fun handleIntent(intent: Intent?, container: AppContainer) {
        if (intent == null) return
        if (BuildConfig.DEBUG && intent.hasExtra("debug_tracker_url")) {
            container.debugTrackerUrl = intent.getStringExtra("debug_tracker_url")
        }
        // `open` extras (launcher shortcuts, widget taps) are routed in MainNavigation's intentFlow collector,
        // which is replayed on cold start; see shortcutDestination().
    }
}
