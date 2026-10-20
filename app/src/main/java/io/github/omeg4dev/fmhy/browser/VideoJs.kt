package io.github.omeg4dev.fmhy.browser

/** Page script that watches <video>/<audio> elements and reports what the app needs to know. */
object VideoJs {
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
        ['loadedmetadata', 'play', 'resize'].forEach(function(evt) {
            document.addEventListener(evt, function(e) {
                if (isMedia(e.target)) reportSize(e.target);
            }, true);
        });
    } catch (_) {}
})();
"""
}
