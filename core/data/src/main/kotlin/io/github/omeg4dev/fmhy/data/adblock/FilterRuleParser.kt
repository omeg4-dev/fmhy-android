package io.github.omeg4dev.fmhy.data.adblock

internal fun FilterEngineBuilder.isProcedural(line: String): Boolean {
    return line.contains(":has-text(") ||
            line.contains(":upward(") ||
            line.contains(":xpath(") ||
            line.contains(":matches-css") ||
            line.contains(":min-text-length") ||
            line.contains(":watch-attr") ||
            line.contains(":remove(") ||
            line.contains(":style(") ||
            line.contains(":others(") ||
            line.contains(":matches-path(") ||
            line.contains(":matches-attr(") ||
            line.contains(":matches-prop(") ||
            line.contains(":spath(") ||
            line.contains(":if(") ||
            line.contains(":if-not(") ||
            line.contains(":not(:has-text")
}

internal fun FilterEngineBuilder.addPureHostRule(pattern: String, blocksDocument: Boolean, raw: String) {
    val host = pattern.substring(2, pattern.length - 1).lowercase()
    val allMask = (1 shl ResourceType.entries.size) - 1
    val defaultMask = if (blocksDocument) {
        allMask and (1 shl ResourceType.POPUP.ordinal).inv()
    } else {
        allMask and (1 shl ResourceType.DOCUMENT.ordinal).inv() and (1 shl ResourceType.POPUP.ordinal).inv()
    }

    val rule = NetworkRule(
        id = nextRuleId++,
        rawRule = raw,
        isException = false,
        isImportant = false,
        party = 0,
        typesMask = defaultMask,
        redirectResource = null,
        isRedirectRule = false,
        blocksDocument = blocksDocument,
        caseSensitive = false,
        domains = emptyList(),
        toDomains = emptyList(),
        denyAllowDomains = emptyList(),
        isPureHost = true,
        pureHost = host,
        isRegex = false,
        regexPattern = null,
        pattern = pattern,
        token = null
    )
    rawNetworkRules.add(FilterEngineBuilder.RawNetworkRule(rule, isBadFilter = false))
}

internal fun FilterEngineBuilder.parseScriptlet(line: String) {
    val isException = line.contains("#@#+js(")
    val delim = if (isException) "#@#+js(" else "##+js("
    val parts = line.split(delim, limit = 2)
    val domainPart = parts[0].trim()
    val rest = parts[1].trim()

    if (domainPart.isEmpty()) {
        recordIgnored("generic-scriptlet")
        return
    }

    val domains = domainPart.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val inside = rest.removeSuffix(")").trim()

    if (inside.isEmpty()) {
        if (isException) {
            scriptletExceptions.add(ScriptletException(domains, null))
        }
        return
    }

    val args = splitScriptletArgs(inside)
    if (args.isEmpty()) return
    val rawName = args[0]
    val canonicalName = normalizeScriptletName(rawName)

    if (isException) {
        scriptletExceptions.add(ScriptletException(domains, canonicalName))
    } else {
        val scriptletArgs = args.drop(1)
        scriptletRules.add(ScriptletRule(domains, canonicalName, scriptletArgs))
    }
}

internal fun FilterEngineBuilder.splitScriptletArgs(content: String): List<String> {
    val args = mutableListOf<String>()
    val sb = StringBuilder()
    var i = 0
    while (i < content.length) {
        val c = content[i]
        if (c == '\\' && i + 1 < content.length && content[i + 1] == ',') {
            sb.append(',')
            i += 2
        } else if (c == ',') {
            args.add(sb.toString().trim())
            sb.setLength(0)
            i++
        } else {
            sb.append(c)
            i++
        }
    }
    args.add(sb.toString().trim())
    return args
}

internal fun FilterEngineBuilder.normalizeScriptletName(rawName: String): String {
    val clean = rawName.removeSuffix(".js").trim()
    return when (clean) {
        "set" -> "set-constant"
        "aopr" -> "abort-on-property-read"
        "aopw" -> "abort-on-property-write"
        "acs", "acis" -> "abort-current-script"
        "nostif" -> "no-setTimeout-if"
        "nosiif" -> "no-setInterval-if"
        "nowoif" -> "no-window-open-if"
        "ra" -> "remove-attr"
        "rc" -> "remove-class"
        "nano-stb" -> "nano-setTimeout-booster"
        "nano-sib" -> "nano-setInterval-booster"
        "aeld" -> "addEventListener-defuser"
        "rmnt" -> "remove-node-text"
        "trusted-set" -> "trusted-set-constant"
        else -> clean
    }
}

