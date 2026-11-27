package io.github.omeg4dev.fmhy.data.fmhyx

import android.content.SharedPreferences
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.work.NetworkKind
import io.github.omeg4dev.fmhy.work.NetworkPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface FmhyXSyncError {
    data object Offline : FmhyXSyncError
    data object TokenInvalid : FmhyXSyncError
    data object TooMany : FmhyXSyncError
    data class Limit(val limit: SyncLimit) : FmhyXSyncError
    data object WaitingForWifi : FmhyXSyncError
    data object Failed : FmhyXSyncError
}

sealed interface FmhyXAccountState {
    data object SignedOut : FmhyXAccountState
    data class SignedIn(
        val running: Boolean = false,
        val lastSyncedAt: Long? = null,
        val error: FmhyXSyncError? = null,
    ) : FmhyXAccountState
}

sealed interface FmhyXSignInResult {
    data object Success : FmhyXSignInResult
    data class Error(val message: String) : FmhyXSignInResult
}

data class PersistedFmhyXStatus(
    val lastSyncedAt: Long? = null,
    val error: FmhyXSyncError? = null,
    val backoffStep: Int = 0,
    val backoffUntilMs: Long = 0L,
)

interface FmhyXStatusStore {
    fun load(): PersistedFmhyXStatus
    fun save(status: PersistedFmhyXStatus)
    fun clear()
}

class PrefsFmhyXStatusStore(
    prefsProvider: () -> SharedPreferences,
) : FmhyXStatusStore {
    private val prefs by lazy(prefsProvider)

    override fun load(): PersistedFmhyXStatus {
        val lastSyncedAt = if (prefs.contains(KEY_LAST_SYNCED_AT)) {
            prefs.getLong(KEY_LAST_SYNCED_AT, 0L).takeIf { it > 0L }
        } else {
            null
        }
        val error = decodeError(prefs.getString(KEY_ERROR, null))
        val backoffStep = prefs.getInt(KEY_BACKOFF_STEP, 0).coerceAtLeast(0)
        val backoffUntilMs = prefs.getLong(KEY_BACKOFF_UNTIL_MS, 0L).coerceAtLeast(0L)
        return PersistedFmhyXStatus(
            lastSyncedAt = lastSyncedAt,
            error = error,
            backoffStep = backoffStep,
            backoffUntilMs = backoffUntilMs,
        )
    }

    override fun save(status: PersistedFmhyXStatus) {
        val editor = prefs.edit()
        if (status.lastSyncedAt != null) {
            editor.putLong(KEY_LAST_SYNCED_AT, status.lastSyncedAt)
        } else {
            editor.remove(KEY_LAST_SYNCED_AT)
        }
        val encodedError = encodeError(status.error)
        if (encodedError != null) {
            editor.putString(KEY_ERROR, encodedError)
        } else {
            editor.remove(KEY_ERROR)
        }
        editor.putInt(KEY_BACKOFF_STEP, status.backoffStep)
        editor.putLong(KEY_BACKOFF_UNTIL_MS, status.backoffUntilMs)
        editor.apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "fmhyx_status"
        private const val KEY_LAST_SYNCED_AT = "last_synced_at"
        private const val KEY_ERROR = "error"
        private const val KEY_BACKOFF_STEP = "backoff_step"
        private const val KEY_BACKOFF_UNTIL_MS = "backoff_until_ms"

        private fun encodeError(error: FmhyXSyncError?): String? = when (error) {
            null -> null
            FmhyXSyncError.Offline -> "offline"
            FmhyXSyncError.TokenInvalid -> "token_invalid"
            FmhyXSyncError.TooMany -> "too_many"
            FmhyXSyncError.WaitingForWifi -> "waiting_for_wifi"
            FmhyXSyncError.Failed -> "failed"
            is FmhyXSyncError.Limit -> when (error.limit) {
                SyncLimit.SAVED_ITEMS -> "limit_saved_items"
                SyncLimit.FOLDERS -> "limit_folders"
                SyncLimit.FOLDER_ITEMS -> "limit_folder_items"
                SyncLimit.BODY_SIZE -> "limit_body_size"
            }
        }

        private fun decodeError(raw: String?): FmhyXSyncError? = when (raw) {
            "offline" -> FmhyXSyncError.Offline
            "token_invalid" -> FmhyXSyncError.TokenInvalid
            "too_many" -> FmhyXSyncError.TooMany
            "waiting_for_wifi" -> FmhyXSyncError.WaitingForWifi
            "failed" -> FmhyXSyncError.Failed
            "limit_saved_items" -> FmhyXSyncError.Limit(SyncLimit.SAVED_ITEMS)
            "limit_folders" -> FmhyXSyncError.Limit(SyncLimit.FOLDERS)
            "limit_folder_items" -> FmhyXSyncError.Limit(SyncLimit.FOLDER_ITEMS)
            "limit_body_size" -> FmhyXSyncError.Limit(SyncLimit.BODY_SIZE)
            else -> null
        }
    }
}

