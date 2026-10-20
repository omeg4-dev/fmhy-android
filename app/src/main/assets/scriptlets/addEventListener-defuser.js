// Ported from uBlock Origin (GPL-3.0), src/js/resources/prevent-addeventlistener.js

function addEventListener_defuser(type = '', pattern = '', ...varargs) {
    const safe = safeSelf();
    const extraArgs = safe.parseVarargs(varargs);
    const logPrefix = safe.makeLogPrefix('prevent-addEventListener', type, pattern);
    const reType = safe.patternToRegex(type, undefined, true);
    const rePattern = safe.patternToRegex(pattern);
    const targetSelector = extraArgs.elements || undefined;
    const elementMatches = elem => {
        if (targetSelector === 'window') { return elem === window; }
        if (targetSelector === 'document') { return elem === document; }
        if (elem && elem.matches && elem.matches(targetSelector)) { return true; }
        const elems = Array.from(document.querySelectorAll(targetSelector));
        return elems.includes(elem);
    };
    const elementDetails = elem => {
        if (elem instanceof Window) { return 'window'; }
        if (elem instanceof Document) { return 'document'; }
        if (elem instanceof Element === false) { return '?'; }
        const parts = [];
        const id = String(elem.id);
        if (id !== '') { parts.push(`#${CSS.escape(id)}`); }
        for (let i = 0; i < elem.classList.length; i++) {
            parts.push(`.${CSS.escape(elem.classList.item(i))}`);
        }
        for (let i = 0; i < elem.attributes.length; i++) {
            const attr = elem.attributes.item(i);
            if (attr.name === 'id') { continue; }
            if (attr.name === 'class') { continue; }
            parts.push(`[${CSS.escape(attr.name)}="${attr.value}"]`);
        }
        return parts.join('');
    };
    const shouldPrevent = (thisArg, type, handler) => {
        const matchesType = safe.RegExp_test(reType, type);
        const matchesHandler = safe.RegExp_test(rePattern, handler);
        const matchesBoth = matchesType && matchesHandler;
        if (matchesBoth && targetSelector !== undefined) {
            if (elementMatches(thisArg) === false) { return false; }
        }
        return matchesBoth;
    };
    const proxyFn = function(context) {
        const { callArgs, thisArg } = context;
        let t, h;
        try {
            t = String(callArgs[0]);
            if (typeof callArgs[1] === 'function') {
                h = String(safe.Function_toString(callArgs[1]));
            } else if (typeof callArgs[1] === 'object' && callArgs[1] !== null) {
                if (typeof callArgs[1].handleEvent === 'function') {
                    h = String(safe.Function_toString(callArgs[1].handleEvent));
                }
            } else {
                h = String(callArgs[1]);
            }
        } catch {
        }
        if (type === '' && pattern === '') {
            safe.uboLog(logPrefix, `Called: ${t}\n${h}\n${elementDetails(thisArg)}`);
        } else if (shouldPrevent(thisArg, t, h)) {
            return safe.uboLog(logPrefix, `Prevented: ${t}\n${h}\n${elementDetails(thisArg)}`);
        }
        return context.reflect();
    };
    const protect = owner => {
        const { addEventListener } = owner;
        Object.defineProperty(owner, 'addEventListener', {
            set() { },
            get() { return addEventListener; }
        });
    };
    runAt(() => {
        proxyApplyFn('EventTarget.prototype.addEventListener', proxyFn);
        if (extraArgs.protect) { protect(EventTarget.prototype); }
        if (Object.prototype.hasOwnProperty.call(document, 'addEventListener')) {
            proxyApplyFn('document.addEventListener', proxyFn);
            if (extraArgs.protect) { protect(document); }
        }
        if (Object.prototype.hasOwnProperty.call(window, 'addEventListener')) {
            proxyApplyFn('window.addEventListener', proxyFn);
            if (extraArgs.protect) { protect(window); }
        }
    }, extraArgs.runAt);
}
