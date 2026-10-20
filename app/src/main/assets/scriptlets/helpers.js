// Ported from uBlock Origin (GPL-3.0), src/js/resources/helpers.js

var scriptletGlobals = globalThis.scriptletGlobals || {};

function safeSelf() {
    if (safeSelf.safe) {
        return safeSelf.safe;
    }
    const self = globalThis;
    const safe = {
        'Array_from': Array.from,
        'Error': self.Error,
        'Function_toString': Function.prototype.call.bind(self.Function.prototype.toString),
        'Math_floor': Math.floor,
        'Math_max': Math.max,
        'Math_min': Math.min,
        'Math_random': Math.random,
        'Object': Object,
        'Object_defineProperty': Object.defineProperty.bind(Object),
        'Object_defineProperties': Object.defineProperties.bind(Object),
        'Object_fromEntries': Object.fromEntries.bind(Object),
        'Object_getOwnPropertyDescriptor': Object.getOwnPropertyDescriptor.bind(Object),
        'Object_hasOwn': Object.hasOwn ? Object.hasOwn.bind(Object) : (obj, prop) => Object.prototype.hasOwnProperty.call(obj, prop),
        'Object_toString': Object.prototype.toString,
        'RegExp': self.RegExp,
        'RegExp_test': Function.prototype.call.bind(self.RegExp.prototype.test),
        'RegExp_exec': self.RegExp.prototype.exec,
        'Request_clone': self.Request ? self.Request.prototype.clone : null,
        'String': self.String,
        'String_fromCharCode': String.fromCharCode,
        'String_split': String.prototype.split,
        'XMLHttpRequest': self.XMLHttpRequest,
        'addEventListener': self.EventTarget ? self.EventTarget.prototype.addEventListener : null,
        'removeEventListener': self.EventTarget ? self.EventTarget.prototype.removeEventListener : null,
        'fetch': self.fetch,
        'JSON': self.JSON,
        'JSON_parse': Function.prototype.call.bind(self.JSON.parse, self.JSON),
        'JSON_stringify': Function.prototype.call.bind(self.JSON.stringify, self.JSON),
        'log': console.log.bind(console),
        logLevel: 0,
        makeLogPrefix(...args) {
            return this.sendToLogger ? `[${args.join(' \u205D ')}]` : '';
        },
        uboLog(...args) {
            if (this.sendToLogger === undefined) { return; }
            if (args === undefined || args[0] === '') { return; }
            return this.sendToLogger('info', ...args);
        },
        uboErr(...args) {
            if (this.sendToLogger === undefined) { return; }
            if (args === undefined || args[0] === '') { return; }
            return this.sendToLogger('error', ...args);
        },
        escapeRegexChars(s) {
            return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        },
        initPattern(pattern, options = {}) {
            if (pattern === '') {
                return { matchAll: true, expect: true };
            }
            const expect = (options.canNegate !== true || pattern.startsWith('!') === false);
            if (expect === false) {
                pattern = pattern.slice(1);
            }
            const match = /^\/(.+)\/([gimsu]*)$/.exec(pattern);
            if (match !== null) {
                return {
                    re: new this.RegExp(
                        match[1],
                        match[2] || options.flags
                    ),
                    expect,
                };
            }
            if (options.flags !== undefined) {
                return {
                    re: new this.RegExp(this.escapeRegexChars(pattern),
                        options.flags
                    ),
                    expect,
                };
            }
            return { pattern, expect };
        },
        testPattern(details, haystack) {
            if (details.matchAll) { return true; }
            if (details.re) {
                return this.RegExp_test(details.re, haystack) === details.expect;
            }
            return haystack.includes(details.pattern) === details.expect;
        },
        patternToRegex(pattern, flags = undefined, verbatim = false) {
            if (pattern === '') { return /^/; }
            const match = /^\/(.+)\/([gimsu]*)$/.exec(pattern);
            if (match === null) {
                const reStr = this.escapeRegexChars(pattern);
                return new RegExp(verbatim ? `^${reStr}$` : reStr, flags);
            }
            try {
                return new RegExp(match[1], match[2] || undefined);
            } catch {
            }
            return /^/;
        },
        parseVarargs(varargs) {
            const entries = varargs.reduce((out, v, i, a) => {
                if (i & 1) { return out; }
                const rawValue = a[i+1];
                const value = /^\d+$/.test(rawValue)
                    ? parseInt(rawValue, 10)
                    : rawValue;
                out.push([ a[i], value ]);
                return out;
            }, []);
            return this.Object_fromEntries(entries);
        },
    };
    safeSelf.safe = safe;
    safe.sendToLogger = (type, ...args) => {
        safe.log(`[FMHY] ${type}:`, ...args);
    };
    return safe;
}

