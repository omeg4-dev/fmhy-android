package io.github.omeg4dev.fmhy.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.omeg4dev.fmhy.BuildConfig
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.work.NetworkKind
import io.github.omeg4dev.fmhy.work.NetworkMonitor
import io.github.omeg4dev.fmhy.work.NetworkPolicy
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class UpdateControllerImpl(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
) : UpdateController {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _availableVersion = MutableStateFlow<String?>(null)
    override val availableVersion: StateFlow<String?> = _availableVersion.asStateFlow()

    override val enabled: Boolean = BuildConfig.UPDATER_ENABLED

    private val _pendingMobileAsk = MutableStateFlow(false)
    override val pendingMobileAsk: StateFlow<Boolean> = _pendingMobileAsk.asStateFlow()

    private val _needsInstallPermission = MutableStateFlow(false)
    override val needsInstallPermission: StateFlow<Boolean> = _needsInstallPermission.asStateFlow()

    private var pendingMobileAction: (() -> Unit)? = null
    private var isWaitingForInstallPermission = false

    private var currentRelease: ReleaseInfo? = null
    private var currentApkFile: File? = null

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    init {
        appScope.launch {
            val prefs = settingsRepository.updatePrefs.first()
            val installed = BuildConfig.VERSION_NAME
            if (UpdateCheckPolicy.showSettingsMarker(prefs.knownAvailable, installed)) {
                _availableVersion.value = prefs.knownAvailable
            } else if (prefs.knownAvailable != null) {
                _availableVersion.value = null
                settingsRepository.updateUpdatePrefs { it.copy(knownAvailable = null) }
            }
        }

        appScope.launch {
            combine(
                networkMonitor.kind,
                settingsRepository.dataUsagePrefs,
            ) { kind, dataUsage ->
                kind to dataUsage.appDataWifiOnly
            }.collect { (kind, wifiOnly) ->
                val currentState = _state.value
                if (currentState is UpdateState.WaitingForWifi) {
                    val allowed = NetworkPolicy.allowed(switchOn = wifiOnly, kind = kind, overrideThisTime = false)
                    if (allowed) {
                        if (currentState.release != null) {
                            startDownload(currentState.release)
                        } else {
                            checkInternal(manual = false, overrideMobileData = false)
                        }
                    }
                }
            }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                if (isWaitingForInstallPermission) {
                    if (context.packageManager.canRequestPackageInstalls()) {
                        isWaitingForInstallPermission = false
                        val readyState = _state.value as? UpdateState.ReadyToInstall
                        val release = currentRelease ?: readyState?.release
                        val file = currentApkFile ?: readyState?.filePath?.let { File(it) }
                        if (release != null && file != null && file.exists()) {
                            startPackageInstaller(release, file)
                        }
                    }
                }
            }
        })
    }

    override fun checkOnStartup() {
        appScope.launch {
            val prefs = settingsRepository.updatePrefs.first()
            val now = System.currentTimeMillis()
            if (UpdateCheckPolicy.shouldCheckOnStartup(prefs.autoCheck, prefs.lastCheckMs, now)) {
                checkInternal(manual = false, overrideMobileData = false)
            }
        }
    }

    override fun checkNow(overrideMobileData: Boolean) {
        checkInternal(manual = true, overrideMobileData = overrideMobileData)
    }

    private fun checkInternal(manual: Boolean, overrideMobileData: Boolean) {
        if (_state.value is UpdateState.Checking) return
        checkJob?.cancel()
        checkJob = appScope.launch(Dispatchers.IO) {
            val dataUsage = settingsRepository.dataUsagePrefs.first()
            val kind = networkMonitor.kind.value
            val decision = UpdateCheckPolicy.networkDecision(
                manual = manual,
                wifiOnlySwitch = dataUsage.appDataWifiOnly,
                kind = kind,
                overrideThisTime = overrideMobileData,
            )

            when (decision) {
                UpdateCheckPolicy.NetworkDecision.ASK_MOBILE_DATA -> {
                    pendingMobileAction = { checkInternal(manual = true, overrideMobileData = true) }
                    _pendingMobileAsk.value = true
                    return@launch
                }
                UpdateCheckPolicy.NetworkDecision.WAIT_FOR_WIFI -> {
                    _state.value = UpdateState.WaitingForWifi(null)
                    return@launch
                }
                UpdateCheckPolicy.NetworkDecision.PROCEED -> {}
            }

            _state.value = UpdateState.Checking(manual = manual)

            val client = okHttpClient.newBuilder()
                .callTimeout(15, TimeUnit.SECONDS)
                .build()

            val url = "https://api.github.com/repos/${BuildConfig.RELEASE_REPO}/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .build()

            val now = System.currentTimeMillis()
            try {
                client.newCall(request).execute().use { response ->
                    settingsRepository.updateUpdatePrefs { it.copy(lastCheckMs = now) }
                    if (!response.isSuccessful) {
                        handleCheckFailure(manual)
                        return@launch
                    }
                    val body = response.body?.string()
                    if (body == null) {
                        handleCheckFailure(manual)
                        return@launch
                    }
                    val release = ReleaseParser.parse(body)
                    if (release == null) {
                        handleCheckFailure(manual)
                        return@launch
                    }

                    currentRelease = release
                    val installed = BuildConfig.VERSION_NAME
                    val isNewer = ReleaseParser.compareVersions(release.version, installed) > 0

                    if (isNewer) {
                        settingsRepository.updateUpdatePrefs { it.copy(knownAvailable = release.version) }
                        _availableVersion.value = release.version

                        val prefs = settingsRepository.updatePrefs.first()
                        val shouldPrompt = manual || UpdateCheckPolicy.shouldShowPrompt(
                            available = release.version,
                            installed = installed,
                            skipped = prefs.skippedVersion,
                            laterUntilMs = prefs.laterUntilMs,
                            nowMs = now,
                        )
                        if (shouldPrompt) {
                            _state.value = UpdateState.Available(release)
                        } else {
                            _state.value = UpdateState.Idle
                        }
                    } else {
                        settingsRepository.updateUpdatePrefs { it.copy(knownAvailable = null) }
                        _availableVersion.value = null
                        if (manual) {
                            _state.value = UpdateState.UpToDate(manual = true)
                        } else {
                            _state.value = UpdateState.Idle
                        }
                    }
                }
            } catch (e: Exception) {
                settingsRepository.updateUpdatePrefs { it.copy(lastCheckMs = now) }
                Log.e("UpdateController", "Update check failed: ${e.message}", e)
                handleCheckFailure(manual)
            }
        }
    }

    private fun handleCheckFailure(manual: Boolean) {
        if (manual) {
            _state.value = UpdateState.CheckFailed("Couldn't check for updates. Try again later.")
        } else {
            _state.value = UpdateState.Idle
        }
    }

    override fun install(overrideMobileData: Boolean) {
        val release = currentRelease
            ?: (_state.value as? UpdateState.Available)?.release
            ?: (_state.value as? UpdateState.WaitingForWifi)?.release
            ?: (_state.value as? UpdateState.ReadyToInstall)?.release
            ?: (_state.value as? UpdateState.DownloadFailed)?.release
            ?: return

        currentRelease = release

        val updatesDir = File(context.cacheDir, "updates")
        val finalFile = File(updatesDir, "fmhy-${release.version}.apk")
        if (finalFile.exists() && (release.apkSize <= 0 || finalFile.length() == release.apkSize)) {
            currentApkFile = finalFile
            _state.value = UpdateState.ReadyToInstall(release, finalFile.absolutePath)
            proceedToInstall(release, finalFile)
            return
        }

        appScope.launch {
            val wifiOnly = settingsRepository.dataUsagePrefs.first().appDataWifiOnly
            val kind = networkMonitor.kind.value
            if (wifiOnly && kind == NetworkKind.MOBILE && !overrideMobileData) {
                _state.value = UpdateState.WaitingForWifi(release)
                return@launch
            }
            startDownload(release)
        }
    }

    private fun startDownload(release: ReleaseInfo) {
        downloadJob?.cancel()
        downloadJob = appScope.launch(Dispatchers.IO) {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) updatesDir.mkdirs()

            updatesDir.listFiles()?.forEach { file ->
                if (!file.name.startsWith("fmhy-${release.version}")) {
                    file.delete()
                }
            }

            val partFile = File(updatesDir, "fmhy-${release.version}.apk.part")
            val finalFile = File(updatesDir, "fmhy-${release.version}.apk")

            if (finalFile.exists()) {
                if (release.apkSize <= 0 || finalFile.length() == release.apkSize) {
                    currentApkFile = finalFile
                    _state.value = UpdateState.ReadyToInstall(release, finalFile.absolutePath)
                    proceedToInstall(release, finalFile)
                    return@launch
                } else {
                    finalFile.delete()
                }
            }

            var downloadedBytes = 0L
            val requestBuilder = Request.Builder().url(release.apkUrl)

            if (partFile.exists() && partFile.length() > 0) {
                downloadedBytes = partFile.length()
                requestBuilder.header("Range", "bytes=$downloadedBytes-")
            }

            _state.value = UpdateState.Downloading(release, downloadedBytes, release.apkSize)

            try {
                val callClient = okHttpClient.newBuilder()
                    .callTimeout(0, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                callClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        if (response.code == 416) {
                            partFile.delete()
                            val freshReq = Request.Builder().url(release.apkUrl).build()
                            callClient.newCall(freshReq).execute().use { freshResp ->
                                if (!freshResp.isSuccessful) {
                                    throw IOException("Server returned HTTP ${freshResp.code}")
                                }
                                streamToFile(freshResp, partFile, release, 0L, append = false)
                            }
                        } else {
                            throw IOException("Server returned HTTP ${response.code}")
                        }
                    } else {
                        val append = (response.code == 206)
                        val startOffset = if (append) downloadedBytes else 0L
                        streamToFile(response, partFile, release, startOffset, append)
                    }
                }

                if (release.apkSize > 0 && partFile.length() != release.apkSize) {
                    throw IOException("Downloaded file size ${partFile.length()} does not match expected size ${release.apkSize}")
                }

                if (partFile.renameTo(finalFile)) {
                    currentApkFile = finalFile
                    _state.value = UpdateState.ReadyToInstall(release, finalFile.absolutePath)
                    proceedToInstall(release, finalFile)
                } else {
                    throw IOException("Failed to rename .part file to .apk")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("UpdateController", "Download failed: ${e.message}", e)
                _state.value = UpdateState.DownloadFailed(release, "Download failed. Check your connection and try again.")
            }
        }
    }

    private suspend fun streamToFile(
        response: Response,
        partFile: File,
        release: ReleaseInfo,
        startOffset: Long,
        append: Boolean,
    ) {
        val body = response.body ?: throw IOException("Empty response body")
        val total = if (release.apkSize > 0) {
            release.apkSize
        } else {
            val cl = body.contentLength()
            if (cl > 0) cl + startOffset else -1L
        }

        var currentBytes = startOffset
        var lastUpdateMs = System.currentTimeMillis()
        var lastFraction = if (total > 0) (currentBytes.toFloat() / total) else 0f

        val fos = FileOutputStream(partFile, append)
        val buffer = ByteArray(8192)
        fos.use { out ->
            body.byteStream().use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    currentCoroutineContext().ensureActive()
                    out.write(buffer, 0, bytesRead)
                    currentBytes += bytesRead

                    val now = System.currentTimeMillis()
                    val currentFraction = if (total > 0) (currentBytes.toFloat() / total) else 0f
                    if (now - lastUpdateMs >= 250L || (currentFraction - lastFraction) >= 0.01f) {
                        _state.value = UpdateState.Downloading(release, currentBytes, total)
                        lastUpdateMs = now
                        lastFraction = currentFraction
                    }
                }
                out.flush()
            }
        }
        _state.value = UpdateState.Downloading(release, currentBytes, total)
    }

    private fun proceedToInstall(release: ReleaseInfo, apkFile: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            appScope.launch {
                val prefs = settingsRepository.updatePrefs.first()
                if (!prefs.installExplained) {
                    _needsInstallPermission.value = true
                } else {
                    openUnknownAppSourcesSettings()
                }
            }
        } else {
            startPackageInstaller(release, apkFile)
        }
    }

    private fun openUnknownAppSourcesSettings() {
        isWaitingForInstallPermission = true
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateController", "Failed to open unknown app sources settings", e)
        }
    }

    private fun startPackageInstaller(release: ReleaseInfo, apkFile: File) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.use { s ->
                s.openWrite("fmhy_update", 0, apkFile.length()).use { out ->
                    apkFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                    s.fsync(out)
                }

                val intent = Intent(context, UpdateInstallReceiver::class.java).apply {
                    action = "io.github.omeg4dev.fmhy.update.INSTALL_RESULT"
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                } else {
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = android.app.PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    flags,
                )
                s.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            Log.e("UpdateController", "Failed to commit package install session", e)
            _state.value = UpdateState.DownloadFailed(release, "The system couldn't install the update.")
        }
    }

    override fun retry() {
        install()
    }

    override fun later() {
        val now = System.currentTimeMillis()
        val laterUntil = UpdateCheckPolicy.laterUntil(now)
        appScope.launch {
            settingsRepository.updateUpdatePrefs { it.copy(laterUntilMs = laterUntil) }
        }
        _state.value = UpdateState.Idle
    }

    override fun skip() {
        val release = currentRelease
            ?: (_state.value as? UpdateState.Available)?.release
            ?: (_state.value as? UpdateState.WaitingForWifi)?.release
            ?: (_state.value as? UpdateState.ReadyToInstall)?.release
            ?: (_state.value as? UpdateState.DownloadFailed)?.release
        if (release != null) {
            appScope.launch {
                settingsRepository.updateUpdatePrefs { it.copy(skippedVersion = release.version) }
            }
        }
        _state.value = UpdateState.Idle
    }

    override fun dismissMessage() {
        when (_state.value) {
            is UpdateState.UpToDate,
            is UpdateState.CheckFailed,
            is UpdateState.SignatureMismatch -> {
                _state.value = UpdateState.Idle
            }
            else -> {}
        }
    }

    override fun onInstallerResult(status: Int, message: String?, extraIntent: Intent?) {
        val outcome = UpdateInstallStatus.map(status, message)
        val release = currentRelease
            ?: (_state.value as? UpdateState.ReadyToInstall)?.release
            ?: (_state.value as? UpdateState.DownloadFailed)?.release
            ?: (_state.value as? UpdateState.SignatureMismatch)?.release
            ?: return

        when (outcome) {
            UpdateInstallOutcome.PENDING_USER_ACTION -> {
                if (extraIntent != null) {
                    extraIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(extraIntent)
                    } catch (e: Exception) {
                        Log.e("UpdateController", "Failed to start install confirm intent", e)
                        _state.value = UpdateState.DownloadFailed(release, "The system couldn't install the update.")
                    }
                }
            }
            UpdateInstallOutcome.SUCCESS -> {
                currentApkFile?.delete()
                appScope.launch {
                    settingsRepository.updateUpdatePrefs { it.copy(knownAvailable = null) }
                }
                _availableVersion.value = null
                _state.value = UpdateState.Idle
            }
            UpdateInstallOutcome.ABORTED -> {
                val path = currentApkFile?.absolutePath.orEmpty()
                _state.value = UpdateState.ReadyToInstall(release, path)
            }
            UpdateInstallOutcome.SIGNATURE_MISMATCH -> {
                _state.value = UpdateState.SignatureMismatch(release)
            }
            UpdateInstallOutcome.FAILURE -> {
                _state.value = UpdateState.DownloadFailed(release, "The system couldn't install the update.")
            }
        }
    }

    override fun confirmMobileAsk() {
        _pendingMobileAsk.value = false
        val action = pendingMobileAction
        pendingMobileAction = null
        action?.invoke()
    }

    override fun cancelMobileAsk() {
        _pendingMobileAsk.value = false
        pendingMobileAction = null
    }

    override fun confirmInstallPermission() {
        _needsInstallPermission.value = false
        appScope.launch {
            settingsRepository.updateUpdatePrefs { it.copy(installExplained = true) }
        }
        openUnknownAppSourcesSettings()
    }

    override fun cancelInstallPermission() {
        _needsInstallPermission.value = false
    }
}
