package io.github.omeg4dev.fmhy.browser.adblock

object SelectorPicker {

    fun computeRule(host: String, selector: String): String {
        val cleanHost = host.trim().lowercase().removePrefix("www.")
        val cleanSelector = selector.trim()
        return "$cleanHost##$cleanSelector"
    }

    fun filterClasses(classes: List<String>): List<String> {
        val digitsOnly = Regex("""^\d+$""")
        return classes
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.matches(digitsOnly) && it.length <= 20 }
            .take(3)
    }

    fun buildPickerJs(bridgeName: String): String {
        return """
            (function() {
                if (window.__fmhy_picker_active) return;
                window.__fmhy_picker_active = true;

                let hoveredEl = null;
                const highlight = document.createElement('div');
                highlight.id = '__fmhy_picker_highlight';
                highlight.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483646;border:2px solid #ff3366;background:rgba(255,51,102,0.2);transition:all 0.05s ease;display:none;';
                document.documentElement.appendChild(highlight);

                const bar = document.createElement('div');
                bar.id = '__fmhy_picker_bar';
                bar.style.cssText = 'position:fixed;bottom:24px;left:50%;transform:translateX(-50%);z-index:2147483647;background:#202020;color:#fff;padding:10px 18px;border-radius:24px;box-shadow:0 4px 16px rgba(0,0,0,0.5);font-family:system-ui,sans-serif;font-size:14px;display:flex;align-items:center;gap:16px;user-select:none;';
                bar.innerHTML = '<span>Tap element to hide</span><button id="__fmhy_picker_cancel" style="background:#444;color:#fff;border:none;padding:6px 14px;border-radius:14px;font-size:13px;font-weight:bold;cursor:pointer;">Cancel</button>';
                document.documentElement.appendChild(bar);

                function cleanup() {
                    window.__fmhy_picker_active = false;
                    highlight.remove();
                    bar.remove();
                    document.removeEventListener('touchstart', onTouchMove, true);
                    document.removeEventListener('touchmove', onTouchMove, true);
                    document.removeEventListener('mousemove', onMouseMove, true);
                    document.removeEventListener('click', onClick, true);
                }

                document.getElementById('__fmhy_picker_cancel').addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    cleanup();
                    if (window['$bridgeName'] && window['$bridgeName'].pickerCancel) {
                        window['$bridgeName'].pickerCancel();
                    }
                }, true);

                function updateHighlight(el) {
                    if (!el || el === bar || bar.contains(el) || el === highlight || el === document.body || el === document.documentElement) {
                        highlight.style.display = 'none';
                        hoveredEl = null;
                        return;
                    }
                    hoveredEl = el;
                    const rect = el.getBoundingClientRect();
                    highlight.style.display = 'block';
                    highlight.style.top = rect.top + 'px';
                    highlight.style.left = rect.left + 'px';
                    highlight.style.width = rect.width + 'px';
                    highlight.style.height = rect.height + 'px';
                }

                function onTouchMove(e) {
                    if (!e.touches || e.touches.length === 0) return;
                    const touch = e.touches[0];
                    const el = document.elementFromPoint(touch.clientX, touch.clientY);
                    updateHighlight(el);
                }

                function onMouseMove(e) {
                    const el = document.elementFromPoint(e.clientX, e.clientY);
                    updateHighlight(el);
                }

                function computeSelector(el) {
                    if (!el || el === document.body || el === document.documentElement) return '';
                    if (el.id) {
                        try {
                            if (document.querySelectorAll('#' + CSS.escape(el.id)).length === 1) {
                                return '#' + CSS.escape(el.id);
                            }
                        } catch (_) {}
                    }
                    const tag = el.tagName.toLowerCase();
                    const validClasses = [];
                    if (el.classList) {
                        for (let i = 0; i < el.classList.length; i++) {
                            const c = el.classList.item(i);
                            if (!c) continue;
                            if (/^\d+$/.test(c)) continue;
                            if (c.length > 20) continue;
                            validClasses.push(CSS.escape(c));
                            if (validClasses.length >= 3) break;
                        }
                    }
                    if (validClasses.length > 0) {
                        return tag + '.' + validClasses.join('.');
                    }
                    const parts = [];
                    let curr = el;
                    let depth = 0;
                    while (curr && curr !== document.body && curr !== document.documentElement && depth < 4) {
                        const curTag = curr.tagName.toLowerCase();
                        let index = 1;
                        let sib = curr.previousElementSibling;
                        while (sib) {
                            if (sib.tagName.toLowerCase() === curTag) index++;
                            sib = sib.previousElementSibling;
                        }
                        parts.unshift(curTag + ':nth-of-type(' + index + ')');
                        curr = curr.parentElement;
                        depth++;
                    }
                    return parts.join(' > ');
                }

                function onClick(e) {
                    if (e.target === bar || bar.contains(e.target)) return;
                    e.preventDefault();
                    e.stopPropagation();
                    const el = hoveredEl || document.elementFromPoint(e.clientX, e.clientY);
                    const selector = computeSelector(el);
                    cleanup();
                    if (selector && window['$bridgeName'] && window['$bridgeName'].pickerResult) {
                        window['$bridgeName'].pickerResult(selector);
                    }
                }

                document.addEventListener('touchstart', onTouchMove, true);
                document.addEventListener('touchmove', onTouchMove, true);
                document.addEventListener('mousemove', onMouseMove, true);
                document.addEventListener('click', onClick, true);
            })();
        """.trimIndent()
    }
}
