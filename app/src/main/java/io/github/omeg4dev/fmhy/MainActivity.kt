package io.github.omeg4dev.fmhy

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.omeg4dev.fmhy.data.Appearance
import io.github.omeg4dev.fmhy.data.ThemeMode
import io.github.omeg4dev.fmhy.theme.FMHYTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)

    val container = (application as FmhyApp).container

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
        onDispose {}
      }

      FMHYTheme(appearance) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation()
        }
      }
    }
  }
}
