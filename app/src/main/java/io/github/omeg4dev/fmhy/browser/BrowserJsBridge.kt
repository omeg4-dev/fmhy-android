package io.github.omeg4dev.fmhy.browser

import android.net.Uri
import android.webkit.JavascriptInterface
import io.github.omeg4dev.fmhy.browser.adblock.AdblockService
import io.github.omeg4dev.fmhy.browser.adblock.ScriptletLibrary
import io.github.omeg4dev.fmhy.browser.download.DownloadRequest
import io.github.omeg4dev.fmhy.browser.download.VideoSources
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CosmeticsResponse(
    val css: String,
    val scriptlets: String,
    val generic: Boolean,
)

class BrowserJsBridge(
    private val tabId: Long,
    private val tabManager: TabManager,
    private val adblockService: AdblockService,
    private val scriptletLibrary: ScriptletLibrary,
    private val onPickerResult: (tabId: Long, selector: String) -> Unit,
    private val onPickerCancel: (tabId: Long) -> Unit,
) {
    @JavascriptInterface
    fun cosmetics(href: String): String {
        val tab = tabManager.getTabRecord(tabId) ?: return "{}"
        val prefs = adblockService.currentBlockingPrefs
        if (!prefs.adblock || tab.sitePrefs?.adblock == false || RescueDecision.sameAddress(href, tab.skipBlockingOnceUrl)) {
            return Json.encodeToString(CosmeticsResponse("", "", false))
        }

        val engine = adblockService.currentEngine
        val css = if (prefs.elementHiding) {
            engine?.cosmeticCss(href).orEmpty()
        } else {
            ""
        }

        val scriptletsCode = if (prefs.scriptlets) {
            val list = engine?.scriptlets(href).orEmpty()
            scriptletLibrary.build(list)
        } else {
            ""
        }

        val generic = prefs.elementHiding && engine != null
        return Json.encodeToString(CosmeticsResponse(css, scriptletsCode, generic))
    }

    @JavascriptInterface
    fun generic(href: String, classesJson: String, idsJson: String): String {
        val tab = tabManager.getTabRecord(tabId) ?: return ""
        val prefs = adblockService.currentBlockingPrefs
        if (!prefs.adblock || tab.sitePrefs?.adblock == false || !prefs.elementHiding) {
            return ""
        }

        val engine = adblockService.currentEngine ?: return ""
        val classes = try { Json.decodeFromString<List<String>>(classesJson) } catch (_: Exception) { emptyList() }
        val ids = try { Json.decodeFromString<List<String>>(idsJson) } catch (_: Exception) { emptyList() }
        val selectors = engine.genericSelectors(href, classes, ids)
        if (selectors.isEmpty()) return ""
        return selectors.chunked(500).joinToString("\n") { chunk ->
            "${chunk.joinToString(",")}{display:none!important}"
        }
    }

    @JavascriptInterface
    fun openAnyway(url: String) {
        tabManager.allowUrlOnce(tabId, url)
        tabManager.postToMain {
            val record = tabManager.getTabRecord(tabId)
            record?.let { r -> Uri.parse(url).host?.let { r.allowedHosts.add(it) }; r.interstitialState = null }
            record?.webView?.reload()
        }
    }

    private fun isNullOrDataOrigin(): Boolean {
        val record = tabManager.getTabRecord(tabId) ?: return false
        val wvUrl = record.webView?.url
        return wvUrl == null || wvUrl == "about:blank" || wvUrl.startsWith("data:")
    }

    @JavascriptInterface
    fun useMobileDataThisTime() {
        if (!isNullOrDataOrigin()) return
        tabManager.postToMain {
            val record = tabManager.getTabRecord(tabId) ?: return@postToMain
            tabManager.allowMobileOnce(tabId)
            tabManager.loadUrl(tabId, record.url)
        }
    }

    @JavascriptInterface
    fun openDataUsageSettings() {
        if (!isNullOrDataOrigin()) return
        tabManager.postToMain {
            tabManager.openDataUsageSettings()
        }
    }

    @JavascriptInterface
    fun pickerResult(selector: String) {
        tabManager.postToMain {
            onPickerResult(tabId, selector)
        }
    }

    @JavascriptInterface
    fun pickerCancel() {
        tabManager.postToMain {
            onPickerCancel(tabId)
        }
    }

    /** "muted" when unmuted media may not start without a user gesture, otherwise "any". */
    @JavascriptInterface
    fun autoplayMode(): String = autoplayScriptMode(tabManager.currentBrowserPrefs.autoplay)

    @JavascriptInterface
    fun hasMedia(found: Boolean) {
        if (!found) return
        tabManager.postToMain {
            tabManager.setHasMedia(tabId)
        }
    }

    /** Speed the page's media should play at; 0 when the app has no opinion. */
    @JavascriptInterface
    fun desiredRate(): Float = tabManager.getTabRecord(tabId)?.desiredRate ?: 0f

    @JavascriptInterface
    fun videoSize(w: Int, h: Int) {
        tabManager.postToMain {
            tabManager.setVideoSize(tabId, w, h)
        }
    }

    @JavascriptInterface
    fun mediaEvent(type: String) {
        tabManager.postToMain {
            tabManager.setVideoPlaying(tabId, type == "play")
        }
    }

    @JavascriptInterface
    fun videoSources(json: String, frame: String) {
        if (json.length > 64_000 || frame.length > 4_000) return
        tabManager.postToMain {
            // The frame address comes from page script: only an http(s) address may serve as the base.
            val baseUrl = frame.takeIf { DownloadRequest.isHttpUrl(it) } ?: tabManager.getTabRecord(tabId)?.url.orEmpty()
            val list = VideoSources.parse(json, baseUrl)
            tabManager.setVideoSources(tabId, frame, list)
        }
    }

    companion object {
        fun buildCosmeticsScript(bridgeName: String, sendGpc: Boolean = true): String {
            return """
                (function() {
                    try {
                        const bridge = window["$bridgeName"];
                        if (!bridge) return;

                        if ($sendGpc) {
                            try {
                                Object.defineProperty(navigator, 'globalPrivacyControl', {
                                    get: () => true,
                                    configurable: true
                                });
                            } catch(_) {}
                        }

                        try {
                            const reportMedia = function(evt) {
                                try { bridge.mediaEvent(evt); } catch(_) {}
                            };
                            document.addEventListener('play', function(e) {
                                if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
                                    reportMedia('play');
                                }
                            }, true);
                            document.addEventListener('pause', function(e) {
                                if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
                                    reportMedia('pause');
                                }
                            }, true);
                            document.addEventListener('ended', function(e) {
                                if (e.target && (e.target.tagName === 'VIDEO' || e.target.tagName === 'AUDIO')) {
                                    reportMedia('ended');
                                }
                            }, true);
                        } catch(_) {}

                        if (!bridge.cosmetics) return;
                        const resStr = bridge.cosmetics(window.location.href);
                        if (!resStr) return;
                        const data = JSON.parse(resStr);

                        if (data.css) {
                            const style = document.createElement('style');
                            style.id = '__fmhy_cosmetics';
                            style.textContent = data.css;
                            const target = document.head || document.documentElement;
                            if (target) {
                                target.appendChild(style);
                            } else {
                                const obs = new MutationObserver(function() {
                                    const t = document.head || document.documentElement;
                                    if (t) {
                                        obs.disconnect();
                                        t.appendChild(style);
                                    }
                                });
                                obs.observe(document, { childList: true });
                            }
                        }

                        if (data.scriptlets) {
                            try {
                                (new Function(data.scriptlets))();
                            } catch (e) {
                                console.error('[FMHY scriptlet error]', e);
                            }
                        }

                        if (data.generic) {
                            const seenClasses = new Set();
                            const seenIds = new Set();
                            const pendingClasses = new Set();
                            const pendingIds = new Set();
                            let timer = null;

                            function collect(root) {
                                if (!root) return;
                                const elems = root.querySelectorAll ? root.querySelectorAll('*') : [];
                                for (let i = 0; i < elems.length; i++) {
                                    const el = elems[i];
                                    if (el.id && !seenIds.has(el.id)) {
                                        seenIds.add(el.id);
                                        if (pendingIds.size < 1000) pendingIds.add(el.id);
                                    }
                                    if (el.classList) {
                                        for (let j = 0; j < el.classList.length; j++) {
                                            const c = el.classList.item(j);
                                            if (c && !seenClasses.has(c)) {
                                                seenClasses.add(c);
                                                if (pendingClasses.size < 1000) pendingClasses.add(c);
                                            }
                                        }
                                    }
                                }
                            }

                            function flush() {
                                timer = null;
                                if (pendingClasses.size === 0 && pendingIds.size === 0) return;
                                const cArr = Array.from(pendingClasses);
                                const iArr = Array.from(pendingIds);
                                pendingClasses.clear();
                                pendingIds.clear();

                                const newCss = bridge.generic(window.location.href, JSON.stringify(cArr), JSON.stringify(iArr));
                                if (newCss) {
                                    let gStyle = document.getElementById('__fmhy_generic_cosmetics');
                                    if (!gStyle) {
                                        gStyle = document.createElement('style');
                                        gStyle.id = '__fmhy_generic_cosmetics';
                                        (document.head || document.documentElement).appendChild(gStyle);
                                    }
                                    gStyle.textContent += '\n' + newCss;
                                }
                            }

                            function schedule() {
                                if (!timer) timer = setTimeout(flush, 300);
                            }

                            collect(document.documentElement || document);
                            schedule();

                            const observer = new MutationObserver(function(muts) {
                                for (let i = 0; i < muts.length; i++) {
                                    const m = muts[i];
                                    for (let j = 0; j < m.addedNodes.length; j++) {
                                        const n = m.addedNodes[j];
                                        if (n.nodeType === 1) {
                                            if (n.id && !seenIds.has(n.id)) {
                                                seenIds.add(n.id);
                                                if (pendingIds.size < 1000) pendingIds.add(n.id);
                                            }
                                            if (n.classList) {
                                                for (let k = 0; k < n.classList.length; k++) {
                                                    const c = n.classList.item(k);
                                                    if (c && !seenClasses.has(c)) {
                                                        seenClasses.add(c);
                                                        if (pendingClasses.size < 1000) pendingClasses.add(c);
                                                    }
                                                }
                                            }
                                            collect(n);
                                        }
                                    }
                                }
                                if (pendingClasses.size > 0 || pendingIds.size > 0) schedule();
                            });
                            observer.observe(document.documentElement || document, { childList: true, subtree: true });
                        }
                    } catch (e) {
                        console.error('[FMHY cosmetics error]', e);
                    }
                })();
            """.trimIndent()
        }
    }
}
