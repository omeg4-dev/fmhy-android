package io.github.omeg4dev.fmhy.browser.adblock

import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import io.github.omeg4dev.fmhy.browser.BlockedRequest
import io.github.omeg4dev.fmhy.browser.RescueDecision
import io.github.omeg4dev.fmhy.data.BlockingPrefs
import io.github.omeg4dev.fmhy.data.SettingsRepository
import io.github.omeg4dev.fmhy.data.adblock.FilterEngine
import io.github.omeg4dev.fmhy.data.adblock.FilterRequest
import io.github.omeg4dev.fmhy.data.adblock.ListState
import io.github.omeg4dev.fmhy.data.adblock.MatchResult
import io.github.omeg4dev.fmhy.data.adblock.NeuteredResources
import io.github.omeg4dev.fmhy.data.adblock.ResourceType
import io.github.omeg4dev.fmhy.data.adblock.ResourceTypes
import io.github.omeg4dev.fmhy.data.user.SitePrefs
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
class AdblockService(
    private val settingsRepository: SettingsRepository,
    private val updater: AdblockUpdater,
    private val scriptletLibrary: ScriptletLibrary,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val engineRef = AtomicReference<FilterEngine?>(null)
    val currentEngine: FilterEngine? get() = engineRef.get()

    private val _status = MutableStateFlow(AdblockStatus())
    val status: StateFlow<AdblockStatus> = _status.asStateFlow()

    private val _listStates = MutableStateFlow<Map<String, ListState>>(emptyMap())
    val listStates: StateFlow<Map<String, ListState>> = _listStates.asStateFlow()

    @Volatile
    var currentBlockingPrefs: BlockingPrefs = BlockingPrefs()
        private set

    init {
        // Keep in-memory cache of blockingPrefs so shouldInterceptRequest never does IO
        coroutineScope.launch {
            settingsRepository.blockingPrefs.collect { prefs ->
                currentBlockingPrefs = prefs
            }
        }

        // Auto-rebuild on enabledLists or customRules changes with 1s debounce
        coroutineScope.launch {
            settingsRepository.blockingPrefs
                .map { Pair(it.enabledLists, it.customRules) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(1000L)
                .collect { (enabledLists, customRules) ->
                    rebuild(enabledLists, customRules)
                }
        }
    }

    fun initialize() {
        coroutineScope.launch(ioDispatcher) {
            _listStates.value = updater.states()
            val loaded = updater.loadEngine()
            if (loaded != null) {
                engineRef.set(loaded)
                updateStatusFromEngine(loaded, updating = false, error = null)
                logEngineStats("Loaded", loaded)
            }

            val prefs = settingsRepository.blockingPrefs.first()
            val states = updater.states()
            _listStates.value = states
            val now = clock()
            val anyExpired = prefs.enabledLists.any { id ->
                val s = states[id]
                s == null || now >= s.expiresAt
            }

            if (loaded == null || (anyExpired && prefs.autoUpdateLists)) {
                updateInternal(force = false)
            }
        }
    }

    suspend fun updateNow() {
        updateInternal(force = true)
    }

    private suspend fun updateInternal(force: Boolean) = withContext(ioDispatcher) {
        _status.value = _status.value.copy(updating = true, error = null, progress = 0f)
        try {
            val prefs = settingsRepository.blockingPrefs.first()
            var newEngine: FilterEngine
            val time = measureTimeMillis {
                newEngine = updater.update(prefs.enabledLists, prefs.customRules, force) { done, total ->
                    // Downloads are ~90 %, building the engine the rest.
                    val p = if (total == 0) 0.9f else 0.9f * done / total
                    _status.value = _status.value.copy(progress = p)
                }
            }
            engineRef.set(newEngine)
            _listStates.value = updater.states()
            updateStatusFromEngine(newEngine, updating = false, error = null)
            logEngineStats("Updated ($time ms)", newEngine)
        } catch (e: Exception) {
            logE(TAG, "Update failed: ${e.message}", e)
            _status.value = _status.value.copy(updating = false, error = e.message ?: e.toString(), progress = null)
        }
    }

    private suspend fun rebuild(enabledLists: Set<String>, customRules: String) = withContext(ioDispatcher) {
        _status.value = _status.value.copy(updating = true, error = null)
        try {
            var newEngine: FilterEngine
            val time = measureTimeMillis {
                newEngine = updater.rebuild(enabledLists, customRules)
            }
            engineRef.set(newEngine)
            _listStates.value = updater.states()
            updateStatusFromEngine(newEngine, updating = false, error = null)
            logEngineStats("Rebuilt ($time ms)", newEngine)
        } catch (e: Exception) {
            logE(TAG, "Rebuild failed: ${e.message}", e)
            _status.value = _status.value.copy(updating = false, error = e.message ?: e.toString())
        }
    }

    private fun updateStatusFromEngine(engine: FilterEngine, updating: Boolean, error: String?) {
        val totalRules = engine.stats.networkRules + engine.stats.cosmeticRules + engine.stats.scriptletRules
        val states = _listStates.value
        val lastUpdated = states.values.map { it.fetchedAt }.maxOrNull()
        _status.value = AdblockStatus(
            ready = true,
            updating = updating,
            ruleCount = totalRules,
            lastUpdated = lastUpdated,
            error = error,
        )
    }

    private fun logEngineStats(action: String, engine: FilterEngine) {
        val totalBytes = _listStates.value.values.sumOf { it.bytes }
        logI(
            TAG,
            "$action engine: network=${engine.stats.networkRules}, cosmetic=${engine.stats.cosmeticRules}, " +
                "scriptlet=${engine.stats.scriptletRules}, lists=${_listStates.value.size}, totalBytes=$totalBytes"
        )
    }

    fun isHostAllowlisted(host: String, allowlist: Set<String>): Boolean {
        if (host.isEmpty() || allowlist.isEmpty()) return false
        val lower = host.lowercase()
        for (allowed in allowlist) {
            val lowerAllowed = allowed.lowercase()
            if (lower == lowerAllowed || lower.endsWith(".$lowerAllowed")) {
                return true
            }
        }
        return false
    }

    fun shouldInterceptRequest(
        request: WebResourceRequest,
        sourceUrl: String?,
        sitePrefs: SitePrefs?,
        tabAllowedOnceUrls: Set<String>,
        tabBypass: Set<String> = emptySet(),
        skipBlockingFor: String? = null,
        onBlocked: (BlockedRequest) -> Unit,
        bridgeName: String,
        onMainFrameBlocked: (rule: String) -> Unit = {},
    ): WebResourceResponse? {
        val prefs = currentBlockingPrefs
        if (!prefs.adblock) return null
        if (sitePrefs?.adblock == false) return null

        val urlStr = request.url.toString()
        if (skipBlockingFor != null && (RescueDecision.sameAddress(sourceUrl ?: urlStr, skipBlockingFor) ||
            (request.isForMainFrame && RescueDecision.sameAddress(urlStr, skipBlockingFor)))) {
            return null
        }

        if (tabAllowedOnceUrls.contains(urlStr)) return null

        if (tabBypass.isNotEmpty()) {
            val pageSite = AllowRule.pageSite(sourceUrl ?: urlStr)
            val host = request.url.host.orEmpty().lowercase()
            if (pageSite != null && host.isNotEmpty() && isBypassed(tabBypass, pageSite, host)) {
                return null
            }
        }

        val pageHost = try {
            Uri.parse(sourceUrl ?: urlStr).host.orEmpty()
        } catch (_: Exception) {
            ""
        }
        if (isHostAllowlisted(pageHost, prefs.allowlist)) return null

        val acceptHeader = request.requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value
        val type = ResourceTypes.infer(
            url = urlStr,
            isMainFrame = request.isForMainFrame,
            acceptHeader = acceptHeader,
        )

        val engine = engineRef.get() ?: return null
        val filterReq = FilterRequest(
            url = urlStr,
            sourceUrl = sourceUrl ?: urlStr,
            type = type,
        )
        val result = effectiveResult(engine.match(filterReq), urlStr, sourceUrl ?: urlStr, type)

        return when (result) {
            is MatchResult.NoMatch, is MatchResult.Allow -> null
            is MatchResult.Redirect -> {
                onBlocked(
                    BlockedRequest(
                        host = request.url.host.orEmpty().lowercase(),
                        url = urlStr,
                        type = type,
                        rule = result.rule,
                    )
                )
                val neutered = NeuteredResources.get(result.resource)
                if (neutered != null) {
                    WebResourceResponse(
                        neutered.mimeType,
                        "UTF-8",
                        200,
                        "OK",
                        mapOf("Access-Control-Allow-Origin" to "*"),
                        ByteArrayInputStream(neutered.bytes)
                    )
                } else {
                    emptyResponse(type)
                }
            }
            is MatchResult.Block -> {
                if (request.isForMainFrame && type == ResourceType.DOCUMENT) {
                    onMainFrameBlocked(result.rule)
                    val hostToShow = pageHost.ifEmpty { request.url.host.orEmpty() }
                    val blockHtml = buildBlockPageHtml(
                        host = hostToShow,
                        rule = result.rule,
                        url = urlStr,
                        bridgeName = bridgeName,
                    )
                    WebResourceResponse(
                        "text/html",
                        "UTF-8",
                        200,
                        "OK",
                        mapOf("Cache-Control" to "no-cache"),
                        ByteArrayInputStream(blockHtml.toByteArray(Charsets.UTF_8))
                    )
                } else {
                    onBlocked(
                        BlockedRequest(
                            host = request.url.host.orEmpty().lowercase(),
                            url = urlStr,
                            type = type,
                            rule = result.rule,
                        )
                    )
                    // 404 (not an empty 200) so <script>/<img> fire onerror like a cancelled request.
                    emptyResponse(type, failed = true)
                }
            }
        }
    }

    private fun isBypassed(tabBypass: Set<String>, pageSite: String, host: String): Boolean {
        var currentHost: String? = host
        while (currentHost != null) {
            if (tabBypass.contains(AllowRule.bypassKey(pageSite, currentHost))) {
                return true
            }
            val dotIndex = currentHost.indexOf('.')
            if (dotIndex == -1) break
            val nextHost = currentHost.substring(dotIndex + 1)
            if (nextHost.contains('.')) {
                currentHost = nextHost
            } else {
                break
            }
        }
        return false
    }

    private fun emptyResponse(type: ResourceType, failed: Boolean = false): WebResourceResponse {
        val mime = when (type) {
            ResourceType.SCRIPT -> "application/javascript"
            ResourceType.STYLESHEET -> "text/css"
            ResourceType.IMAGE -> "image/png"
            ResourceType.MEDIA -> "video/mp4"
            ResourceType.FONT -> "font/woff2"
            ResourceType.XHR -> "application/json"
            else -> "text/plain"
        }
        return if (failed) {
            WebResourceResponse(mime, "UTF-8", 404, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        } else {
            WebResourceResponse(mime, "UTF-8", 200, "OK", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
    }

    private fun buildBlockPageHtml(host: String, rule: String, url: String, bridgeName: String): String {
        val escapedRule = rule.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val escapedHost = host.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val escapedUrl = url.replace("\"", "\\\"").replace("'", "\\'")

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Blocked</title>
            <style>
              body { font-family: system-ui, -apple-system, sans-serif; background: #121212; color: #e0e0e0; display: flex; align-items: center; justify-content: center; min-height: 100vh; margin: 0; padding: 16px; box-sizing: border-box; }
              .card { background: #1e1e1e; padding: 24px; border-radius: 16px; max-width: 420px; width: 100%; box-shadow: 0 4px 24px rgba(0,0,0,0.6); text-align: center; }
              h1 { font-size: 20px; color: #ff5252; margin: 0 0 12px; }
              p { font-size: 14px; color: #aaa; margin: 8px 0; word-break: break-all; }
              details { margin: 12px 0; }
              summary { font-size: 13px; color: #aaa; cursor: pointer; }
              .rule { font-family: monospace; background: #2a2a2a; color: #ffb74d; padding: 8px 12px; border-radius: 6px; font-size: 13px; display: inline-block; margin: 8px 0; word-break: break-all; }
              .buttons { display: flex; gap: 12px; margin-top: 24px; }
              button { flex: 1; padding: 12px; border: none; border-radius: 8px; font-weight: bold; font-size: 14px; cursor: pointer; }
              .btn-back { background: #ff5252; color: #fff; }
              .btn-continue { background: #333; color: #fff; }
            </style>
            </head>
            <body>
            <div class="card">
              <h1>Blocked</h1>
              <p>An ad-blocking filter blocks this site.<br><strong>$escapedHost</strong></p>
              <details><summary>Details</summary><div class="rule">$escapedRule</div></details>
              <div class="buttons">
                <button class="btn-back" onclick="history.back()">Go back</button>
                <button class="btn-continue" onclick="openAnyway()">Continue anyway</button>
              </div>
            </div>
            <script>
            function openAnyway() {
              if (window["$bridgeName"] && window["$bridgeName"].openAnyway) {
                window["$bridgeName"].openAnyway("$escapedUrl");
              }
            }
            </script>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private const val TAG = "FmhyAdblock"

        /**
         * Returns [MatchResult.NoMatch] if the match is a block or redirect on an essential request
         * that should be exempted; otherwise returns [result] unchanged.
         */
        fun effectiveResult(
            result: MatchResult,
            requestUrl: String,
            pageUrl: String,
            type: ResourceType,
        ): MatchResult {
            val rule = when (result) {
                is MatchResult.Block -> result.rule
                is MatchResult.Redirect -> result.rule
                else -> return result
            }
            return if (EssentialRequests.exempt(requestUrl, pageUrl, type, rule)) {
                MatchResult.NoMatch
            } else {
                result
            }
        }

        private fun logI(tag: String, msg: String) {
            try {
                Log.i(tag, msg)
            } catch (_: Throwable) {
                println("INFO: [$tag] $msg")
            }
        }

        private fun logE(tag: String, msg: String, tr: Throwable? = null) {
            try {
                if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
            } catch (_: Throwable) {
                println("ERROR: [$tag] $msg ${tr?.message.orEmpty()}")
            }
        }
    }
}