function runAt(fn, when) {
    const intFromReadyState = state => {
        const targets = {
            'loading': 1, 'asap': 1,
            'interactive': 2, 'end': 2, '2': 2,
            'complete': 3, 'idle': 3, '3': 3,
        };
        const tokens = Array.isArray(state) ? state : [ state ];
        for (const token of tokens) {
            const prop = `${token}`;
            if (Object.prototype.hasOwnProperty.call(targets, prop) === false) { continue; }
            return targets[prop];
        }
        return 0;
    };
    const targetState = intFromReadyState(when);
    if (intFromReadyState(document.readyState) >= targetState) {
        fn(); return;
    }
    const safe = safeSelf();
    const onStateChange = () => {
        if (intFromReadyState(document.readyState) < targetState) { return; }
        fn();
        document.removeEventListener('readystatechange', onStateChange, { capture: true });
    };
    document.addEventListener('readystatechange', onStateChange, { capture: true });
}

function runAtHtmlElementFn(fn) {
    if (document.documentElement) {
        fn();
        return;
    }
    const observer = new MutationObserver(() => {
        observer.disconnect();
        fn();
    });
    observer.observe(document, { childList: true });
}

function proxyApplyFn(target = '', handler = '', options = {}) {
    let context = globalThis;
    let prop = target;
    for (;;) {
        const pos = prop.indexOf('.');
        if (pos === -1) { break; }
        context = context[prop.slice(0, pos)];
        if (context instanceof Object === false) { return; }
        prop = prop.slice(pos+1);
    }
    const fn = context[prop];
    if (typeof fn !== 'function') { return; }
    if (proxyApplyFn.CtorContext === undefined) {
        proxyApplyFn.ctorContexts = [];
        proxyApplyFn.CtorContext = class {
            constructor(...args) { this.init(...args); }
            init(callFn, callArgs) {
                this.callFn = callFn;
                this.callArgs = callArgs;
                return this;
            }
            reflect() {
                const r = Reflect.construct(this.callFn, this.callArgs);
                this.callFn = this.callArgs = this.private = undefined;
                proxyApplyFn.ctorContexts.push(this);
                return r;
            }
            static factory(...args) {
                return proxyApplyFn.ctorContexts.length !== 0
                    ? proxyApplyFn.ctorContexts.pop().init(...args)
                    : new proxyApplyFn.CtorContext(...args);
            }
        };
        proxyApplyFn.applyContexts = [];
        proxyApplyFn.ApplyContext = class {
            constructor(...args) { this.init(...args); }
            init(callFn, thisArg, callArgs) {
                this.callFn = callFn;
                this.thisArg = thisArg;
                this.callArgs = callArgs;
                return this;
            }
            reflect() {
                const r = Reflect.apply(this.callFn, this.thisArg, this.callArgs);
                this.callFn = this.thisArg = this.callArgs = this.private = undefined;
                proxyApplyFn.applyContexts.push(this);
                return r;
            }
            static factory(...args) {
                return proxyApplyFn.applyContexts.length !== 0
                    ? proxyApplyFn.applyContexts.pop().init(...args)
                    : new proxyApplyFn.ApplyContext(...args);
            }
        };
        proxyApplyFn.isCtor = new Map();
        proxyApplyFn.proxies = new WeakMap();
    }
    if (proxyApplyFn.isCtor.has(target) === false) {
        proxyApplyFn.isCtor.set(target, fn.prototype?.constructor === fn);
    }
    const proxyDetails = {
        apply(target, thisArg, args) {
            return handler(proxyApplyFn.ApplyContext.factory(target, thisArg, args));
        }
    };
    if (proxyApplyFn.isCtor.get(target)) {
        proxyDetails.construct = function(target, args) {
            return handler(proxyApplyFn.CtorContext.factory(target, args));
        };
    }
    const proxiedTarget = new Proxy(fn, proxyDetails);
    proxyApplyFn.proxies.set(proxiedTarget, fn);
    context[prop] = proxiedTarget;
}

