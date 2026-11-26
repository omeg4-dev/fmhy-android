package io.github.omeg4dev.fmhy.net

import android.os.Handler
import android.os.Looper
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Abstraction over WebView proxy override so [ProxyManager] is unit-testable on the JVM. */
interface WebProxyController {
    val supported: Boolean
    fun apply(rule: String, onDone: () -> Unit)
    fun clear(onDone: () -> Unit)
}

object AndroidWebProxyController : WebProxyController {
    private val directExecutor = Executor { runnable -> runnable.run() }

    override val supported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    override fun apply(rule: String, onDone: () -> Unit) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            val config = ProxyConfig.Builder().addProxyRule(rule).build()
            ProxyController.getInstance().setProxyOverride(config, directExecutor, onDone)
        } else {
            error("WebView PROXY_OVERRIDE is not supported")
        }
    }

    override fun clear(onDone: () -> Unit) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().clearProxyOverride(directExecutor, onDone)
        } else {
            onDone()
        }
    }
}

data class ProxyState(
    val endpoint: ProxyEndpoint?,
    val allowJavaScript: Boolean = false,
) {
    companion object {
        fun fromPrefs(prefs: io.github.omeg4dev.fmhy.data.ProtectionPrefs): ProxyState {
            val endpoint = ProxyEndpoint.resolve(prefs)
            return ProxyState(
                endpoint = endpoint,
                allowJavaScript = endpoint != null && prefs.proxyAllowJavaScript,
            )
        }
    }
}

class ProxyTimeoutException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Coordinates proxy configuration across OkHttp and WebView with fail-closed semantics.
 *
 * Collects [states] on a dedicated dispatcher so blocked waiters cannot starve updates.
 */
