package io.github.omeg4dev.fmhy.browser

/**
 * "Fix this site": a per-host anti-detection bundle for pages that demand the ad blocker be turned
 * off. Three layers, all gated by SitePrefs.fixSite for the host:
 *  - network: blocked sub-resources answer 200 (not 404) so `onerror`/failed-fetch probes see "loaded",
 *    and known detector scripts are replaced by an empty script (see [isDetectorUrl]);
 *  - page start: [script] stubs the ad globals and keeps bait elements visible;
 *  - after load: the same script finds the "disable your ad blocker" wall, hides it and undoes the scroll lock.
 */
object SiteFix {
    private val DETECTOR_URL = Regex(
        "(fuckadblock|blockadblock|adblock-?detect|detect-?adblock|anti-?adblock|adblocker-?detect|" +
            "adblock\\.js|ads-?blocked|adb-?check|abp-?detect|bab\\.js|blockerdetect)",
        RegexOption.IGNORE_CASE,
    )

    /** URL of a known ad-block detector script. */
    fun isDetectorUrl(url: String): Boolean = DETECTOR_URL.containsMatchIn(url.substringBefore('?'))

    /** Page-start script; asks the bridge whether this page's host has the fix before doing anything. */
    fun script(bridgeName: String): String = """
        (function() {
            try {
                var bridge = window["$bridgeName"];
                if (!bridge || !bridge.fixSite || !bridge.fixSite(window.location.href)) return;
                if (window.__fmhySiteFix) return;
                window.__fmhySiteFix = true;

                // --- 1. Ad globals and detector classes: detection sees "ads loaded" ---
                var chain = (function() {
                    var f = function() { return p; };
                    var p = new Proxy(f, {
                        get: function(_, k) {
                            if (k === Symbol.toPrimitive) return function() { return ''; };
                            if (k === 'then') return undefined;
                            return p;
                        },
                        apply: function() { return p; }
                    });
                    return p;
                })();
                try {
                    var ads = window.adsbygoogle || [];
                    ads.loaded = true;
                    ads.push = function() { return 0; };
                    window.adsbygoogle = ads;
                } catch (_) {}
                try { window.canRunAds = true; } catch (_) {}
                try {
                    if (!window.googletag) {
                        window.googletag = {
                            cmd: { push: function(fn) { try { fn(); } catch (_) {} return 0; } },
                            apiReady: true, pubadsReady: true,
                            pubads: function() { return chain; }, defineSlot: function() { return chain; },
                            defineOutOfPageSlot: function() { return chain; }, enableServices: function() {},
                            display: function() {}, destroySlots: function() { return true; }
                        };
                    }
                } catch (_) {}
                var Detector = function() {};
                Detector.prototype.onDetected = function() { return this; };
                Detector.prototype.onNotDetected = function(fn) {
                    if (typeof fn === 'function') setTimeout(function() { try { fn(); } catch (_) {} }, 0);
                    return this;
                };
                Detector.prototype.on = function(detected, fn) {
                    return detected ? this : this.onNotDetected(fn);
                };
                Detector.prototype.check = function() { return false; };
                Detector.prototype.setOption = function() { return this; };
                Detector.prototype.clearEvent = function() { return this; };
                Detector.prototype.emitEvent = function() { return this; };
                ['FuckAdBlock', 'BlockAdBlock'].forEach(function(n) {
                    try { window[n] = Detector; window[n.charAt(0).toLowerCase() + n.slice(1)] = new Detector(); } catch (_) {}
                });

                // --- 2. Bait elements stay laid out (detectors read offsetHeight/display) ---
                var css = document.createElement('style');
                css.id = '__fmhy_sitefix';
                css.textContent = 'html body .adsbox,html body .ad-placement,html body .ad-placeholder,html body .adbanner,' +
                    'html body .pub_300x250,html body .pub_300x250m,html body .pub_728x90,html body .text-ad,' +
                    'html body .textAd,html body .text_ad,html body .text_ads,html body .text-ads,' +
                    'html body .text-ad-links{display:block!important;visibility:visible!important;' +
                    'opacity:1!important;min-height:1px!important}';
                (document.head || document.documentElement).appendChild(css);

                // --- 3. The wall: hide it, undo the scroll lock ---
                var ASK = /(ad[\s\-]?block(er|ing)?|ads?[\s\-]?blocker|block(ing)? (the |our )?ads)/i;
                var ACT = /(disable|turn(ed)? off|pause|white-?list|allow|deactivate|detected|switch off|exception)/i;
                var LOCK_CLASSES = ['modal-open', 'no-scroll', 'noscroll', 'overflow-hidden', 'scroll-lock',
                    'scroll-locked', 'is-locked', 'locked', 'ab-lock', 'adblock-active', 'has-adblock'];
                var sweepTimer = 0;
                var found = false;

                function wallTextNodes() {
                    var out = [];
                    if (!document.body) return out;
                    var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
                    var n;
                    while ((n = w.nextNode())) {
                        var t = n.nodeValue;
                        if (t && t.length > 15 && t.length < 400 && ASK.test(t) && ACT.test(t)) out.push(n);
                    }
                    return out;
                }

                function textLen(e) { return (e.textContent || '').trim().length; }

                function hideWall(textNode) {
                    var c = textNode.parentElement;
                    if (!c || c === document.body || c === document.documentElement) return false;
                    var own = textLen(c);
                    // Climb to the container that holds the message and little else.
                    while (c.parentElement && c.parentElement !== document.body &&
                           c.parentElement !== document.documentElement && c.parentElement.id !== 'app' &&
                           c.parentElement.tagName !== 'MAIN' &&
                           textLen(c.parentElement) <= Math.max(own * 2, own + 200) &&
                           textLen(c.parentElement) <= 1200) {
                        c = c.parentElement;
                    }
                    if (c.hasAttribute('data-fmhy-fixed')) return false;
                    c.setAttribute('data-fmhy-fixed', '1');
                    c.style.setProperty('display', 'none', 'important');
                    return true;
                }

                function unlock() {
                    [document.documentElement, document.body].forEach(function(e) {
                        if (!e) return;
                        LOCK_CLASSES.forEach(function(k) { e.classList.remove(k); });
                        var cs = getComputedStyle(e);
                        if (cs.overflow === 'hidden' || cs.overflowY === 'hidden') {
                            e.style.setProperty('overflow', 'auto', 'important');
                        }
                        if (cs.position === 'fixed') e.style.setProperty('position', 'static', 'important');
                        if (e.style.paddingRight) e.style.removeProperty('padding-right');
                        if (/blur/.test(cs.filter)) e.style.setProperty('filter', 'none', 'important');
                    });
                    // Text-less full-screen fixed layers left behind (backdrops).
                    var vw = window.innerWidth, vh = window.innerHeight;
                    var kids = document.body ? document.body.children : [];
                    for (var i = 0; i < kids.length; i++) {
                        var e = kids[i];
                        if (e.hasAttribute('data-fmhy-fixed')) continue;
                        var cs = getComputedStyle(e);
                        if (cs.position !== 'fixed' || cs.display === 'none') continue;
                        var r = e.getBoundingClientRect();
                        if (r.width >= vw * 0.9 && r.height >= vh * 0.9 && textLen(e) === 0 &&
                            !e.querySelector('video,canvas,iframe,img')) {
                            e.setAttribute('data-fmhy-fixed', '1');
                            e.style.setProperty('display', 'none', 'important');
                        }
                    }
                }

                function sweep() {
                    sweepTimer = 0;
                    var hid = false;
                    wallTextNodes().forEach(function(n) { if (hideWall(n)) hid = true; });
                    if (hid) found = true;
                    if (found) unlock();
                }
                function schedule() {
                    if (!sweepTimer) sweepTimer = setTimeout(sweep, 400);
                }

                function start() {
                    sweep();
                    var obs = new MutationObserver(schedule);
                    obs.observe(document.documentElement, { childList: true, subtree: true, characterData: true });
                    // The wall may appear late (after a detection timeout); stop watching after a minute.
                    setTimeout(function() { obs.disconnect(); }, 60000);
                }
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', start, { once: true });
                } else {
                    start();
                }
            } catch (e) {}
        })();
    """.trimIndent()
}
