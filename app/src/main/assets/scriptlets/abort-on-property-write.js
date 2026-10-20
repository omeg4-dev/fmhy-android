// Ported from uBlock Origin (GPL-3.0), src/js/resources/scriptlets.js

function abort_on_property_write(prop = '') {
    if (typeof prop !== 'string') { return; }
    if (prop === '') { return; }
    const safe = safeSelf();
    const logPrefix = safe.makeLogPrefix('abort-on-property-write', prop);
    const exceptionToken = getExceptionTokenFn();
    let owner = window;
    for (;;) {
        const pos = prop.indexOf('.');
        if (pos === -1) { break; }
        owner = owner[prop.slice(0, pos)];
        if (owner instanceof Object === false) { return; }
        prop = prop.slice(pos + 1);
    }
    delete owner[prop];
    Object.defineProperty(owner, prop, {
        set: function() {
            safe.uboLog(logPrefix, 'Aborted');
            throw new ReferenceError(exceptionToken);
        }
    });
}
