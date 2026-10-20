// Ported from uBlock Origin (GPL-3.0), src/js/resources/prevent-fetch.js

function no_fetch_if(
    propsToMatch = '',
    responseBody = '',
    responseType = '',
    ...varargs
) {
    const trusted = false;
    const safe = safeSelf();
    const setTimeout = self.setTimeout;
    const logPrefix = safe.makeLogPrefix('prevent-fetch', propsToMatch, responseBody, responseType);
    const extraArgs = safe.parseVarargs(varargs);
    const propNeedles = parsePropertiesToMatchFn(propsToMatch, 'url');
    const validResponseProps = {
        ok: [ false, true ],
        status: [ 403 ],
        statusText: [ '', 'Not Found' ],
        type: [ 'basic', 'cors', 'default', 'error', 'opaque' ],
    };
    const responseProps = {
        statusText: { value: 'OK' },
    };
    const responseHeaders = {};
    if (/^\{.*\}$/.test(responseType)) {
        try {
            Object.entries(JSON.parse(responseType)).forEach(([ p, v ]) => {
                if (validResponseProps[p] === undefined) { return; }
                if (validResponseProps[p].includes(v) === false) { return; }
                responseProps[p] = { value: v };
            });
        } catch { }
    } else if (responseType !== '') {
        if (validResponseProps.type.includes(responseType)) {
            responseProps.type = { value: responseType };
        }
    }
    proxyApplyFn('fetch', function fetch(context) {
        const { callArgs } = context;
        const details = collateFetchArgumentsFn(...callArgs);
        if (safe.logLevel > 1 || (propsToMatch === '' && responseBody === '')) {
            const out = Array.from(Object.entries(details)).map(a => `${a[0]}:${a[1]}`);
            safe.uboLog(logPrefix, `Called: ${out.join('\n')}`);
        }
        if (propsToMatch === '' && responseBody === '') {
            return context.reflect();
        }
        const matched = matchObjectPropertiesFn(propNeedles, details);
        if (matched === undefined || matched.length === 0) {
            return context.reflect();
        }
        return Promise.resolve(generateContentFn(trusted, responseBody)).then(text => {
            safe.uboLog(logPrefix, `Prevented with response "${text}"`);
            const headers = Object.assign({}, responseHeaders);
            if (headers['content-length'] === undefined) {
                headers['content-length'] = text.length;
            }
            const response = new Response(text, { headers });
            const props = Object.assign(
                { url: { value: details.url } },
                responseProps
            );
            safe.Object_defineProperties(response, props);
            if (extraArgs.throttle) {
                return new Promise(resolve => {
                    setTimeout(() => { resolve(response); }, extraArgs.throttle);
                });
            }
            return response;
        });
    });
}