class RangeParser {
    constructor(s) {
        this.not = s.charAt(0) === '!';
        if (this.not) { s = s.slice(1); }
        if (s === '') { return; }
        const pos = s.indexOf('-');
        if (pos !== 0) {
            this.min = this.max = parseInt(s, 10) || 0;
        }
        if (pos !== -1) {
            this.max = parseInt(s.slice(pos + 1), 10) || Number.MAX_SAFE_INTEGER;
        }
    }
    unbound() {
        return this.min === undefined && this.max === undefined;
    }
    test(v) {
        const n = Math.min(Math.max(Number(v) || 0, 0), Number.MAX_SAFE_INTEGER);
        if (this.min === this.max) {
            return (this.min === undefined || n === this.min) !== this.not;
        }
        if (this.min === undefined) {
            return (n <= this.max) !== this.not;
        }
        if (this.max === undefined) {
            return (n >= this.min) !== this.not;
        }
        return (n >= this.min && n <= this.max) !== this.not;
    }
}

function getRandomTokenFn() {
    const safe = safeSelf();
    return safe.String_fromCharCode(Date.now() % 26 + 97) +
        safe.Math_floor(safe.Math_random() * 982451653 + 982451653).toString(36);
}

function getExceptionTokenFn() {
    const token = getRandomTokenFn();
    const oe = self.onerror;
    self.onerror = function(msg, ...args) {
        if (typeof msg === 'string' && msg.includes(token)) { return true; }
        if (oe instanceof Function) {
            return oe.call(this, msg, ...args);
        }
    }.bind();
    return token;
}

function trapPropertyFn(propChain, handler, options = {}) {
    if (propChain === '') { return; }
    let owner = self;
    let prop = propChain;
    for (;;) {
        const pos = prop.indexOf('.');
        if (pos === -1) { break; }
        owner = owner[prop.slice(0, pos)];
        if (owner instanceof Object === false) { return; }
        prop = prop.slice(pos + 1);
    }
    const safe = safeSelf();
    if (trapPropertyFn.db === undefined) {
        trapPropertyFn.db = new WeakMap();
        trapPropertyFn.entryFromContext = (owner, prop) => {
            const handlers = trapPropertyFn.db.get(owner);
            return handlers?.get(prop);
        };
        trapPropertyFn.getter = (owner, prop) => {
            const entry = trapPropertyFn.entryFromContext(owner, prop);
            if (entry === undefined) { return; }
            let r = entry.value;
            for (const desc of entry.stack) {
                try { r = desc.get(); } catch (e) {
                    if (entry.canThrow) { throw e; }
                }
            }
            return r;
        };
        trapPropertyFn.setter = (owner, prop, value) => {
            const entry = trapPropertyFn.entryFromContext(owner, prop);
            if (entry === undefined) { return; }
            entry.value = value;
            for (const desc of entry.stack) {
                try { desc.set(value); } catch (e) {
                    if (entry.canThrow) { throw e; }
                }
            }
        };
    }
    const { db } = trapPropertyFn;
    const handlers = db.get(owner) || new Map();
    if (handlers.size === 0) {
        db.set(owner, handlers);
    }
    const entry = handlers.get(prop) || {
        value: owner[prop],
        stack: [],
    };
    entry.stack.push(handler);
    if (entry.stack.length > 1) { return entry.value; }
    Object.assign(entry, options);
    handlers.set(prop, entry);
    const desc = safe.Object_getOwnPropertyDescriptor(owner, prop);
    if (desc instanceof safe.Object) {
        if (desc.get || desc.set) {
            entry.stack.push(desc);
        }
    }
    try {
        safe.Object_defineProperty(owner, prop, {
            get() {
                return trapPropertyFn.getter(owner, prop);
            },
            set(value) {
                trapPropertyFn.setter(owner, prop, value);
            }
        });
    } catch {
    }
    return entry.value;
}