internal fun FilterEngineBuilder.parseCosmetic(line: String) {
    val isException = line.contains("#@#")
    val delim = if (isException) "#@#" else "##"
    val parts = line.split(delim, limit = 2)
    val domainPart = parts[0].trim()
    val selector = parts[1].trim()

    if (selector.isEmpty()) return

    if (domainPart.isEmpty()) {
        if (isException) {
            genericExceptions.add(selector)
        } else {
            addGenericSelector(selector)
        }
    } else {
        val domains = domainPart.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (isException) {
            domainCosmeticExceptions.add(CosmeticRule(domains, selector))
        } else {
            domainCosmeticRules.add(CosmeticRule(domains, selector))
        }
    }
}

internal fun FilterEngineBuilder.addGenericSelector(selector: String) {
    val s = selector.trim()
    val firstChar = s.firstOrNull() ?: return
    if (firstChar == '.') {
        val token = extractCssToken(s.substring(1))
        if (token.isNotEmpty()) {
            genericByClass.computeIfAbsent(token) { ArrayList() }.add(selector)
            return
        }
    } else if (firstChar == '#') {
        val token = extractCssToken(s.substring(1))
        if (token.isNotEmpty()) {
            genericById.computeIfAbsent(token) { ArrayList() }.add(selector)
            return
        }
    }

    // Lowly generic selector
    if (lowlyGenericSelectors.size < 2000) {
        lowlyGenericSelectors.add(selector)
    } else {
        recordIgnored("lowly-generic")
    }
}

internal fun FilterEngineBuilder.extractCssToken(s: String): String {
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_') {
            i++
        } else {
            break
        }
    }
    return s.substring(0, i)
}

