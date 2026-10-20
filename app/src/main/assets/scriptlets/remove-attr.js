// Ported from uBlock Origin (GPL-3.0), src/js/resources/attribute.js

function remove_attr(rawToken = '', rawSelector = '', behavior = '', ...varargs) {
    if (typeof rawToken !== 'string') { return; }
    if (rawToken === '') { return; }
    const safe = safeSelf();
    const logPrefix = safe.makeLogPrefix('remove-attr',
        rawToken, rawSelector, behavior, ...varargs
    );
    const tokens = safe.String_split.call(rawToken, /\s*\|\s*/);
    const selector = tokens.map(a => {
        const b = CSS.escape(a);
        return rawSelector.includes(`[${b}]`) ? rawSelector : `${rawSelector}[${b}]`;
    }).join(',');
    const lazily = /\basap\b/.test(behavior) === false;
    const options = safe.parseVarargs(varargs);
    if (safe.logLevel > 1) {
        safe.uboLog(logPrefix, `Target selector:\n\t${selector}`);
    }
    const rmattrFromNode = node => {
        for (const attr of tokens) {
            if (node.hasAttribute(attr) === false) { continue; }
            node.removeAttribute(attr);
            safe.uboLog(logPrefix, `Removed attribute '${attr}'`);
        }
    };
    const rmattr = nodes => {
        for (const node of nodes ?? document.querySelectorAll(selector)) {
            rmattrFromNode(node);
        }
    };
    const rmAttrLazily = () => {
        if (rmAttrLazily.timer !== undefined) { return; }
        rmAttrLazily.timer = onIdleFn(() => {
            rmAttrLazily.timer = undefined;
            rmattr();
        }, { timeout: 17 });
    };
    const mutationHandler = mutations => {
        for (const { addedNodes, removedNodes } of mutations) {
            for (const node of addedNodes) {
                if (node.nodeType !== 1) { continue; }
                if (lazily) { return rmAttrLazily(); }
                if (node.matches(selector)) {
                    rmattrFromNode(node);
                }
                if (node.childElementCount) {
                    rmattr(node.querySelectorAll(selector));
                }
            }
            if (lazily) { return; }
            for (const node of removedNodes) {
                if (node.nodeType !== 1) { continue; }
                if (node.matches(selector)) {
                    rmattrFromNode(node);
                }
            }
        }
    };
    const stop = () => {
        if (start.observer) {
            start.observer.disconnect();
            start.observer = undefined;
        }
        if (rmAttrLazily.timer) {
            offIdleFn(rmAttrLazily.timer);
            rmAttrLazily.timer = undefined;
        }
        if (safe.logLevel > 1) {
            safe.uboLog(logPrefix, 'Quitting');
        }
    };
    const start = () => {
        rmattr();
        if (/\bstay\b/.test(behavior) === false) {
            if (options.quitAfter === undefined) { return; }
        }
        start.observer = new MutationObserver(mutationHandler);
        start.observer.observe(document, {
            attributes: true,
            attributeFilter: tokens,
            childList: true,
            subtree: true,
        });
        if (options.quitAfter) {
            runAt(() => {
                self.setTimeout(stop, options.quitAfter * 1000);
            }, 'load');
        }
    };
    runAt(() => { start(); }, safe.String_split.call(behavior, /\s+/));
}
