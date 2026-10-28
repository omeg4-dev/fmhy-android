package io.github.omeg4dev.fmhy.browser

/** Page script that watches <video>/<audio> elements and reports what the app needs to know. */
object VideoJs {
    /** Pauses whatever is playing and remembers it so [RESUME_MEDIA] can carry on. */
    const val PAUSE_MEDIA = "(function(){try{document.querySelectorAll('video,audio').forEach(function(m){" +
        "if(!m.paused){m.__fmhyWasPlaying=true;m.pause();}});}catch(e){}})();"

    /** Resumes what [PAUSE_MEDIA] paused, else the first media element. */
    const val RESUME_MEDIA = "(function(){try{var l=Array.prototype.slice.call(document.querySelectorAll('video,audio'));" +
        "var t=l.filter(function(m){return m.__fmhyWasPlaying;});if(!t.length)t=l.slice(0,1);" +
        "t.forEach(function(m){m.__fmhyWasPlaying=false;var p=m.play();if(p&&p.catch)p.catch(function(){});});}catch(e){}})();"

    /** JS that jumps the fullscreen video (else the first one on the page) by [seconds]. */
    fun seekCall(seconds: Int): String = "(function(d){try{" +
        "var f=document.fullscreenElement,v=null;" +
        "if(f){v=(f.tagName==='VIDEO')?f:f.querySelector('video');}" +
        "if(!v)v=document.querySelector('video');" +
        "if(v){var t=v.currentTime+d;if(isFinite(v.duration))t=Math.min(t,v.duration);v.currentTime=Math.max(0,t);}" +
        "}catch(e){}})($seconds);"

    /** JS that sets the playback speed on the page's media; a rate of 0 or less hands control back to the page. */
    fun setAutoplayCall(mode: String): String =
        "window.__fmhyVideo&&window.__fmhyVideo.setAutoplay('$mode');"

    /** JS that makes the playing video fill the page (on) or puts the page back (off), for picture-in-picture. */
    fun pipCall(on: Boolean): String = "window.__fmhyVideo&&window.__fmhyVideo.pip($on);"

    fun setRateCall(rate: Float): String =
        "window.__fmhyVideo&&window.__fmhyVideo.setRate(${if (rate > 0f) rate.toString() else "null"});"

