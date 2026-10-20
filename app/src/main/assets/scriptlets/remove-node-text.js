// Ported from uBlock Origin (GPL-3.0), src/js/resources/scriptlets.js

function remove_node_text(
    nodeName = '',
    includes = '',
    ...extraArgs
) {
    replaceNodeTextFn(nodeName, '', '', 'includes', includes || '', ...extraArgs);
}