internal fun FilterEngineBuilder.parseNetworkRule(line: String, isFmhyList: Boolean) {
    val isException = line.startsWith("@@")
    val ruleText = if (isException) line.substring(2) else line

    var pattern: String
    var optionsStr: String? = null

    val isRegexPattern = ruleText.startsWith("/") && ruleText.contains('/')
    if (isRegexPattern) {
        val closingSlash = ruleText.lastIndexOf('/')
        val dollarIdx = ruleText.indexOf('$', closingSlash)
        if (dollarIdx != -1) {
            pattern = ruleText.substring(0, dollarIdx)
            optionsStr = ruleText.substring(dollarIdx + 1)
        } else {
            pattern = ruleText
        }
    } else {
        val dollarIdx = ruleText.lastIndexOf('$')
        if (dollarIdx != -1) {
            pattern = ruleText.substring(0, dollarIdx)
            optionsStr = ruleText.substring(dollarIdx + 1)
        } else {
            pattern = ruleText
        }
    }

    var isRegex = false
    var regexContent: String? = null
    if (pattern.startsWith("/") && pattern.endsWith("/") && pattern.length >= 2) {
        isRegex = true
        regexContent = pattern.substring(1, pattern.length - 1)
        if (regexContent.length > 300) {
            recordIgnored("regex-too-long")
            return
        }
        try {
            Regex(regexContent)
        } catch (_: Exception) {
            recordIgnored("invalid-regex")
            return
        }
    }

    var isBadFilter = false
    var isImportant = false
    var party = 0 // 0=any, 1=1p, 2=3p
    var caseSensitive = false
    var redirectResource: String? = null
    var isRedirectRule = false

    val positiveTypes = HashSet<ResourceType>()
    val negativeTypes = HashSet<ResourceType>()
    var hasAllType = false

    val domains = ArrayList<String>()
    val toDomains = ArrayList<String>()
    val denyAllowDomains = ArrayList<String>()

    if (optionsStr != null) {
        val options = optionsStr.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        for (opt in options) {
            when {
                opt == "badfilter" -> isBadFilter = true
                opt == "important" -> isImportant = true
                opt == "match-case" -> caseSensitive = true

                opt == "third-party" || opt == "3p" || opt == "strict3p" -> party = 2
                opt == "~third-party" || opt == "first-party" || opt == "1p" || opt == "strict1p" -> party = 1

                opt.startsWith("domain=") || opt.startsWith("from=") -> {
                    val v = opt.substringAfter('=')
                    domains.addAll(v.split('|').map { it.trim() }.filter { it.isNotEmpty() })
                }
                opt.startsWith("to=") -> {
                    val v = opt.substringAfter('=')
                    toDomains.addAll(v.split('|').map { it.trim() }.filter { it.isNotEmpty() })
                }
                opt.startsWith("denyallow=") -> {
                    val v = opt.substringAfter('=')
                    denyAllowDomains.addAll(v.split('|').map { it.trim() }.filter { it.isNotEmpty() })
                }

                opt.startsWith("redirect=") || opt.startsWith("redirect-rule=") -> {
                    isRedirectRule = opt.startsWith("redirect-rule=")
                    val rawRes = opt.substringAfter('=').substringBefore(':').trim()
                    if (NeuteredResources.get(rawRes) != null) {
                        redirectResource = rawRes
                    }
                }

                // Page exception options (only on @@ rules)
                opt == "document" || opt == "doc" -> {
                    positiveTypes.add(ResourceType.DOCUMENT)
                }
                opt == "all" -> hasAllType = true

                opt == "generichide" || opt == "ghide" -> {
                    if (isException) extractPageHosts(pattern, domains).forEach { pageGenerichide.add(it) }
                }
                opt == "elemhide" || opt == "ehide" -> {
                    if (isException) extractPageHosts(pattern, domains).forEach { pageElemhide.add(it) }
                }
                opt == "specifichide" || opt == "shide" -> {
                    if (isException) extractPageHosts(pattern, domains).forEach { pageSpecifichide.add(it) }
                }

                // Resource types
                opt == "script" -> positiveTypes.add(ResourceType.SCRIPT)
                opt == "~script" -> negativeTypes.add(ResourceType.SCRIPT)
                opt == "image" -> positiveTypes.add(ResourceType.IMAGE)
                opt == "~image" -> negativeTypes.add(ResourceType.IMAGE)
                opt == "stylesheet" || opt == "css" -> positiveTypes.add(ResourceType.STYLESHEET)
                opt == "~stylesheet" || opt == "~css" -> negativeTypes.add(ResourceType.STYLESHEET)
                opt == "xmlhttprequest" || opt == "xhr" -> positiveTypes.add(ResourceType.XHR)
                opt == "~xmlhttprequest" || opt == "~xhr" -> negativeTypes.add(ResourceType.XHR)
                opt == "subdocument" || opt == "frame" -> positiveTypes.add(ResourceType.SUBDOCUMENT)
                opt == "~subdocument" || opt == "~frame" -> negativeTypes.add(ResourceType.SUBDOCUMENT)
                opt == "media" -> positiveTypes.add(ResourceType.MEDIA)
                opt == "~media" -> negativeTypes.add(ResourceType.MEDIA)
                opt == "font" -> positiveTypes.add(ResourceType.FONT)
                opt == "~font" -> negativeTypes.add(ResourceType.FONT)
                opt == "websocket" -> positiveTypes.add(ResourceType.WEBSOCKET)
                opt == "~websocket" -> negativeTypes.add(ResourceType.WEBSOCKET)
                opt == "ping" -> positiveTypes.add(ResourceType.PING)
                opt == "~ping" -> negativeTypes.add(ResourceType.PING)
                opt == "other" || opt == "object" -> positiveTypes.add(ResourceType.OTHER)
                opt == "~other" || opt == "~object" -> negativeTypes.add(ResourceType.OTHER)
                opt == "popup" -> positiveTypes.add(ResourceType.POPUP)
                opt == "~popup" -> negativeTypes.add(ResourceType.POPUP)
                opt == "~document" || opt == "~doc" -> negativeTypes.add(ResourceType.DOCUMENT)

                // Known unsupported options -> count in stats.ignored and skip rule
                opt == "removeparam" || opt.startsWith("removeparam=") -> {
                    recordIgnored("removeparam"); return
                }
                opt == "csp" || opt.startsWith("csp=") -> {
                    recordIgnored("csp"); return
                }
                opt == "header" || opt.startsWith("header=") -> {
                    recordIgnored("unsupported-option:header"); return
                }
                opt == "permissions" || opt.startsWith("permissions=") -> {
                    recordIgnored("permissions"); return
                }
                opt == "replace" || opt.startsWith("replace=") -> {
                    recordIgnored("replace"); return
                }
                opt == "urlskip" || opt.startsWith("urlskip=") -> {
                    recordIgnored("urlskip"); return
                }
                opt == "uritransform" || opt.startsWith("uritransform=") -> {
                    recordIgnored("uritransform"); return
                }
                opt == "method" || opt.startsWith("method=") -> {
                    recordIgnored("method"); return
                }
                opt == "ipaddress" || opt.startsWith("ipaddress=") -> {
                    recordIgnored("ipaddress"); return
                }
                else -> {
                    val key = opt.substringBefore('=')
                    recordIgnored("unsupported-option:$key")
                    return
                }
            }
        }
    }

    // Check if page allow document: @@||host^$document or @@$all
    if (isException && (hasAllType || positiveTypes.contains(ResourceType.DOCUMENT))) {
        extractPageHosts(pattern, domains).forEach { pageAllowDocument.add(it) }
    }

    val allMask = (1 shl ResourceType.entries.size) - 1
    val blocksDocument = isFmhyList && pattern.startsWith("||") && pattern.endsWith("^") &&
            !pattern.contains('/') && !pattern.contains('*')

    val typesMask = if (hasAllType) {
        allMask
    } else if (positiveTypes.isNotEmpty()) {
        var mask = 0
        for (t in positiveTypes) {
            if (t !in negativeTypes) {
                mask = mask or (1 shl t.ordinal)
            }
        }
        mask
    } else {
        var mask = if (blocksDocument) {
            allMask and (1 shl ResourceType.POPUP.ordinal).inv()
        } else {
            allMask and (1 shl ResourceType.DOCUMENT.ordinal).inv() and (1 shl ResourceType.POPUP.ordinal).inv()
        }
        for (t in negativeTypes) {
            mask = mask and (1 shl t.ordinal).inv()
        }
        mask
    }

    // Pure hostname check
    val isPureHost = pattern.startsWith("||") && pattern.endsWith("^") &&
            !pattern.substring(2, pattern.length - 1).let {
                it.contains('/') || it.contains('*') || it.contains('^') ||
                        it.contains('|') || it.contains('?') || it.contains('#') ||
                        it.contains('$') || !it.contains('.')
            } && domains.isEmpty() && toDomains.isEmpty() && denyAllowDomains.isEmpty() &&
            !caseSensitive && !isRegex

    val pureHost = if (isPureHost) pattern.substring(2, pattern.length - 1).lowercase() else null

    val token = if (isPureHost || isRegex) null else extractLongestToken(pattern)

    val rule = NetworkRule(
        id = nextRuleId++,
        rawRule = line,
        isException = isException,
        isImportant = isImportant,
        party = party,
        typesMask = typesMask,
        redirectResource = redirectResource,
        isRedirectRule = isRedirectRule,
        blocksDocument = blocksDocument,
        caseSensitive = caseSensitive,
        domains = domains,
        toDomains = toDomains,
        denyAllowDomains = denyAllowDomains,
        isPureHost = isPureHost,
        pureHost = pureHost,
        isRegex = isRegex,
        regexPattern = regexContent,
        pattern = pattern,
        token = token
    )

    if (isBadFilter) {
        badfilteredKeys.add(rule.canonicalKey())
    } else {
        rawNetworkRules.add(FilterEngineBuilder.RawNetworkRule(rule, isBadFilter = false))
    }
}