function collateFetchArgumentsFn(resource, options) {
    const safe = safeSelf();
    const props = [
        'body', 'cache', 'credentials', 'duplex', 'headers',
        'integrity', 'keepalive', 'method', 'mode', 'priority',
        'redirect', 'referrer', 'referrerPolicy', 'url'
    ];
    const out = {};
    if (collateFetchArgumentsFn.collateKnownProps === undefined) {
        collateFetchArgumentsFn.collateKnownProps = (src, out) => {
            for (const prop of props) {
                if (src[prop] === undefined) { continue; }
                out[prop] = src[prop];
            }
        };
    }
    if (
        typeof resource !== 'object' ||
        safe.Object_toString.call(resource) !== '[object Request]'
    ) {
        out.url = `${resource}`;
    } else {
        let clone;
        try {
            clone = safe.Request_clone ? safe.Request_clone.call(resource) : null;
        } catch {
        }
        collateFetchArgumentsFn.collateKnownProps(clone || resource, out);
    }
    if (typeof options === 'object' && options !== null) {
        collateFetchArgumentsFn.collateKnownProps(options, out);
    }
    return out;
}

function parsePropertiesToMatchFn(propsToMatch, implicit = '') {
    const safe = safeSelf();
    const needles = new Map();
    if (propsToMatch === undefined || propsToMatch === '') { return needles; }
    const options = { canNegate: true };
    for (const needle of safe.String_split.call(propsToMatch, /\s+/)) {
        let [ prop, pattern ] = safe.String_split.call(needle, ':');
        if (prop === '') { continue; }
        if (pattern !== undefined && /[^$\w -]/.test(prop)) {
            prop = `${prop}:${pattern}`;
            pattern = undefined;
        }
        if (pattern !== undefined) {
            needles.set(prop, safe.initPattern(pattern, options));
        } else if (implicit !== '') {
            needles.set(implicit, safe.initPattern(prop, options));
        }
    }
    return needles;
}

function matchObjectPropertiesFn(propNeedles, ...objs) {
    const safe = safeSelf();
    const matched = [];
    for (const obj of objs) {
        if (obj instanceof Object === false) { continue; }
        for (const [ prop, details ] of propNeedles) {
            let value = obj[prop];
            if (value === undefined) { continue; }
            if (typeof value !== 'string') {
                try { value = safe.JSON_stringify(value); } catch { }
                if (typeof value !== 'string') { continue; }
            }
            if (safe.testPattern(details, value) === false) { return; }
            matched.push(`${prop}: ${value}`);
        }
    }
    return matched;
}

function generateContentFn(trusted, directive) {
    const safe = safeSelf();
    const randomize = len => {
        const chunks = [];
        let textSize = 0;
        do {
            const s = safe.Math_random().toString(36).slice(2);
            chunks.push(s);
            textSize += s.length;
        } while (textSize < len);
        return chunks.join(' ').slice(0, len);
    };
    if (directive === 'true') {
        return randomize(10);
    }
    if (directive === 'emptyObj') {
        return '{}';
    }
    if (directive === 'emptyArr') {
        return '[]';
    }
    if (directive === 'emptyStr') {
        return '';
    }
    if (directive.startsWith('length:')) {
        const match = /^length:(\d+)(?:-(\d+))?$/.exec(directive);
        if (match === null) { return ''; }
        const min = parseInt(match[1], 10);
        const extent = safe.Math_max(parseInt(match[2], 10) || 0, min) - min;
        const len = safe.Math_min(min + extent * safe.Math_random(), 500000);
        return randomize(len | 0);
    }
    if (trusted) {
        return directive;
    }
    return '';
}

function onIdleFn(fn, options) {
    if (self.requestIdleCallback) {
        return self.requestIdleCallback(fn, options);
    }
    return self.requestAnimationFrame(fn);
}

function offIdleFn(id) {
    if (self.cancelIdleCallback) {
        return self.cancelIdleCallback(id);
    }
    return self.cancelAnimationFrame(id);
}

