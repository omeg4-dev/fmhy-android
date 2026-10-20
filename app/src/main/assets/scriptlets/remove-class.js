// Ported from uBlock Origin (GPL-3.0), src/js/resources/scriptlets.js

function remove_class(rawToken = '', rawSelector = '', behavior = '') {
    if (typeof rawToken !== 'string') { return; }
    if (rawToken === '') { return; }
    const safe = safeSelf();
    const logPrefix = safe.makeLogPrefix('remove-class', rawToken, rawSelector, behavior);
    const tokens = safe.String_split.call(rawToken, /\s*\|\s*/);
    const selector = tokens
        .map(a => `${rawSelector}.${CSS.escape(a)}`)
        .join(',');
    if (safe.logLevel > 1) {
        safe.uboLog(logPrefix, `Target selector:\n\t${selector}`);
    }
    const mustStay = /\bstay\b/.test(behavior);
    let timer;
    const rmclass = () => {
        timer = undefined;
        try {
            const nodes = document.querySelectorAll(selector);
            for (const node of nodes) {
                node.classList.remove(...tokens);
                safe.uboLog(logPrefix, 'Removed class(es)');
            }
        } catch {
        }
        if (mustStay) { return; }
        if (document.readyState !== 'complete') { return; }
        observer.disconnect();
    };
    const mutationHandler = mutations => {
        if (timer !== undefined) { return; }
        let skip = true;
        for (let i = 0; i < mutations.length && skip; i++) {
            const { type, addedNodes, removedNodes } = mutations[i];
            if (type === 'attributes') { skip = false; }
            for (let j = 0; j < addedNodes.length && skip; j++) {
                if (addedNodes[j].nodeType === 1) { skip = false; break; }
            }
            for (let j = 0; j < removedNodes.length && skip; j++) {
                if (removedNodes[j].nodeType === 1) { skip = false; break; }
            }
        }
        if (skip) { return; }
        timer = onIdleFn(rmclass, { timeout: 67 });
    };
    const observer = new MutationObserver(mutationHandler);
    const start = () => {
        rmclass();
        observer.observe(document, {
            attributes: true,
            attributeFilter: [ 'class' ],
            childList: true,
            subtree: true,
        });
    };
    runAt(() => {
        start();
    }, /\bcomplete\b/.test(behavior) ? 'idle' : 'loading');
}
