package io.github.omeg4dev.fmhy.update

import android.content.Context
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.work.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient

fun createUpdateController(
    context: Context,
    appScope: CoroutineScope,
    okHttpClient: OkHttpClient,
    settingsRepository: SettingsRepository,
    networkMonitor: NetworkMonitor,
): UpdateController = UpdateControllerImpl(context, appScope, okHttpClient, settingsRepository, networkMonitor)