    fun script(bridgeName: String): String = """
(function() {
    try {
        if (window.__fmhyVideo) return;
        var B = window["$bridgeName"];
        if (!B) return;
        var V = window.__fmhyVideo = {};
        function isMedia(t) { return t && (t.tagName === 'VIDEO' || t.tagName === 'AUDIO'); }
        function reportSize(v) {
            try {
                if (v && v.tagName === 'VIDEO' && v.videoWidth > 0 && v.videoHeight > 0 && B.videoSize) {
                    B.videoSize(v.videoWidth, v.videoHeight);
                }
            } catch (_) {}
        }
        // The playing (else first) video's rectangle in device pixels, so picture-in-picture can start from it.
        // Only the top page reports it: an iframe's rectangle is relative to the frame, not the WebView.
        var rectTimer = 0;
        function pickVideo() {
            var list = document.querySelectorAll('video'), first = null;
            for (var i = 0; i < list.length; i++) {
                if (!first) first = list[i];
                if (!list[i].paused && !list[i].ended) return list[i];
            }
            return first;
        }
        function reportRect() {
            rectTimer = 0;
            try {
                if (window.top !== window || !B.videoRect) return;
                var v = pickVideo();
                if (!v) return;
                var r = v.getBoundingClientRect(), d = window.devicePixelRatio || 1;
                B.videoRect(Math.round(r.left * d), Math.round(r.top * d), Math.round(r.right * d), Math.round(r.bottom * d));
            } catch (_) {}
        }
        function scheduleRect() { if (!rectTimer) rectTimer = setTimeout(reportRect, 100); }
        // Picture-in-picture: the window is the page's viewport, so pin the video to fill it and drop its controls.
        var PIP_CSS = 'position:fixed;top:0;left:0;width:100vw;height:100vh;max-width:none;max-height:none;' +
            'margin:0;padding:0;transform:none;object-fit:contain;background:#000;z-index:2147483647;border:0';
        function pinned(el, on) {
            if (on) {
                if (el.__fmhyPip) return;
                el.__fmhyPip = { style: el.getAttribute('style'), controls: el.controls };
                el.style.cssText = PIP_CSS.replace(/;/g, ' !important;') + ' !important';
                if (el.tagName === 'VIDEO') el.controls = false;
            } else if (el.__fmhyPip) {
                var o = el.__fmhyPip; el.__fmhyPip = null;
                if (o.style === null) el.removeAttribute('style'); else el.setAttribute('style', o.style);
                if (el.tagName === 'VIDEO') el.controls = o.controls;
            }
        }
        // Messages between frames are page-controlled, so they only count while picture-in-picture is on: the app
        // switches the top page (trusted), the top page switches its frames, and only a parent may switch a frame.
        var pipActive = false;
        function applyPip(on) {
            pipActive = on;
            try {
                var v = pickVideo();
                if (v && (on ? !v.paused : true)) {
                    pinned(v, on);
                    document.documentElement.style.setProperty('overflow', on ? 'hidden' : '', on ? 'important' : '');
                    if (on && window.top !== window) window.parent.postMessage('fmhyPipHit', '*');
                }
                var frames = document.querySelectorAll('iframe');
                for (var i = 0; i < frames.length; i++) {
                    if (!on) pinned(frames[i], false);
                    try { frames[i].contentWindow.postMessage(on ? 'fmhyPipOn' : 'fmhyPipOff', '*'); } catch (_) {}
                }
            } catch (_) {}
        }
        V.pip = applyPip;
        window.addEventListener('message', function(e) {
            try {
                if (e.data === 'fmhyPipOn' || e.data === 'fmhyPipOff') {
                    if (window.top !== window && e.source === window.parent) applyPip(e.data === 'fmhyPipOn');
                } else if (e.data === 'fmhyPipHit') {
                    if (!pipActive) return;
                    var frames = document.querySelectorAll('iframe');
                    for (var i = 0; i < frames.length; i++) {
                        if (frames[i].contentWindow === e.source) {
                            pinned(frames[i], true);
                            document.documentElement.style.setProperty('overflow', 'hidden', 'important');
                            if (window.top !== window) window.parent.postMessage('fmhyPipHit', '*');
                        }
                    }
                }
            } catch (_) {}
        });
        window.addEventListener('scroll', scheduleRect, true);
        window.addEventListener('resize', scheduleRect);
        // Playback speed. rate === null means the app has no opinion and the page is left alone.
        var rate = null, reported = false;
        function applyRate(m) {
            try { if (rate !== null && Math.abs(m.playbackRate - rate) > 0.001) m.playbackRate = rate; } catch (_) {}
        }
        function pullRate() {
            try {
                if (B.desiredRate) { var r = B.desiredRate(); if (r > 0) rate = r; }
            } catch (_) {}
        }
        function hook(m) {
            if (m.__fmhyHooked) return;
            m.__fmhyHooked = true;
            m.__fmhyFixes = 0;
            m.addEventListener('ratechange', function() {
                // A site may reset the speed; fight back at most 3 times per element.
                if (rate !== null && Math.abs(m.playbackRate - rate) > 0.001 && m.__fmhyFixes < 3) {
                    m.__fmhyFixes++;
                    setTimeout(function() { applyRate(m); }, 0);
                }
            });
            if (!reported) { reported = true; try { if (B.hasMedia) B.hasMedia(true); } catch (_) {} }
            pullRate();
            applyRate(m);
        }
        function scan() {
            var list = document.querySelectorAll('video,audio');
            for (var i = 0; i < list.length; i++) hook(list[i]);
        }
        V.setRate = function(r) {
            rate = (r && r > 0) ? r : null;
            var list = document.querySelectorAll('video,audio');
            for (var i = 0; i < list.length; i++) { list[i].__fmhyFixes = 0; applyRate(list[i]); }
        };
        // Autoplay: in "muted" mode, media that starts unmuted without a recent gesture is paused.
        var autoplayGuard = false, lastGesture = -100000;
        function gesture() { lastGesture = Date.now(); }
        ['pointerdown', 'touchstart', 'keydown', 'click'].forEach(function(evt) {
            window.addEventListener(evt, gesture, true);
        });
        V.setAutoplay = function(mode) { autoplayGuard = (mode === 'muted'); };
        try { if (B.autoplayMode) V.setAutoplay(B.autoplayMode()); } catch (_) {}
        function userStarted() {
            try { if (navigator.userActivation && navigator.userActivation.isActive) return true; } catch (_) {}
            return Date.now() - lastGesture <= 1000;
        }
        document.addEventListener('play', function(e) {
            var m = e.target;
            if (autoplayGuard && isMedia(m) && !m.muted && m.volume > 0 && !userStarted()) {
                try { m.pause(); } catch (_) {}
            }
        }, true);
        var scanTimer = null;
        function scheduleScan() {
            scheduleSources();
            if (scanTimer) return;
            scanTimer = setTimeout(function() { scanTimer = null; scan(); }, 150);
        }

        var lastSourcesJson = null, sourcesTimer = null;
        // The script runs in every frame; each frame reports its own list, keyed by its address ('' = top page).
        var frameKey = '';
        try { if (window.top !== window) frameKey = String(location.href); } catch (_) { frameKey = String(location.href); }
        function collectSources() {
            var res = [];
            var videos = document.querySelectorAll('video');
            for (var i = 0; i < videos.length; i++) {
                var v = videos[i];
                var vSrc = v.currentSrc || v.src;
                if (vSrc) {
                    res.push({
                        src: vSrc,
                        type: '',
                        w: v.videoWidth || 0,
                        h: v.videoHeight || 0
                    });
                }
                var sources = v.querySelectorAll('source');
                for (var j = 0; j < sources.length; j++) {
                    var s = sources[j];
                    var sSrc = s.src || s.getAttribute('src');
                    if (sSrc) {
                        var qAttrs = ['label', 'res', 'size', 'data-quality', 'data-res'];
                        var q = null;
                        for (var k = 0; k < qAttrs.length; k++) {
                            var val = s.getAttribute(qAttrs[k]);
                            if (val && val.trim().length > 0) {
                                q = val.trim();
                                break;
                            }
                        }
                        var item = {
                            src: sSrc,
                            type: s.getAttribute('type') || '',
                            w: v.videoWidth || 0,
                            h: v.videoHeight || 0
                        };
                        if (q) item.q = q;
                        res.push(item);
                    }
                }
            }
            return res;
        }

        function reportSources() {
            try {
                if (!B.videoSources) return;
                var list = collectSources();
                var json = JSON.stringify(list);
                if (json !== lastSourcesJson) {
                    lastSourcesJson = json;
                    B.videoSources(json, frameKey);
                }
            } catch (_) {}
        }

        function scheduleSources() {
            if (sourcesTimer) return;
            sourcesTimer = setTimeout(function() {
                sourcesTimer = null;
                reportSources();
            }, 300);
        }

        function handleSpaNavigation() {
            lastSourcesJson = null;
            scheduleSources();
        }
        try {
            var origPush = history.pushState;
            if (origPush) {
                history.pushState = function() {
                    var ret = origPush.apply(this, arguments);
                    handleSpaNavigation();
                    return ret;
                };
            }
            var origReplace = history.replaceState;
            if (origReplace) {
                history.replaceState = function() {
                    var ret = origReplace.apply(this, arguments);
                    handleSpaNavigation();
                    return ret;
                };
            }
            window.addEventListener('popstate', handleSpaNavigation);
        } catch (_) {}

        try { new MutationObserver(scheduleScan).observe(document, { childList: true, subtree: true }); } catch (_) {}
        document.addEventListener('DOMContentLoaded', function() { scan(); scheduleSources(); });
        ['loadedmetadata', 'play', 'resize'].forEach(function(evt) {
            document.addEventListener(evt, function(e) {
                if (isMedia(e.target)) {
                    hook(e.target);
                    reportSize(e.target);
                    scheduleRect();
                    if (evt === 'loadedmetadata' || evt === 'play') scheduleSources();
                }
            }, true);
        });
        scheduleSources();
    } catch (_) {}
})();
"""
}