interface FmhyXSyncBackend {
    suspend fun checkAccount(token: String)
    suspend fun deleteAccount(token: String)
    suspend fun sync(token: String): SyncResult
    suspend fun sharedFolderIds(): Set<Long>
    suspend fun reset()
}

class DefaultFmhyXSyncBackend(
    clientProvider: () -> FmhyXClient,
    engineProvider: (FmhyXClient) -> FmhyXSyncEngine,
) : FmhyXSyncBackend {
    private val client by lazy(clientProvider)
    private val engine by lazy { engineProvider(client) }

    override suspend fun checkAccount(token: String) = client.checkAccount(token)
    override suspend fun deleteAccount(token: String) = client.deleteAccount(token)
    override suspend fun sync(token: String): SyncResult = engine.sync(token)
    override suspend fun sharedFolderIds(): Set<Long> = engine.sharedFolderIds()
    override suspend fun reset() = engine.reset()
}

/**
 * Coordinates FMHY X account authentication, sync triggers, backoff, and status state.
 */
class FmhyXSyncManager(
    private val tokenStore: FmhyXTokenStore,
    private val statusStore: FmhyXStatusStore,
    userStoreProvider: () -> UserStore,
    private val backend: FmhyXSyncBackend,
    private val networkKind: StateFlow<NetworkKind>,
    private val wifiOnlyFlow: Flow<Boolean>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val awaitReady: suspend () -> Unit = {},
) {
    /** Opening the store reads the disk, so it waits for the first off-main use. */
    private val userStore by lazy(userStoreProvider)

    private val _state = MutableStateFlow<FmhyXAccountState>(FmhyXAccountState.SignedOut)
    val state: StateFlow<FmhyXAccountState> = _state.asStateFlow()

    private val _sharedFolderIds = MutableStateFlow<Set<Long>>(emptySet())
    val sharedFolderIds: StateFlow<Set<Long>> = _sharedFolderIds.asStateFlow()

    private val authMutex = Mutex()
    private val controlMutex = Mutex()
    private val loadMutex = Mutex()

    private var loaded = false
    private var started = false

    private var currentToken: String? = null
    private var lastSyncedAt: Long? = null
    private var currentError: FmhyXSyncError? = null
    private var backoffStep: Int = 0
    private var backoffUntilMs: Long = 0L
    private var lastForegroundTriggerMs: Long? = null

    private var syncJob: Job? = null
    private var syncExecuting: Boolean = false
    private var pendingRerun: Boolean = false
    private var pendingManual: Boolean = false
    private var pendingOverrideWifiOnly: Boolean = false

    private var debounceJob: Job? = null
    private var debounceTargetFp: SyncFingerprint? = null
    private var retryJob: Job? = null
    private var lastObservedFp: SyncFingerprint? = null

    private data class BookmarkFingerprintRow(
        val entryId: String,
        val url: String,
        val folderId: Long?,
        val savedAt: Long,
        val name: String,
    )

    private data class FolderFingerprintRow(
        val id: Long,
        val name: String,
        val syncId: String?,
    )

    private data class SyncFingerprint(
        val bookmarks: List<BookmarkFingerprintRow>,
        val folders: List<FolderFingerprintRow>,
    )

    fun start() {
        scope.launch {
            val shouldStartObservers = controlMutex.withLock {
                if (started) {
                    false
                } else {
                    started = true
                    true
                }
            }
            if (!shouldStartObservers) return@launch

            ensureLoaded()

            val initialFp = withContext(ioDispatcher) { readFingerprint() }
            controlMutex.withLock {
                if (lastObservedFp == null) {
                    lastObservedFp = initialFp
                }
                val token = currentToken
                if (token != null && currentError !is FmhyXSyncError.TokenInvalid) {
                    val now = clock()
                    if (backoffUntilMs > now) {
                        scheduleRetryLocked(backoffUntilMs - now)
                    }
                }
            }

            launch {
                awaitReady()
                val isSignedIn = controlMutex.withLock { currentToken != null }
                if (isSignedIn) {
                    refreshSharedFolderIds()
                }
            }

            launch { observeUserChanges() }
            launch { observeWifiRecovery() }
        }
    }

    fun onForeground() {
        scope.launch {
            ensureLoaded()
            controlMutex.withLock {
                if (currentToken == null || currentError is FmhyXSyncError.TokenInvalid) {
                    return@withLock
                }
                val now = clock()
                val prev = lastForegroundTriggerMs
                if (prev != null && now - prev < FOREGROUND_THROTTLE_MS) {
                    return@withLock
                }
                if (now < backoffUntilMs) {
                    if (currentError != FmhyXSyncError.TooMany) {
                        currentError = FmhyXSyncError.TooMany
                        persistStatusLocked()
                        publishStateLocked(running = syncJob?.isActive == true)
                    }
                    return@withLock
                }
                val wifiOnly = wifiOnlyFlow.first()
                val kind = networkKind.value
                if (!NetworkPolicy.allowed(switchOn = wifiOnly, kind = kind, overrideThisTime = false)) {
                    setErrorIfIdleLocked(FmhyXSyncError.WaitingForWifi)
                    return@withLock
                }
                if (kind == NetworkKind.NONE) {
                    setErrorIfIdleLocked(FmhyXSyncError.Offline)
                    return@withLock
                }
                lastForegroundTriggerMs = now
                enqueueSyncLocked(manual = false, overrideWifiOnly = false)
            }
        }
    }

    fun syncNow(overrideWifiOnly: Boolean = true) {
        scope.launch {
            ensureLoaded()
            controlMutex.withLock {
                if (currentToken == null || currentError is FmhyXSyncError.TokenInvalid) {
                    return@withLock
                }
                val now = clock()
                if (now < backoffUntilMs) {
                    if (currentError != FmhyXSyncError.TooMany) {
                        currentError = FmhyXSyncError.TooMany
                        persistStatusLocked()
                        publishStateLocked(running = syncJob?.isActive == true)
                    }
                    return@withLock
                }
                val wifiOnly = wifiOnlyFlow.first()
                val kind = networkKind.value
                if (!NetworkPolicy.allowed(switchOn = wifiOnly, kind = kind, overrideThisTime = overrideWifiOnly)) {
                    setErrorIfIdleLocked(FmhyXSyncError.WaitingForWifi)
                    return@withLock
                }
                if (kind == NetworkKind.NONE) {
                    setErrorIfIdleLocked(FmhyXSyncError.Offline)
                    return@withLock
                }
                enqueueSyncLocked(manual = true, overrideWifiOnly = overrideWifiOnly)
            }
        }
    }

    suspend fun signIn(token: String, overrideWifiOnly: Boolean = true): FmhyXSignInResult {
        ensureLoaded()
        return authMutex.withLock {
            val trimmed = token.trim()
            if (trimmed.isEmpty()) {
                return@withLock FmhyXSignInResult.Error("Token not valid")
            }
            if (networkKind.value == NetworkKind.NONE) {
                return@withLock FmhyXSignInResult.Error("Offline, try again")
            }
            try {
                withContext(ioDispatcher) {
                    backend.checkAccount(trimmed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: FmhyXException.Unauthorized) {
                return@withLock FmhyXSignInResult.Error("Token not valid")
            } catch (_: FmhyXException.RateLimited) {
                return@withLock FmhyXSignInResult.Error("Too many tries, wait a moment")
            } catch (_: FmhyXException.Offline) {
                return@withLock FmhyXSignInResult.Error("Offline, try again")
            } catch (_: Exception) {
                return@withLock FmhyXSignInResult.Error("Sign-in failed, try again")
            }

            cancelAndJoinSyncJobs()

            withContext(ioDispatcher) {
                backend.reset()
                tokenStore.save(trimmed)
                statusStore.clear()
            }
            val currentFp = withContext(ioDispatcher) { readFingerprint() }

            _sharedFolderIds.value = emptySet()
            controlMutex.withLock {
                currentToken = trimmed
                lastSyncedAt = null
                currentError = null
                backoffStep = 0
                backoffUntilMs = 0L
                lastForegroundTriggerMs = null
                lastObservedFp = currentFp
                publishStateLocked(running = false)
                enqueueSyncLocked(manual = true, overrideWifiOnly = overrideWifiOnly)
            }
            FmhyXSignInResult.Success
        }
    }

    suspend fun signOut() {
        ensureLoaded()
        authMutex.withLock {
            performSignOutLocked()
        }
    }

    suspend fun deleteAccount(): Boolean {
        ensureLoaded()
        return authMutex.withLock {
            val token = controlMutex.withLock { currentToken } ?: run {
                performSignOutLocked()
                return@withLock true
            }
            cancelAndJoinSyncJobs()
            try {
                withContext(ioDispatcher) {
                    backend.deleteAccount(token)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                controlMutex.withLock {
                    publishStateLocked(running = false)
                }
                return@withLock false
            }
            performSignOutLocked()
            true
        }
    }

    suspend fun readToken(): String? {
        ensureLoaded()
        return withContext(ioDispatcher) {
            controlMutex.withLock { currentToken } ?: tokenStore.read()
        }
    }

    private suspend fun performSignOutLocked() {
        controlMutex.withLock {
            currentToken = null
        }
        cancelAndJoinSyncJobs()
        withContext(ioDispatcher) {
            backend.reset()
            tokenStore.clear()
            statusStore.clear()
        }
        _sharedFolderIds.value = emptySet()
        controlMutex.withLock {
            currentToken = null
            lastSyncedAt = null
            currentError = null
            backoffStep = 0
            backoffUntilMs = 0L
            lastForegroundTriggerMs = null
            pendingRerun = false
            pendingManual = false
            pendingOverrideWifiOnly = false
            publishStateLocked(running = false)
        }
    }

    private suspend fun cancelAndJoinSyncJobs() {
        val activeSync = controlMutex.withLock {
            debounceJob?.cancel()
            debounceJob = null
            debounceTargetFp = null
            retryJob?.cancel()
            retryJob = null
            pendingRerun = false
            pendingManual = false
            pendingOverrideWifiOnly = false
            val job = syncJob
            job?.cancel()
            job
        }
        activeSync?.join()
        controlMutex.withLock {
            syncJob = null
            syncExecuting = false
        }
    }

    private suspend fun ensureLoaded() {
        loadMutex.withLock {
            if (loaded) return
            val (token, status) = withContext(ioDispatcher) {
                val t = tokenStore.read()
                val s = if (t != null) statusStore.load() else PersistedFmhyXStatus()
                if (t == null) {
                    statusStore.clear()
                }
                t to s
            }
            controlMutex.withLock {
                currentToken = token
                if (token != null) {
                    lastSyncedAt = status.lastSyncedAt
                    currentError = status.error
                    backoffStep = status.backoffStep
                    backoffUntilMs = status.backoffUntilMs
                } else {
                    lastSyncedAt = null
                    currentError = null
                    backoffStep = 0
                    backoffUntilMs = 0L
                }
                publishStateLocked(running = false)
            }
            loaded = true
        }
    }

    private suspend fun observeUserChanges() {
        withContext(ioDispatcher) { userStore }.changes.collect {
            // `changes` also counts history writes: read nothing while signed out.
            val isSignedIn = controlMutex.withLock { currentToken != null }
            if (!isSignedIn) return@collect
            val fp = withContext(ioDispatcher) { readFingerprint() }
            if (controlMutex.withLock { fp == lastObservedFp }) return@collect
            refreshSharedFolderIds()
            controlMutex.withLock {
                if (fp == lastObservedFp) return@withLock
                lastObservedFp = fp
                if (currentToken == null || currentError is FmhyXSyncError.TokenInvalid) {
                    return@withLock
                }
                debounceJob?.cancel()
                debounceTargetFp = fp
                debounceJob = scope.launch {
                    delay(DEBOUNCE_MS)
                    triggerAutomaticSyncFromDebounce(fp)
                }
            }
        }
    }

    private suspend fun triggerAutomaticSyncFromDebounce(fp: SyncFingerprint) {
        controlMutex.withLock {
            if (debounceTargetFp == fp) {
                debounceJob = null
                debounceTargetFp = null
            }
            if (currentToken == null || currentError is FmhyXSyncError.TokenInvalid) {
                return
            }
            val now = clock()
            if (now < backoffUntilMs) {
                if (currentError != FmhyXSyncError.TooMany) {
                    currentError = FmhyXSyncError.TooMany
                    persistStatusLocked()
                    publishStateLocked(running = syncJob?.isActive == true)
                }
                return
            }
            val wifiOnly = wifiOnlyFlow.first()
            val kind = networkKind.value
            if (!NetworkPolicy.allowed(switchOn = wifiOnly, kind = kind, overrideThisTime = false)) {
                setErrorIfIdleLocked(FmhyXSyncError.WaitingForWifi)
                return
            }
            if (kind == NetworkKind.NONE) {
                setErrorIfIdleLocked(FmhyXSyncError.Offline)
                return
            }
            enqueueSyncLocked(manual = false, overrideWifiOnly = false)
        }
    }

    private suspend fun observeWifiRecovery() {
        combine(networkKind, wifiOnlyFlow) { kind, wifiOnly ->
            kind to wifiOnly
        }.collect { (kind, wifiOnly) ->
            controlMutex.withLock {
                if (currentToken == null || currentError !is FmhyXSyncError.WaitingForWifi) {
                    return@withLock
                }
                if (!NetworkPolicy.allowed(switchOn = wifiOnly, kind = kind, overrideThisTime = false)) {
                    return@withLock
                }
                if (kind == NetworkKind.NONE) {
                    setErrorIfIdleLocked(FmhyXSyncError.Offline)
                    return@withLock
                }
                if (clock() < backoffUntilMs) {
                    currentError = FmhyXSyncError.TooMany
                    persistStatusLocked()
                    publishStateLocked(running = syncJob?.isActive == true)
                    return@withLock
                }
                enqueueSyncLocked(manual = false, overrideWifiOnly = false)
            }
        }
    }

    private fun setErrorIfIdleLocked(error: FmhyXSyncError) {
        if (syncJob?.isActive == true) return
        currentError = error
        persistStatusLocked()
        publishStateLocked(running = false)
    }

    private fun enqueueSyncLocked(manual: Boolean, overrideWifiOnly: Boolean) {
        val active = syncJob
        if (active != null && active.isActive) {
            if (syncExecuting) {
                pendingRerun = true
            }
            if (manual) pendingManual = true
            if (overrideWifiOnly) pendingOverrideWifiOnly = true
            return
        }
        syncExecuting = false
        syncJob = scope.launch {
            runSyncLoop(initialOverrideWifiOnly = overrideWifiOnly)
        }
    }

    private suspend fun runSyncLoop(initialOverrideWifiOnly: Boolean) {
        var nextOverride = initialOverrideWifiOnly
        while (true) {
            awaitReady()
            currentCoroutineContext().ensureActive()

            val tokenToUse = controlMutex.withLock {
                val tok = currentToken
                if (tok == null || currentError is FmhyXSyncError.TokenInvalid) {
                    pendingRerun = false
                    pendingManual = false
                    pendingOverrideWifiOnly = false
                    syncExecuting = false
                    return
                }
                val now = clock()
                if (now < backoffUntilMs) {
                    currentError = FmhyXSyncError.TooMany
                    persistStatusLocked()
                    publishStateLocked(running = false)
                    pendingRerun = false
                    pendingManual = false
                    pendingOverrideWifiOnly = false
                    syncExecuting = false
                    return
                }
                val wifiOnly = wifiOnlyFlow.first()
                val kind = networkKind.value
                val effectiveOverride = nextOverride || pendingOverrideWifiOnly
                pendingOverrideWifiOnly = false
                pendingManual = false
                if (!NetworkPolicy.allowed(switchOn = wifiOnly, kind = kind, overrideThisTime = effectiveOverride)) {
                    currentError = FmhyXSyncError.WaitingForWifi
                    persistStatusLocked()
                    publishStateLocked(running = false)
                    pendingRerun = false
                    syncExecuting = false
                    return
                }
                if (kind == NetworkKind.NONE) {
                    currentError = FmhyXSyncError.Offline
                    persistStatusLocked()
                    publishStateLocked(running = false)
                    pendingRerun = false
                    syncExecuting = false
                    return
                }
                syncExecuting = true
                currentError = null
                publishStateLocked(running = true)
                tok
            }

            var shouldRerun = false
            try {
                val result = withContext(ioDispatcher) {
                    backend.sync(tokenToUse)
                }
                val postFp = withContext(ioDispatcher) { readFingerprint() }
                refreshSharedFolderIds()

                controlMutex.withLock {
                    syncExecuting = false
                    lastObservedFp = postFp
                    if (debounceTargetFp == postFp) {
                        debounceJob?.cancel()
                        debounceJob = null
                        debounceTargetFp = null
                    }
                    if (currentToken == tokenToUse) {
                        lastSyncedAt = result.syncedAt
                        currentError = null
                        backoffStep = 0
                        backoffUntilMs = 0L
                        retryJob?.cancel()
                        retryJob = null
                        persistStatusLocked()

                        if (result.changesPending || pendingRerun) {
                            shouldRerun = true
                            nextOverride = pendingOverrideWifiOnly
                            pendingRerun = false
                            pendingManual = false
                            pendingOverrideWifiOnly = false
                            publishStateLocked(running = true)
                        } else {
                            publishStateLocked(running = false)
                        }
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    val postFp = runCatching {
                        withContext(ioDispatcher) { readFingerprint() }
                    }.getOrNull()
                    controlMutex.withLock {
                        syncExecuting = false
                        if (postFp != null) {
                            lastObservedFp = postFp
                        }
                        pendingRerun = false
                        pendingManual = false
                        pendingOverrideWifiOnly = false
                        if (currentToken != null) {
                            publishStateLocked(running = false)
                        }
                    }
                }
                throw e
            } catch (e: FmhyXException) {
                handleSyncFailure(tokenToUse, e)
            } catch (_: Exception) {
                handleSyncFailure(tokenToUse, null)
            }

            if (!shouldRerun) break
        }
    }

    private suspend fun handleSyncFailure(tokenToUse: String, e: FmhyXException?) {
        // A failed run wrote no rows, so a pending debounce belongs to the user and must stay.
        controlMutex.withLock {
            syncExecuting = false
            if (currentToken != tokenToUse) {
                pendingRerun = false
                pendingManual = false
                pendingOverrideWifiOnly = false
                return
            }
            when (e) {
                is FmhyXException.Unauthorized -> {
                    currentError = FmhyXSyncError.TokenInvalid
                    backoffStep = 0
                    backoffUntilMs = 0L
                    retryJob?.cancel()
                    retryJob = null
                }
                is FmhyXException.RateLimited -> {
                    currentError = FmhyXSyncError.TooMany
                    val expDelayMs = minOf(
                        INITIAL_BACKOFF_MS shl backoffStep.coerceAtMost(MAX_BACKOFF_SHIFT),
                        MAX_BACKOFF_MS,
                    )
                    val serverDelayMs = (e.retryAfterSeconds ?: 0L).coerceAtLeast(0L) * 1000L
                    val delayMs = maxOf(expDelayMs, serverDelayMs)
                    backoffUntilMs = clock() + delayMs
                    backoffStep += 1
                    scheduleRetryLocked(delayMs)
                }
                is FmhyXException.LimitExceeded -> {
                    currentError = FmhyXSyncError.Limit(e.limit)
                }
                is FmhyXException.Offline -> {
                    currentError = FmhyXSyncError.Offline
                }
                else -> {
                    currentError = FmhyXSyncError.Failed
                }
            }
            pendingRerun = false
            pendingManual = false
            pendingOverrideWifiOnly = false
            persistStatusLocked()
            publishStateLocked(running = false)
        }
    }

    private fun scheduleRetryLocked(delayMs: Long) {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMs.coerceAtLeast(0L))
            triggerScheduledRetry()
        }
    }

    private suspend fun triggerScheduledRetry() {
        controlMutex.withLock {
            retryJob = null
            if (currentToken == null || currentError is FmhyXSyncError.TokenInvalid) {
                return
            }
            if (clock() < backoffUntilMs) {
                scheduleRetryLocked(backoffUntilMs - clock())
                return
            }
            val wifiOnly = wifiOnlyFlow.first()
            val kind = networkKind.value
            if (!NetworkPolicy.allowed(switchOn = wifiOnly, kind = kind, overrideThisTime = false)) {
                setErrorIfIdleLocked(FmhyXSyncError.WaitingForWifi)
                return
            }
            if (kind == NetworkKind.NONE) {
                setErrorIfIdleLocked(FmhyXSyncError.Offline)
                return
            }
            enqueueSyncLocked(manual = false, overrideWifiOnly = false)
        }
    }

    private suspend fun refreshSharedFolderIds() {
        val ids = runCatching {
            withContext(ioDispatcher) { backend.sharedFolderIds() }
        }.getOrElse { emptySet() }
        _sharedFolderIds.value = ids
    }

    private suspend fun readFingerprint(): SyncFingerprint {
        val snap = userStore.bookmarksSnapshot()
        return SyncFingerprint(
            bookmarks = snap.bookmarks
                .map {
                    BookmarkFingerprintRow(
                        entryId = it.entryId,
                        url = it.url,
                        folderId = it.folderId,
                        savedAt = it.savedAt,
                        name = it.name,
                    )
                }
                .sortedBy { it.entryId },
            folders = snap.folders
                .map {
                    FolderFingerprintRow(
                        id = it.id,
                        name = it.name,
                        syncId = it.syncId,
                    )
                }
                .sortedBy { it.id },
        )
    }

    private fun persistStatusLocked() {
        statusStore.save(
            PersistedFmhyXStatus(
                lastSyncedAt = lastSyncedAt,
                error = currentError,
                backoffStep = backoffStep,
                backoffUntilMs = backoffUntilMs,
            )
        )
    }

    private fun publishStateLocked(running: Boolean) {
        _state.value = if (currentToken == null) {
            FmhyXAccountState.SignedOut
        } else {
            FmhyXAccountState.SignedIn(
                running = running,
                lastSyncedAt = lastSyncedAt,
                error = currentError,
            )
        }
    }

    companion object {
        const val DEBOUNCE_MS = 5_000L
        const val FOREGROUND_THROTTLE_MS = 5L * 60L * 1000L
        const val INITIAL_BACKOFF_MS = 60_000L
        const val MAX_BACKOFF_MS = 60L * 60L * 1000L
        private const val MAX_BACKOFF_SHIFT = 6
    }
}
