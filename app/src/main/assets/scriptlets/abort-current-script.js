// Ported from uBlock Origin (GPL-3.0), src/js/resources/abort-current-script.js

function abort_current_script(target = '', needle = '', context = '') {
    runAtHtmlElementFn(() => {
        if (typeof target !== 'string' || target === '') { return; }
        const safe = safeSelf();
        const logPrefix = safe.makeLogPrefix('abort-current-script', target, needle, context);
        const reNeedle = safe.patternToRegex(needle);
        const reContext = safe.patternToRegex(context);
        const thisScript = document.currentScript;
        const exceptionToken = getExceptionTokenFn();
        const scriptTexts = new WeakMap();
        const textContentGetter = Object.getOwnPropertyDescriptor(Node.prototype, 'textContent').get;
        const getScriptText = elem => {
            let text = textContentGetter.call(elem);
            if (text.trim() !== '') { return text; }
            if (scriptTexts.has(elem)) { return scriptTexts.get(elem); }
            const [ , mime, content ] = /^data:([^,]*),(.+)$/.exec(elem.src.trim()) || [ '', '', '' ];
            try {
                switch (true) {
                case mime.endsWith(';base64'):
                    text = self.atob(content);
                    break;
                default:
                    text = self.decodeURIComponent(content);
                    break;
                }
            } catch {
            }
            scriptTexts.set(elem, text);
            return text;
        };
        const validate = () => {
            const e = document.currentScript;
            if (e instanceof HTMLScriptElement === false) { return; }
            if (e === thisScript) { return; }
            if (context !== '' && reContext.test(e.src) === false) { return; }
            if (safe.logLevel > 1 && context !== '') {
                safe.uboLog(logPrefix, `Matched src\n${e.src}`);
            }
            const scriptText = getScriptText(e);
            if (reNeedle.test(scriptText) === false) { return; }
            if (safe.logLevel > 1) {
                safe.uboLog(logPrefix, `Matched text\n${scriptText}`);
            }
            safe.uboLog(logPrefix, 'Aborted');
            throw new ReferenceError(exceptionToken);
        };
        let currentValue = trapPropertyFn(target, {
            get: function() {
                validate();
                return currentValue;
            },
            set: function(a) {
                validate();
                currentValue = a;
            }
        }, { canThrow: true });
    });
}