function matchesStackTraceFn(needleDetails, logLevel = '') {
    const safe = safeSelf();
    const exceptionToken = getExceptionTokenFn();
    const error = new safe.Error(exceptionToken);
    const docURL = new URL(self.location.href);
    docURL.hash = '';
    const reLine = /(.*?@)?(\S+)(:\d+):\d+\)?$/;
    const lines = [];
    for (let line of safe.String_split.call(error.stack || '', /[\n\r]+/)) {
        if (line.includes(exceptionToken)) { continue; }
        line = line.trim();
        const match = safe.RegExp_exec.call(reLine, line);
        if (match === null) { continue; }
        let url = match[2];
        if (url.startsWith('(')) { url = url.slice(1); }
        if (url === docURL.href) {
            url = 'inlineScript';
        } else if (url.startsWith('<anonymous>')) {
            url = 'injectedScript';
        }
        let fn = match[1] !== undefined
            ? match[1].slice(0, -1)
            : line.slice(0, match.index).trim();
        if (fn.startsWith('at')) { fn = fn.slice(2).trim(); }
        let rowcol = match[3];
        lines.push(' ' + `${fn} ${url}${rowcol}:1`.trim());
    }
    lines[0] = `stackDepth:${lines.length-1}`;
    const stack = lines.join('\t');
    const r = needleDetails.matchAll !== true &&
        safe.testPattern(needleDetails, stack);
    if (
        logLevel === 'all' ||
        logLevel === 'match' && r ||
        logLevel === 'nomatch' && !r
    ) {
        safe.uboLog(stack.replace(/\t/g, '\n'));
    }
    return r;
}

function objectFindOwnerFn(root, path, prune = false) {
    const safe = safeSelf();
    let owner = root;
    let chain = path;
    for (;;) {
        if (typeof owner !== 'object' || owner === null) { return false; }
        const pos = chain.indexOf('.');
        if (pos === -1) {
            if (prune === false) {
                return safe.Object_hasOwn(owner, chain);
            }
            let modified = false;
            if (chain === '*') {
                for (const key in owner) {
                    if (safe.Object_hasOwn(owner, key) === false) { continue; }
                    delete owner[key];
                    modified = true;
                }
            } else if (safe.Object_hasOwn(owner, chain)) {
                delete owner[chain];
                modified = true;
            }
            return modified;
        }
        const prop = chain.slice(0, pos);
        const next = chain.slice(pos + 1);
        let found = false;
        if (prop === '[-]' && Array.isArray(owner)) {
            let i = owner.length;
            while (i--) {
                if (objectFindOwnerFn(owner[i], next) === false) { continue; }
                owner.splice(i, 1);
                found = true;
            }
            return found;
        }
        if (prop === '{-}' && owner instanceof Object) {
            for (const key of Object.keys(owner)) {
                if (objectFindOwnerFn(owner[key], next) === false) { continue; }
                delete owner[key];
                found = true;
            }
            return found;
        }
        if (
            prop === '[]' && Array.isArray(owner) ||
            prop === '{}' && owner instanceof Object ||
            prop === '*' && owner instanceof Object
        ) {
            for (const key of Object.keys(owner)) {
                if (objectFindOwnerFn(owner[key], next, prune) === false) { continue; }
                found = true;
            }
            return found;
        }
        if (safe.Object_hasOwn(owner, prop) === false) { return false; }
        owner = owner[prop];
        chain = chain.slice(pos + 1);
    }
}

function objectPruneFn(
    obj,
    rawPrunePaths,
    rawNeedlePaths,
    stackNeedleDetails = { matchAll: true },
    extraArgs = {}
) {
    if (typeof rawPrunePaths !== 'string') { return; }
    const safe = safeSelf();
    const prunePaths = rawPrunePaths !== ''
        ? safe.String_split.call(rawPrunePaths, / +/)
        : [];
    const needlePaths = prunePaths.length !== 0 && rawNeedlePaths !== ''
        ? safe.String_split.call(rawNeedlePaths, / +/)
        : [];
    if (stackNeedleDetails.matchAll !== true) {
        if (matchesStackTraceFn(stackNeedleDetails, extraArgs.logstack) === false) {
            return;
        }
    }
    if (objectPruneFn.mustProcess === undefined) {
        objectPruneFn.mustProcess = (root, needlePaths) => {
            for (const needlePath of needlePaths) {
                if (objectFindOwnerFn(root, needlePath) === false) {
                    return false;
                }
            }
            return true;
        };
    }
    if (prunePaths.length === 0) { return; }
    let outcome = 'nomatch';
    if (objectPruneFn.mustProcess(obj, needlePaths)) {
        for (const path of prunePaths) {
            if (objectFindOwnerFn(obj, path, true)) {
                outcome = 'match';
            }
        }
    }
    if (outcome === 'match') { return obj; }
}