class ProxyManager(
    private val states: Flow<ProxyState>,
    private val webProxyController: WebProxyController,
    private val onNetworkReset: () -> Unit = {},
    private val onApplyJavaScript: () -> Unit = {},
    private val onRecreateWebViews: () -> Unit = {},
    private val runOnMain: (() -> Unit) -> Unit = ::defaultRunOnMain,
    private val isMainThread: () -> Boolean = ::defaultIsMainThread,
    private val collectorDispatcher: CoroutineDispatcher? = null,
    private val awaitTimeoutMs: Long = DEFAULT_AWAIT_TIMEOUT_MS,
    private val nanoClock: () -> Long = System::nanoTime,
) {
    private val started = AtomicBoolean(false)
    private val lock = ReentrantLock()
    private val stateChanged = lock.newCondition()

    private var ownedExecutor: ExecutorService? = null
    private var collectorScope: CoroutineScope? = null

    @Volatile
    private var loaded: Boolean = false

    @Volatile
    private var endpoint: ProxyEndpoint? = null

    @Volatile
    private var allowJavaScript: Boolean = false

    @Volatile
    private var webAllowed: Boolean = false

    private var webFailed: Boolean = false
    private var generation: Long = 0L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        val dispatcher = collectorDispatcher ?: Executors.newSingleThreadExecutor { r ->
            Thread(r, "ProxyManager").apply { isDaemon = true }
        }.also { ownedExecutor = it }.asCoroutineDispatcher()

        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        collectorScope = scope
        scope.launch {
            states.collect { next ->
                handleState(next)
            }
        }
    }

    fun close() {
        collectorScope?.cancel()
        collectorScope = null
        ownedExecutor?.shutdownNow()
        ownedExecutor = null
    }

    fun isLoaded(): Boolean = loaded

    fun currentEndpoint(): ProxyEndpoint? = endpoint

    fun currentAllowJavaScript(): Boolean = allowJavaScript

    /**
     * Waits until initial proxy settings are loaded and returns the current [ProxyEndpoint]
     * (or `null` when Off). Throws [ProxyTimeoutException] on timeout; never falls back to direct.
     */
    fun awaitEndpoint(): ProxyEndpoint? {
        if (loaded) return endpoint
        check(!isMainThread()) { "awaitEndpoint() must not be called on the main thread" }
        val timeoutNs = TimeUnit.MILLISECONDS.toNanos(awaitTimeoutMs)
        val deadlineNs = nanoClock() + timeoutNs
        lock.withLock {
            while (!loaded) {
                val remainingNs = deadlineNs - nanoClock()
                if (remainingNs <= 0L) {
                    throw ProxyTimeoutException("Timed out waiting for proxy configuration")
                }
                try {
                    stateChanged.awaitNanos(remainingNs)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw ProxyTimeoutException("Interrupted waiting for proxy configuration", e)
                }
            }
            return endpoint
        }
    }

    /**
     * Fast volatile check when WebView traffic is allowed; otherwise blocks until the WebView
     * proxy override has been applied (or fails closed returning `false` on timeout/error/unsupported).
     */
    fun awaitWebAllowed(): Boolean {
        if (webAllowed) return true
        check(!isMainThread()) { "awaitWebAllowed() must not be called on the main thread" }
        val timeoutNs = TimeUnit.MILLISECONDS.toNanos(awaitTimeoutMs)
        val deadlineNs = nanoClock() + timeoutNs
        lock.withLock {
            while (true) {
                if (webAllowed) return true
                if (loaded && webFailed) return false
                val remainingNs = deadlineNs - nanoClock()
                if (remainingNs <= 0L) return false
                try {
                    stateChanged.awaitNanos(remainingNs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
    }

    private fun handleState(next: ProxyState) {
        val normalized = if (next.endpoint == null) ProxyState(null, allowJavaScript = false) else next
        val isFirst: Boolean
        val gen: Long
        var firstOff = false
        lock.withLock {
            isFirst = !loaded
            val currentState = ProxyState(endpoint, allowJavaScript)
            if (!isFirst && normalized == currentState && (normalized.endpoint == null || !webFailed)) {
                return
            }
            gen = ++generation
            endpoint = normalized.endpoint
            allowJavaScript = normalized.allowJavaScript
            loaded = true
            webFailed = false
            webAllowed = false
            stateChanged.signalAll()
            firstOff = isFirst && normalized.endpoint == null
        }

        if (firstOff) {
            // First load with Off: apply effective JavaScript on the main thread before opening the
            // gate, and do not touch ProxyController.
            runOnMain {
                if (!isGenerationCurrent(gen)) return@runOnMain
                try {
                    onApplyJavaScript()
                } catch (_: Throwable) {}
                lock.withLock {
                    if (generation == gen) {
                        webAllowed = true
                        stateChanged.signalAll()
                    }
                }
            }
            return
        }

        if (!isFirst) {
            try {
                onNetworkReset()
            } catch (_: Throwable) {}
        }

        if (normalized.endpoint != null) {
            applyProxiedWeb(gen, normalized.endpoint)
        } else {
            clearWebProxy(gen)
        }
    }

    private fun applyProxiedWeb(gen: Long, target: ProxyEndpoint) {
        val supported = try {
            webProxyController.supported
        } catch (_: Throwable) {
            false
        }
        if (!supported) {
            failWebIfCurrent(gen)
            return
        }
        runOnMain {
            if (!isGenerationCurrent(gen)) return@runOnMain
            try {
                webProxyController.apply(target.webViewRule) {
                    runOnMain {
                        completeWebChange(gen)
                    }
                }
            } catch (_: Throwable) {
                failWebIfCurrent(gen)
            }
        }
    }

    private fun clearWebProxy(gen: Long) {
        val supported = try {
            webProxyController.supported
        } catch (_: Throwable) {
            false
        }
        runOnMain {
            if (!isGenerationCurrent(gen)) return@runOnMain
            if (!supported) {
                completeWebChange(gen)
                return@runOnMain
            }
            try {
                webProxyController.clear {
                    runOnMain {
                        completeWebChange(gen)
                    }
                }
            } catch (_: Throwable) {
                completeWebChange(gen)
            }
        }
    }

    private fun completeWebChange(gen: Long) {
        if (!isGenerationCurrent(gen)) return
        try {
            onRecreateWebViews()
        } catch (_: Throwable) {}
        lock.withLock {
            if (generation == gen) {
                webFailed = false
                webAllowed = true
                stateChanged.signalAll()
            }
        }
    }

    private fun failWebIfCurrent(gen: Long) {
        lock.withLock {
            if (generation != gen) return
            webFailed = true
            webAllowed = false
            stateChanged.signalAll()
        }
        // Pages opened before the failure keep running their scripts (and WebRTC): destroy them too.
        runOnMain {
            if (!isGenerationCurrent(gen)) return@runOnMain
            try {
                onRecreateWebViews()
            } catch (_: Throwable) {}
        }
    }

    private fun isGenerationCurrent(gen: Long): Boolean = lock.withLock { generation == gen }

    companion object {
        const val DEFAULT_AWAIT_TIMEOUT_MS = 10_000L
    }
}

private fun defaultRunOnMain(action: () -> Unit) {
    try {
        val mainLooper = Looper.getMainLooper()
        if (mainLooper != null && Looper.myLooper() == mainLooper) {
            action()
        } else if (mainLooper != null) {
            Handler(mainLooper).post(action)
        } else {
            action()
        }
    } catch (_: Throwable) {
        action()
    }
}

private fun defaultIsMainThread(): Boolean = try {
    val mainLooper = Looper.getMainLooper()
    mainLooper != null && Looper.myLooper() == mainLooper
} catch (_: Throwable) {
    false
}
