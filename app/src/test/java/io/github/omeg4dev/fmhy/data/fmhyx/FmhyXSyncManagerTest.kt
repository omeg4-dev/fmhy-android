package io.github.omeg4dev.fmhy.data.fmhyx

import io.github.omeg4dev.fmhy.data.user.UserBookmark
import io.github.omeg4dev.fmhy.data.user.UserStore
import io.github.omeg4dev.fmhy.work.NetworkKind
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class InMemoryFmhyXStatusStore : FmhyXStatusStore {
    private var current = PersistedFmhyXStatus()
    override fun load(): PersistedFmhyXStatus = current
    override fun save(status: PersistedFmhyXStatus) {
        current = status
    }
    override fun clear() {
        current = PersistedFmhyXStatus()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FmhyXSyncManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var tokenStore: FmhyXTokenStore
    private lateinit var statusStore: InMemoryFmhyXStatusStore
    private val openStores = mutableListOf<UserStore>()

    @Before
    fun setUp() {
        tokenStore = FmhyXTokenStore(File(tempFolder.root, "fmhyx_token.bin"), FakeCipher())
        statusStore = InMemoryFmhyXStatusStore()
    }

    @After
    fun tearDown() {
        openStores.forEach { it.close() }
        openStores.clear()
    }

    private class FakeCipher : TokenCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = plaintext.reversedArray()
        override fun decrypt(blob: ByteArray): ByteArray = blob.reversedArray()
    }

    private class FakeBackend : FmhyXSyncBackend {
        var checkAccountResult: suspend (String) -> Unit = {}
        var deleteAccountResult: suspend (String) -> Unit = {}
        var syncHandler: suspend (String) -> SyncResult = { SyncResult(syncedAt = 1000L, changesPending = false) }
        var sharedIds: Set<Long> = emptySet()

        var checkAccountCalls = 0
        var deleteAccountCalls = 0
        var syncCalls = 0
        var resetCalls = 0

        override suspend fun checkAccount(token: String) {
            checkAccountCalls++
            checkAccountResult(token)
        }

        override suspend fun deleteAccount(token: String) {
            deleteAccountCalls++
            deleteAccountResult(token)
        }

        override suspend fun sync(token: String): SyncResult {
            syncCalls++
            return syncHandler(token)
        }

        override suspend fun sharedFolderIds(): Set<Long> = sharedIds

        override suspend fun reset() {
            resetCalls++
        }
    }

    private data class Harness(
        val manager: FmhyXSyncManager,
        val scope: CoroutineScope,
        val userStore: UserStore,
    )

    private fun TestScope.createHarness(
        backend: FakeBackend = FakeBackend(),
        networkKind: MutableStateFlow<NetworkKind> = MutableStateFlow(NetworkKind.WIFI),
        wifiOnly: MutableStateFlow<Boolean> = MutableStateFlow(false),
        awaitReady: suspend () -> Unit = {},
    ): Harness {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val userStore = UserStore(
            dbFile = tempFolder.newFile("user-${openStores.size}.db"),
            dispatcher = dispatcher,
        ).also { openStores.add(it) }
        val managerScope = CoroutineScope(SupervisorJob() + dispatcher)
        val manager = FmhyXSyncManager(
            tokenStore = tokenStore,
            statusStore = statusStore,
            userStoreProvider = { userStore },
            backend = backend,
            networkKind = networkKind,
            wifiOnlyFlow = wifiOnly,
            scope = managerScope,
            ioDispatcher = dispatcher,
            clock = { testScheduler.currentTime },
            awaitReady = awaitReady,
        )
        return Harness(manager, managerScope, userStore)
    }

    private fun bookmark(id: String, folderId: Long? = null, note: String = "") = UserBookmark(
        entryId = id,
        name = "Site $id",
        url = "https://example.com/$id",
        sectionId = "tools",
        sectionTitle = "Tools",
        headingPath = "Tools",
        savedAt = 1_700_000_000_000L,
        note = note,
        folderId = folderId,
    )

    @Test
    fun debounceFiveSecondsCoalescesChangesAndIgnoresNotes() = runTest {
        tokenStore.save("fmhyx_tok")
        val backend = FakeBackend()
        val (manager, managerScope, userStore) = createHarness(backend)
        try {
            manager.start()
            runCurrent()
            assertEquals(0, backend.syncCalls)

            // Note-only change on existing bookmark: first add a bookmark and sync
            userStore.upsertBookmark(bookmark("e1"))
            runCurrent()
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(0, backend.syncCalls)

            // Second change resets the 5s debounce timer
            userStore.upsertBookmark(bookmark("e2"))
            runCurrent()
            advanceTimeBy(4_999)
            runCurrent()
            assertEquals(0, backend.syncCalls)

            advanceTimeBy(1)
            runCurrent()
            assertEquals(1, backend.syncCalls)

            // Note-only edit does not change fingerprint -> no sync
            userStore.setNote("e1", "my private note")
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(1, backend.syncCalls)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun triggerDuringRunCausesExactlyOneRerunAndChangesPendingReruns() = runTest {
        tokenStore.save("fmhyx_tok")
        val backend = FakeBackend()
        val firstRunGate = CompletableDeferred<Unit>()
        var callIndex = 0
        backend.syncHandler = {
            callIndex++
            when (callIndex) {
                1 -> {
                    firstRunGate.await()
                    SyncResult(syncedAt = 100L, changesPending = false)
                }
                2 -> SyncResult(syncedAt = 200L, changesPending = true)
                else -> SyncResult(syncedAt = 300L, changesPending = false)
            }
        }

        val (manager, managerScope, _) = createHarness(backend)
        try {
            manager.start()
            runCurrent()

            manager.syncNow()
            runCurrent()
            assertEquals(1, backend.syncCalls)

            // Multiple triggers during run #1 -> only ONE extra rerun
            manager.syncNow()
            manager.syncNow()
            manager.syncNow()
            runCurrent()
            assertEquals(1, backend.syncCalls)

            // Finish run #1 -> triggers run #2 (which returns changesPending = true) -> triggers run #3
            firstRunGate.complete(Unit)
            runCurrent()
            assertEquals(3, backend.syncCalls)
            val state = manager.state.value as FmhyXAccountState.SignedIn
            assertFalse(state.running)
            assertEquals(300L, state.lastSyncedAt)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun changeMadeDuringAFailingRunStillSyncs() = runTest {
        tokenStore.save("fmhyx_tok")
        val backend = FakeBackend()
        val firstRunGate = CompletableDeferred<Unit>()
        backend.syncHandler = {
            if (backend.syncCalls == 1) {
                firstRunGate.await()
                throw FmhyXException.Offline()
            }
            SyncResult(syncedAt = 200L, changesPending = false)
        }
        val (manager, managerScope, userStore) = createHarness(backend)
        try {
            manager.start()
            runCurrent()
            manager.syncNow()
            runCurrent()

            userStore.upsertBookmark(bookmark("during"))
            runCurrent()
            firstRunGate.complete(Unit)
            runCurrent()
            assertEquals(1, backend.syncCalls)

            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(2, backend.syncCalls)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun ownWritesDuringSyncDoNotRetriggerDebounce() = runTest {
        tokenStore.save("fmhyx_tok")
        val backend = FakeBackend()
        lateinit var userStoreRef: UserStore
        backend.syncHandler = {
            userStoreRef.upsertBookmark(bookmark("from_server"))
            SyncResult(syncedAt = 500L, changesPending = false)
        }

        val (manager, managerScope, userStore) = createHarness(backend)
        userStoreRef = userStore
        try {
            manager.start()
            runCurrent()

            // Local change triggers 5s debounced sync
            userStore.upsertBookmark(bookmark("local_1"))
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(1, backend.syncCalls)

            // Advance well past 5s: engine's write ("from_server") must NOT trigger a second sync
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(1, backend.syncCalls)

            // Subsequent real local change DOES trigger sync #2
            userStore.upsertBookmark(bookmark("local_2"))
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(2, backend.syncCalls)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun foregroundThrottleAllowsAtMostOncePerFiveMinutes() = runTest {
        tokenStore.save("fmhyx_tok")
        val backend = FakeBackend()
        val (manager, managerScope, _) = createHarness(backend)
        try {
            manager.start()
            runCurrent()

            manager.onForeground()
            runCurrent()
            assertEquals(1, backend.syncCalls)

            advanceTimeBy(60_000)
            manager.onForeground()
            runCurrent()
            assertEquals(1, backend.syncCalls)

            advanceTimeBy(4 * 60_000L - 1L)
            manager.onForeground()
            runCurrent()
            assertEquals(1, backend.syncCalls)

            advanceTimeBy(1L)
            manager.onForeground()
            runCurrent()
            assertEquals(2, backend.syncCalls)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun unauthorized401StopsAllRunsUntilNewSignIn() = runTest {
        tokenStore.save("fmhyx_bad")
        val backend = FakeBackend()
        backend.syncHandler = { throw FmhyXException.Unauthorized() }

        val (manager, managerScope, userStore) = createHarness(backend)
        try {
            manager.start()
            runCurrent()

            manager.syncNow()
            runCurrent()
            assertEquals(1, backend.syncCalls)

            val stateAfter401 = manager.state.value as FmhyXAccountState.SignedIn
            assertEquals(FmhyXSyncError.TokenInvalid, stateAfter401.error)
            assertEquals(FmhyXSyncError.TokenInvalid, statusStore.load().error)

            // Further foreground, debounce, and manual syncNow must NOT call backend
            manager.onForeground()
            manager.syncNow()
            userStore.upsertBookmark(bookmark("e1"))
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()
            assertEquals(1, backend.syncCalls)

            // New sign-in succeeds, resets, and syncs
            backend.syncHandler = { SyncResult(syncedAt = 999L, changesPending = false) }
            val signInRes = manager.signIn("fmhyx_new_good")
            assertEquals(FmhyXSignInResult.Success, signInRes)
            runCurrent()
            assertEquals(1, backend.resetCalls)
            assertEquals(2, backend.syncCalls)
            val signedIn = manager.state.value as FmhyXAccountState.SignedIn
            assertNull(signedIn.error)
            assertEquals(999L, signedIn.lastSyncedAt)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun rateLimitBackoffScheduleRetryAfterAndReset() = runTest {
        tokenStore.save("fmhyx_tok")
        val backend = FakeBackend()
        backend.syncHandler = { throw FmhyXException.RateLimited(retryAfterSeconds = null) }

        val (manager, managerScope, _) = createHarness(backend)
        try {
            manager.start()
            runCurrent()

            // Initial trigger -> 429 -> 1 min backoff
            manager.syncNow()
            runCurrent()
            assertEquals(1, backend.syncCalls)
            assertEquals(FmhyXSyncError.TooMany, (manager.state.value as FmhyXAccountState.SignedIn).error)

            // Inside 1 min window: auto & manual skipped without backend call
            advanceTimeBy(30_000)
            manager.onForeground()
            manager.syncNow()
            runCurrent()
            assertEquals(1, backend.syncCalls)
            assertEquals(FmhyXSyncError.TooMany, (manager.state.value as FmhyXAccountState.SignedIn).error)

            // At 1 min -> scheduled retry fires -> 429 -> 2 min backoff
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(2, backend.syncCalls)

            // At +1m59s -> not yet; at +2m -> scheduled retry fires -> 429 with Retry-After 300s (5m > 4m)
            backend.syncHandler = { throw FmhyXException.RateLimited(retryAfterSeconds = 300L) }
            advanceTimeBy(120_000)
            runCurrent()
            assertEquals(3, backend.syncCalls)

            // At +4m (240s) -> skipped because Retry-After was 300s
            advanceTimeBy(240_000)
            runCurrent()
            assertEquals(3, backend.syncCalls)

            // At +5m (300s) -> retry fires and succeeds -> resets backoff
            backend.syncHandler = { SyncResult(syncedAt = 1234L, changesPending = false) }
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(4, backend.syncCalls)
            val okState = manager.state.value as FmhyXAccountState.SignedIn
            assertNull(okState.error)

            // Next 429 starts back at 1 min (not 8 min)
            backend.syncHandler = { throw FmhyXException.RateLimited(retryAfterSeconds = null) }
            manager.syncNow()
            runCurrent()
            assertEquals(5, backend.syncCalls)

            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(6, backend.syncCalls)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun wifiGateBlocksAutoAndManualOverridesAndOfflineIsSafe() = runTest {
        tokenStore.save("fmhyx_tok")
        val backend = FakeBackend()
        val networkKind = MutableStateFlow(NetworkKind.MOBILE)
        val wifiOnly = MutableStateFlow(true)
        val (manager, managerScope, userStore) = createHarness(backend, networkKind, wifiOnly)
        try {
            manager.start()
            runCurrent()

            // Automatic foreground trigger on mobile with Wi-Fi only -> WaitingForWifi, no call
            manager.onForeground()
            runCurrent()
            assertEquals(0, backend.syncCalls)
            assertEquals(
                FmhyXSyncError.WaitingForWifi,
                (manager.state.value as FmhyXAccountState.SignedIn).error,
            )

            // Manual Sync now overrides Wi-Fi only (after confirmation dialog)
            manager.syncNow(overrideWifiOnly = true)
            runCurrent()
            assertEquals(1, backend.syncCalls)
            assertNull((manager.state.value as FmhyXAccountState.SignedIn).error)

            // Auto debounce on mobile sets WaitingForWifi, then switching to WIFI auto-retries
            userStore.upsertBookmark(bookmark("e_wifi"))
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(1, backend.syncCalls)
            assertEquals(
                FmhyXSyncError.WaitingForWifi,
                (manager.state.value as FmhyXAccountState.SignedIn).error,
            )

            networkKind.value = NetworkKind.WIFI
            runCurrent()
            assertEquals(2, backend.syncCalls)

            // No network at all -> Offline without backend call or crash
            wifiOnly.value = false
            networkKind.value = NetworkKind.NONE
            manager.syncNow()
            runCurrent()
            assertEquals(2, backend.syncCalls)
            assertEquals(
                FmhyXSyncError.Offline,
                (manager.state.value as FmhyXAccountState.SignedIn).error,
            )
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun signOutDuringRunCancelsJoinsResetsAndKeepsLocalData() = runTest {
        tokenStore.save("fmhyx_tok")
        val backend = FakeBackend()
        val syncStarted = CompletableDeferred<Unit>()
        val neverFinishes = CompletableDeferred<Unit>()
        var wasCancelled = false

        backend.syncHandler = {
            syncStarted.complete(Unit)
            try {
                neverFinishes.await()
            } finally {
                wasCancelled = true
            }
            SyncResult(syncedAt = 1L, changesPending = false)
        }

        val (manager, managerScope, userStore) = createHarness(backend)
        try {
            userStore.upsertBookmark(bookmark("keep_me"))
            manager.start()
            runCurrent()

            manager.syncNow()
            runCurrent()
            syncStarted.await()

            val signOutJob = launch { manager.signOut() }
            runCurrent()
            signOutJob.join()

            assertTrue(wasCancelled)
            assertEquals(FmhyXAccountState.SignedOut, manager.state.value)
            assertNull(tokenStore.read())
            assertEquals(1, backend.resetCalls)
            assertEquals(1, userStore.bookmarks().size)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun neverRunsBeforeInitAndMigrationReady() = runTest {
        tokenStore.save("fmhyx_tok")
        val backend = FakeBackend()
        val readyGate = CompletableDeferred<Unit>()
        val (manager, managerScope, userStore) = createHarness(backend, awaitReady = { readyGate.await() })
        try {
            manager.start()
            runCurrent()

            manager.onForeground()
            manager.syncNow()
            userStore.upsertBookmark(bookmark("early"))
            runCurrent()
            advanceTimeBy(10_000)
            runCurrent()

            assertEquals(0, backend.syncCalls)

            readyGate.complete(Unit)
            runCurrent()
            assertEquals(1, backend.syncCalls)
        } finally {
            managerScope.cancel()
        }
    }

    @Test
    fun signInErrorsStoreNothingAndDeleteAccountBehavior() = runTest {
        val backend = FakeBackend()
        val networkKind = MutableStateFlow(NetworkKind.WIFI)
        val (manager, managerScope, _) = createHarness(backend, networkKind = networkKind)
        try {
            manager.start()
            runCurrent()

            backend.checkAccountResult = { throw FmhyXException.Unauthorized() }
            assertEquals(FmhyXSignInResult.Error("Token not valid"), manager.signIn(" bad "))
            assertNull(tokenStore.read())
            assertEquals(FmhyXAccountState.SignedOut, manager.state.value)

            backend.checkAccountResult = { throw FmhyXException.RateLimited(10) }
            assertEquals(FmhyXSignInResult.Error("Too many tries, wait a moment"), manager.signIn("tok"))

            backend.checkAccountResult = { throw FmhyXException.Offline() }
            assertEquals(FmhyXSignInResult.Error("Offline, try again"), manager.signIn("tok"))

            backend.checkAccountResult = { throw FmhyXException.Server(500) }
            assertEquals(FmhyXSignInResult.Error("Sign-in failed, try again"), manager.signIn("tok"))

            // Success signs in and syncs
            backend.checkAccountResult = {}
            assertEquals(FmhyXSignInResult.Success, manager.signIn(" fmhyx_valid "))
            runCurrent()
            assertEquals("fmhyx_valid", tokenStore.read())
            assertTrue(manager.state.value is FmhyXAccountState.SignedIn)

            // Delete failure stays signed in
            backend.deleteAccountResult = { throw FmhyXException.Server(500) }
            assertFalse(manager.deleteAccount())
            assertEquals("fmhyx_valid", tokenStore.read())
            assertTrue(manager.state.value is FmhyXAccountState.SignedIn)

            // Delete success clears account
            backend.deleteAccountResult = {}
            assertTrue(manager.deleteAccount())
            assertNull(tokenStore.read())
            assertEquals(FmhyXAccountState.SignedOut, manager.state.value)
        } finally {
            managerScope.cancel()
        }
    }
}
