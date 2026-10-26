package io.github.omeg4dev.fmhy.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import io.github.omeg4dev.fmhy.di.AppContainer
import io.github.omeg4dev.fmhy.R
import io.github.omeg4dev.fmhy.ui.common.rememberLinkOpener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    viewModel: SettingsViewModel,
    container: AppContainer,
    onBack: () -> Unit,
    onShowSnackbar: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    extraRows: LazyListScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val linkOpener = rememberLinkOpener(container, onShowSnackbar)

    val eAboutFmhy = SettingsCatalog.aboutAboutFmhy
    val eVersion = SettingsCatalog.aboutVersion
    val eRunSetupAgain = SettingsCatalog.aboutRunSetupAgain
    val eOpenFmhyLinks = SettingsCatalog.aboutOpenFmhyLinks
    val eGithubRepo = SettingsCatalog.aboutGithubRepo

    val sectionSettingIds = remember {
        listOf(
            listOf(
                eAboutFmhy.id,
                eVersion.id,
                eRunSetupAgain.id,
                eOpenFmhyLinks.id,
                eGithubRepo.id,
            )
        )
    }
    val listState = rememberFocusedListState(sectionSettingIds)

    val versionName = remember(context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: Exception) {
            null
        } ?: "1.0.0"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SettingsSection(title = "About") {
                    InfoRow(
                        title = eAboutFmhy.title,
                        value = "Unofficial client for FMHY. Data: github.com/fmhy/edit. GPL-3.0.",
                        icon = Icons.Default.Code,
                        settingId = eAboutFmhy.id,
                    )
                    SettingsDivider(hasIcon = true)
                    InfoRow(
                        title = eVersion.title,
                        value = versionName,
                        icon = Icons.Default.Info,
                        settingId = eVersion.id,
                    )
                    SettingsDivider(hasIcon = true)
                    NavRow(
                        title = eRunSetupAgain.title,
                        summary = eRunSetupAgain.description,
                        icon = Icons.Default.Restore,
                        settingId = eRunSetupAgain.id,
                        onClick = {
                            viewModel.setOnboardingVersion(0)
                        },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsDivider(hasIcon = true)
                        NavRow(
                            title = eOpenFmhyLinks.title,
                            summary = eOpenFmhyLinks.description,
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            settingId = eOpenFmhyLinks.id,
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            },
                        )
                    }
                    SettingsDivider(hasIcon = true)
                    NavRow(
                        title = eGithubRepo.title,
                        summary = eGithubRepo.description,
                        icon = ImageVector.vectorResource(R.drawable.ic_github),
                        settingId = eGithubRepo.id,
                        onClick = {
                            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                            val activeNetwork = connectivityManager?.activeNetwork
                            val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
                            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                            if (!hasInternet) {
                                onShowSnackbar("You're offline. Connect to the internet to open the GitHub page.")
                            } else {
                                linkOpener.openUrlDirectly(
                                    "https://github.com/omeg4-dev/fmhy-android",
                                    recordHistory = false,
                                )
                            }
                        },
                    )
                }
            }

            extraRows()
        }
    }
}
