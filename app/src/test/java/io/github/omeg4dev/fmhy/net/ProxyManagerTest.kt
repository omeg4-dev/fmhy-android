package io.github.omeg4dev.fmhy.net

import io.github.omeg4dev.fmhy.data.ProtectionPrefs
import io.github.omeg4dev.fmhy.data.ProxyMode
import io.github.omeg4dev.fmhy.data.ProxyType
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProxyManagerTest {

    private class FakeWebProxyController(
        var isSupported: Boolean = true,
        var autoComplete: Boolean = false,
        var throwOnApply: Boolean = false,
    ) : WebProxyController {
        val supportedChecks = AtomicInteger(0)
        val appliedRules = CopyOnWriteArrayList<String>()
        val clearCount = AtomicInteger(0)
        private val pendingCallbacks = CopyOnWriteArrayList<() -> Unit>()

        override val supported: Boolean
            get() {
                supportedChecks.incrementAndGet()
                return isSupported
            }

        override fun apply(rule: String, onDone: () -> Unit) {
            appliedRules.add(rule)
            if (throwOnApply) error("Simulated ProxyController failure")
            if (autoComplete) {
                onDone()
            } else {
                pendingCallbacks.add(onDone)
            }
        }

        override fun clear(onDone: () -> Unit) {
            clearCount.incrementAndGet()
            if (autoComplete) {
                onDone()
            } else {
                pendingCallbacks.add(onDone)
            }
        }

        fun completeAt(index: Int) {
            pendingCallbacks[index]()
        }

        fun callbackCount(): Int = pendingCallbacks.size
    }

    private fun endpoint(port: Int): ProxyEndpoint = ProxyEndpoint.resolve(
        ProtectionPrefs(
            proxyMode = ProxyMode.CUSTOM,
            proxyType = ProxyType.SOCKS5,
            proxyHost = "127.0.0.1",
            proxyPort = port,
        )
    )!!

    private fun awaitCondition(timeoutMs: Long = 2_000L, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (predicate()) return
            Thread.sleep(5)
        }
        fail("Condition not met within ${timeoutMs}ms")
    }

    @Test
    fun firstLoadOffOpensGateAfterApplyingJavaScriptWithoutTouchingWebController() {
        val flow = MutableSharedFlow<ProxyState>(replay = 1)
        val controller = FakeWebProxyController()
        val resets = AtomicInteger(0)
        val recreates = AtomicInteger(0)
        val mainQueue = CopyOnWriteArrayList<() -> Unit>()
        val events = CopyOnWriteArrayList<String>()
        lateinit var pm: ProxyManager
        pm = ProxyManager(
            states = flow,
            webProxyController = controller,
            onNetworkReset = { resets.incrementAndGet() },
            onApplyJavaScript = {
                assertTrue(pm.isLoaded())
                assertNull(pm.currentEndpoint())
                assertFalse(pm.currentAllowJavaScript())
                events.add("applyJs")
            },
            onRecreateWebViews = { recreates.incrementAndGet() },
            runOnMain = { mainQueue.add(it) },
            isMainThread = { false },
            awaitTimeoutMs = 120L,
        )
        pm.start()
        try {
            assertTrue(flow.tryEmit(ProxyState(null, allowJavaScript = false)))
            assertNull(pm.awaitEndpoint())
            awaitCondition { mainQueue.size == 1 }
            // Gate must remain closed until onApplyJavaScript finishes on the main thread.
            assertFalse(pm.awaitWebAllowed())
            assertTrue(events.isEmpty())

            mainQueue[0].invoke()
            assertTrue(pm.awaitWebAllowed())
            assertEquals(listOf("applyJs"), events.toList())
            assertEquals(0, controller.supportedChecks.get())
            assertEquals(0, controller.appliedRules.size)
            assertEquals(0, controller.clearCount.get())
            assertEquals(0, resets.get())
            assertEquals(0, recreates.get())
        } finally {
            pm.close()
        }
    }

    @Test
    fun firstLoadProxiedPublishesEndpointImmediatelyAndHoldsGateUntilCallback() {
        val flow = MutableSharedFlow<ProxyState>(replay = 1)
        val controller = FakeWebProxyController(autoComplete = false)
        val events = CopyOnWriteArrayList<String>()
        val pm = ProxyManager(
            states = flow,
            webProxyController = controller,
            onNetworkReset = { events.add("reset") },
            onApplyJavaScript = { events.add("applyJs") },
            onRecreateWebViews = { events.add("recreate") },
            runOnMain = { it() },
            isMainThread = { false },
            awaitTimeoutMs = 150L,
        )
        pm.start()
        try {
            val ep = endpoint(9050)
            assertTrue(flow.tryEmit(ProxyState(ep, allowJavaScript = false)))
            assertEquals(ep, pm.awaitEndpoint())
            awaitCondition { controller.callbackCount() == 1 }
            // Gate must still be closed before callback completes.
            assertFalse(pm.awaitWebAllowed())
            assertEquals(listOf("socks://127.0.0.1:9050"), controller.appliedRules.toList())
            assertFalse("reset must not run on first load", "reset" in events)

            // Complete callback -> recreates WebViews, then opens gate.
            controller.completeAt(0)
            assertTrue(pm.awaitWebAllowed())
            assertEquals(listOf("recreate"), events.toList())
        } finally {
            pm.close()
        }
    }

    @Test
    fun togglingAllowJavaScriptWhileProxiedTriggersResetApplyAndRecreate() {
        val flow = MutableSharedFlow<ProxyState>(replay = 1)
        val controller = FakeWebProxyController(autoComplete = true)
        val resets = AtomicInteger(0)
        val recreates = AtomicInteger(0)
        val pm = ProxyManager(
            states = flow,
            webProxyController = controller,
            onNetworkReset = { resets.incrementAndGet() },
            onRecreateWebViews = { recreates.incrementAndGet() },
            runOnMain = { it() },
            isMainThread = { false },
        )
        pm.start()
        try {
            val ep = endpoint(9050)
            assertTrue(flow.tryEmit(ProxyState(ep, allowJavaScript = false)))
            assertTrue(pm.awaitWebAllowed())
            assertFalse(pm.currentAllowJavaScript())
            assertEquals(0, resets.get())
            assertEquals(1, recreates.get())

            assertTrue(flow.tryEmit(ProxyState(ep, allowJavaScript = true)))
            awaitCondition { pm.currentAllowJavaScript() && recreates.get() == 2 }
            assertEquals(1, resets.get())
            assertEquals(2, controller.appliedRules.size)
            assertTrue(pm.awaitWebAllowed())
        } finally {
            pm.close()
        }
    }

    @Test
    fun unsupportedOrThrowingApplyFailsClosed() {
        val flowUnsupported = MutableSharedFlow<ProxyState>(replay = 1)
        val unsupportedCtrl = FakeWebProxyController(isSupported = false)
        val pmUnsupported = ProxyManager(
            states = flowUnsupported,
            webProxyController = unsupportedCtrl,
            runOnMain = { it() },
            isMainThread = { false },
            awaitTimeoutMs = 5_000L,
        )
        pmUnsupported.start()
        try {
            assertTrue(flowUnsupported.tryEmit(ProxyState(endpoint(9050))))
            awaitCondition { pmUnsupported.isLoaded() }
            assertFalse(pmUnsupported.awaitWebAllowed())
        } finally {
            pmUnsupported.close()
        }

        val flowThrowing = MutableSharedFlow<ProxyState>(replay = 1)
        val throwingCtrl = FakeWebProxyController(isSupported = true, throwOnApply = true)
        val pmThrowing = ProxyManager(
            states = flowThrowing,
            webProxyController = throwingCtrl,
            runOnMain = { it() },
            isMainThread = { false },
            awaitTimeoutMs = 5_000L,
        )
        pmThrowing.start()
        try {
            assertTrue(flowThrowing.tryEmit(ProxyState(endpoint(9050))))
            awaitCondition { throwingCtrl.appliedRules.size == 1 }
            assertFalse(pmThrowing.awaitWebAllowed())
        } finally {
            pmThrowing.close()
        }
    }

    @Test
    fun failedApplyStillDestroysOpenPages() {
        val flow = MutableSharedFlow<ProxyState>(replay = 1)
        val recreates = AtomicInteger(0)
        val pm = ProxyManager(
            states = flow,
            webProxyController = FakeWebProxyController(throwOnApply = true),
            onRecreateWebViews = { recreates.incrementAndGet() },
            runOnMain = { it() },
            isMainThread = { false },
        )
        pm.start()
        try {
            assertTrue(flow.tryEmit(ProxyState(endpoint(9050))))
            awaitCondition { recreates.get() == 1 }
            assertFalse(pm.awaitWebAllowed())
        } finally {
            pm.close()
        }
    }

    @Test
    fun unloadedTimeoutFailsClosed() {
        val flow = MutableSharedFlow<ProxyState>(replay = 1)
        val pm = ProxyManager(
            states = flow,
            webProxyController = FakeWebProxyController(),
            runOnMain = { it() },
            isMainThread = { false },
            awaitTimeoutMs = 50L,
        )
        pm.start()
        try {
            var threw = false
            try {
                pm.awaitEndpoint()
            } catch (_: ProxyTimeoutException) {
                threw = true
            }
            assertTrue(threw)
            assertFalse(pm.awaitWebAllowed())
        } finally {
            pm.close()
        }
    }

    @Test
    fun newerChangeWinsOverStaleCallback() {
        val flow = MutableSharedFlow<ProxyState>(replay = 1)
        val controller = FakeWebProxyController(autoComplete = false)
        val resets = AtomicInteger(0)
        val recreates = AtomicInteger(0)
        val pm = ProxyManager(
            states = flow,
            webProxyController = controller,
            onNetworkReset = { resets.incrementAndGet() },
            onRecreateWebViews = { recreates.incrementAndGet() },
            runOnMain = { it() },
            isMainThread = { false },
            awaitTimeoutMs = 150L,
        )
        pm.start()
        try {
            val ep1 = endpoint(9050)
            val ep2 = endpoint(1080)
            assertTrue(flow.tryEmit(ProxyState(ep1)))
            awaitCondition { controller.callbackCount() == 1 }

            // Emit ep2 before ep1's callback fires.
            assertTrue(flow.tryEmit(ProxyState(ep2)))
            awaitCondition { controller.callbackCount() == 2 }
            assertEquals(1, resets.get())
            assertEquals(ep2, pm.awaitEndpoint())

            // Stale callback from ep1 must NOT open the gate or recreate WebViews.
            controller.completeAt(0)
            assertEquals(0, recreates.get())
            assertFalse(pm.awaitWebAllowed())

            // Newer callback from ep2 opens the gate.
            controller.completeAt(1)
            assertEquals(1, recreates.get())
            assertTrue(pm.awaitWebAllowed())
        } finally {
            pm.close()
        }
    }

    @Test
    fun blockedWaitersDoNotStarveCollector() {
        val flow = MutableSharedFlow<ProxyState>(replay = 1)
        val controller = FakeWebProxyController(autoComplete = true)
        val pm = ProxyManager(
            states = flow,
            webProxyController = controller,
            runOnMain = { it() },
            isMainThread = { false },
            awaitTimeoutMs = 3_000L,
        )
        pm.start()
        val waiterPool = Executors.newFixedThreadPool(8)
        val done = CountDownLatch(8)
        val results = CopyOnWriteArrayList<Boolean>()
        try {
            repeat(8) {
                waiterPool.submit {
                    try {
                        pm.awaitEndpoint()
                        results.add(pm.awaitWebAllowed())
                    } finally {
                        done.countDown()
                    }
                }
            }
            Thread.sleep(30)
            assertTrue(flow.tryEmit(ProxyState(null)))
            assertTrue(done.await(3, TimeUnit.SECONDS))
            assertEquals(8, results.size)
            assertTrue(results.all { it })
        } finally {
            waiterPool.shutdownNow()
            pm.close()
        }
    }
}