function replaceNodeTextFn(
    nodeName = '',
    pattern = '',
    replacement = '',
    ...varargs
) {
    const safe = safeSelf();
    const logPrefix = safe.makeLogPrefix('replace-node-text.fn', ...Array.from(arguments));
    const reNodeName = safe.patternToRegex(nodeName, 'i', true);
    const rePattern = safe.patternToRegex(pattern, 'gms');
    const extraArgs = safe.parseVarargs(varargs);
    const reIncludes = extraArgs.includes || extraArgs.condition
        ? safe.patternToRegex(extraArgs.includes || extraArgs.condition, 'ms')
        : null;
    const reExcludes = extraArgs.excludes
        ? safe.patternToRegex(extraArgs.excludes, 'ms')
        : null;
    const textContentFactory = (() => {
        const out = { createScript: s => s };
        const { trustedTypes: tt } = self;
        if (tt instanceof Object) {
            if (typeof tt.getPropertyType === 'function') {
                if (tt.getPropertyType('script', 'textContent') === 'TrustedScript') {
                    return tt.createPolicy(getRandomTokenFn(), out);
                }
            }
        }
        return out;
    })();
    let sedCount = extraArgs.sedCount ?? Number.MAX_SAFE_INTEGER;
    const handleNode = node => {
        const before = node.textContent;
        if (reIncludes) {
            reIncludes.lastIndex = 0;
            if (safe.RegExp_test(reIncludes, before) === false) { return; }
        }
        if (reExcludes) {
            reExcludes.lastIndex = 0;
            if (safe.RegExp_test(reExcludes, before)) { return; }
        }
        rePattern.lastIndex = 0;
        if (safe.RegExp_test(rePattern, before) === false) { return; }
        rePattern.lastIndex = 0;
        const after = pattern !== ''
            ? before.replace(rePattern, replacement)
            : replacement;
        node.textContent = node.nodeName === 'SCRIPT'
            ? textContentFactory.createScript(after)
            : after;
        if (safe.logLevel > 1) {
            safe.uboLog(logPrefix, `Text before:\n${before.trim()}`);
        }
        safe.uboLog(logPrefix, `Text after:\n${after.trim()}`);
        sedCount -= 1;
    };
    const handleTree = root => {
        const treeWalker = document.createTreeWalker(root,
            NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT
        );
        const { currentScript } = document;
        let count = 0;
        for (;;) {
            const node = treeWalker.nextNode();
            if (node === null) { break; }
            count += 1;
            if (node === currentScript) { continue; }
            if (reNodeName.test(node.nodeName)) {
                handleNode(node);
            } else if (node.nodeName === 'TEMPLATE') {
                count += handleTree(node.content);
            } else {
                continue;
            }
            if (sedCount === 0) { break; }
        }
        return count;
    };
    runAt(() => {
        handleTree(document);
    }, extraArgs.runAt);
}

function validateConstantFn(trusted, raw, extraArgs = {}) {
    const safe = safeSelf();
    let value;
    if (raw === 'undefined') {
        value = undefined;
    } else if (raw === 'false') {
        value = false;
    } else if (raw === 'true') {
        value = true;
    } else if (raw === 'null') {
        value = null;
    } else if (raw === "''" || raw === '') {
        value = '';
    } else if (raw === '[]' || raw === 'emptyArr') {
        value = [];
    } else if (raw === '{}' || raw === 'emptyObj') {
        value = {};
    } else if (raw === 'noopFunc') {
        value = function(){};
    } else if (raw === 'trueFunc') {
        value = function(){ return true; };
    } else if (raw === 'falseFunc') {
        value = function(){ return false; };
    } else if (raw === 'throwFunc') {
        value = function(){ throw ''; };
    } else if (/^-?\d+$/.test(raw)) {
        value = parseInt(raw);
        if (isNaN(raw)) { return; }
        if (Math.abs(raw) > 0x7FFF) { return; }
    } else if (trusted) {
        if (raw.startsWith('json:')) {
            try { value = safe.JSON_parse(raw.slice(5)); } catch { return; }
        } else if (raw.startsWith('{') && raw.endsWith('}')) {
            try { value = safe.JSON_parse(raw).value; } catch { return; }
        }
    } else {
        return;
    }
    if (extraArgs.as !== undefined) {
        if (extraArgs.as === 'function') {
            return () => value;
        } else if (extraArgs.as === 'callback') {
            return () => (() => value);
        } else if (extraArgs.as === 'resolved') {
            return Promise.resolve(value);
        } else if (extraArgs.as === 'rejected') {
            return Promise.reject(value);
        }
    }
    return value;
}
