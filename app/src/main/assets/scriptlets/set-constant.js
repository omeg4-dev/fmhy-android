// Ported from uBlock Origin (GPL-3.0), src/js/resources/set-constant.js

function set_constant(chain = '', rawValue = '', ...varargs) {
    if (chain === '') { return; }
    const safe = safeSelf();
    const logPrefix = safe.makeLogPrefix('set-constant', chain, rawValue);
    const extraArgs = safe.parseVarargs(varargs);
    function setConstant(chain, rawValue) {
        const trappedProp = (() => {
            const pos = chain.lastIndexOf('.');
            if (pos === -1) { return chain; }
            return chain.slice(pos + 1);
        })();
        const thisScript = document.currentScript;
        let value = validateConstantFn(false, rawValue, extraArgs);
        let canceled = false;
        const setConstantValue = (v) => {
            if (canceled) { return; }
            value = v;
        };
        const trapProp = (owner, prop) => {
            let desc;
            try {
                desc = Object.getOwnPropertyDescriptor(owner, prop);
            } catch {
                return;
            }
            if (desc instanceof Object && desc.configurable === false) {
                return;
            }
            trapPropertyFn(chain, {
                get() {
                    return value;
                },
                set(a) {
                    if (thisScript === document.currentScript) {
                        return;
                    }
                    setConstantValue(a);
                }
            });
        };
        const trapChain = (owner, chain) => {
            const pos = chain.indexOf('.');
            if (pos === -1) {
                trapProp(owner, chain);
                return;
            }
            const prop = chain.slice(0, pos);
            const v = owner[prop];
            chain = chain.slice(pos + 1);
            if (v instanceof Object) {
                trapChain(v, chain);
                return;
            }
            let o = v;
            trapPropertyFn(prop, {
                get() {
                    return o;
                },
                set(a) {
                    o = a;
                    if (a instanceof Object) {
                        trapChain(a, chain);
                    }
                }
            });
        };
        trapChain(window, chain);
    }
    runAt(() => {
        setConstant(chain, rawValue);
    }, extraArgs.runAt);
}