internal fun FilterEngineBuilder.extractPageHosts(pattern: String, domains: List<String>): List<String> {
    val hosts = mutableListOf<String>()
    if (pattern.startsWith("||") && pattern.endsWith("^")) {
        val h = pattern.substring(2, pattern.length - 1)
        if (!h.contains('/') && !h.contains('*')) {
            hosts.add(h.lowercase())
        }
    }
    for (d in domains) {
        if (!d.startsWith("~")) {
            hosts.add(d.lowercase())
        }
    }
    return hosts
}

private val COMMON_TOKENS = setOf("http", "https", "www", "com")

internal fun FilterEngineBuilder.extractLongestToken(pattern: String): String? {
    val lower = pattern.lowercase()
    var bestToken: String? = null
    var bestLen = 0

    var i = 0
    val n = lower.length
    while (i < n) {
        val ch = lower[i]
        if ((ch in 'a'..'z') || (ch in '0'..'9') || ch == '%') {
            val start = i
            while (i < n && ((lower[i] in 'a'..'z') || (lower[i] in '0'..'9') || lower[i] == '%')) {
                i++
            }
            val len = i - start
            if (len >= 3) {
                val token = lower.substring(start, i)
                if (token !in COMMON_TOKENS) {
                    val precededByStar = start > 0 && lower[start - 1] == '*'
                    val followedByStar = i < n && lower[i] == '*'
                    if (!precededByStar && !followedByStar) {
                        if (len > bestLen) {
                            bestLen = len
                            bestToken = token
                        }
                    }
                }
            }
        } else {
            i++
        }
    }
    return bestToken
}

