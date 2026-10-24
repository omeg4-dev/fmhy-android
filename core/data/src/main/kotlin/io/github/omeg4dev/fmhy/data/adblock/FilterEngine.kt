package io.github.omeg4dev.fmhy.data.adblock

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class FilterEngine internal constructor(
    val stats: EngineStats,
    internal val allNetworkRules: List<NetworkRule>,
    private val pureHostBlockRules: Map<String, List<NetworkRule>>,
    private val pureHostAllowRules: Map<String, List<NetworkRule>>,
    private val tokenIndex: Map<String, List<NetworkRule>>,
    private val fallbackGlobalRules: List<NetworkRule>,
    private val fallbackByDomain: Map<String, List<NetworkRule>>,
    internal val pageAllowDocument: Set<String>,
    internal val pageGenerichide: Set<String>,
    internal val pageElemhide: Set<String>,
    internal val pageSpecifichide: Set<String>,
    internal val domainCosmeticRules: List<CosmeticRule>,
    internal val domainCosmeticExceptions: List<CosmeticRule>,
    internal val genericByClass: Map<String, List<String>>,
    internal val genericById: Map<String, List<String>>,
    internal val lowlyGenericSelectors: List<String>,
    internal val genericExceptions: Set<String>,
    internal val scriptletRules: List<ScriptletRule>,
    internal val scriptletExceptions: List<ScriptletException>
) {
    private val matchGen = AtomicInteger(0)

    fun match(req: FilterRequest): MatchResult {
        val reqHost = DomainUtils.extractHost(req.url) ?: return MatchResult.NoMatch
        val srcHost = DomainUtils.extractHost(req.sourceUrl) ?: reqHost

        val pageHost = if (req.type == ResourceType.DOCUMENT) reqHost else srcHost

        // @@||host^$document (or $all on exception) on the PAGE's host -> everything allowed
        if (isPageExceptionMatched(pageHost, pageAllowDocument)) {
            return MatchResult.Allow("@@||$pageHost^\$document")
        }

        val reqTopDomain = DomainUtils.getTopPrivateDomain(reqHost)
        val srcTopDomain = if (srcHost == reqHost) reqTopDomain else DomainUtils.getTopPrivateDomain(srcHost)
        val is3p = !reqTopDomain.equals(srcTopDomain, ignoreCase = true)
        val reqTypeMask = 1 shl req.type.ordinal
        val reqParents = DomainUtils.extractParentDomains(reqHost)
        val srcParents = DomainUtils.extractParentDomains(srcHost)

        var importantBlock: NetworkRule? = null
        var importantAllow: NetworkRule? = null
        var firstBlock: NetworkRule? = null
        var firstAllow: NetworkRule? = null
        var firstRedirect: NetworkRule? = null
        var hasPlainBlockMatch = false

        // 1. Pure hostname rules lookup (O(number of domain labels) hashmap lookups)
        for (host in reqParents) {
            pureHostAllowRules[host]?.let { list ->
                for (rule in list) {
                    if (matchesRuleOptions(rule, reqHost, srcHost, reqTopDomain, srcTopDomain, is3p, reqTypeMask)) {
                        if (rule.isImportant && importantAllow == null) importantAllow = rule
                        if (firstAllow == null) firstAllow = rule
                    }
                }
            }

            pureHostBlockRules[host]?.let { list ->
                for (rule in list) {
                    if (matchesRuleOptions(rule, reqHost, srcHost, reqTopDomain, srcTopDomain, is3p, reqTypeMask)) {
                        if (rule.isImportant && importantBlock == null) importantBlock = rule
                        if (rule.redirectResource != null) {
                            if (firstRedirect == null) firstRedirect = rule
                        } else {
                            hasPlainBlockMatch = true
                            if (firstBlock == null) firstBlock = rule
                        }
                    }
                }
            }
        }

        // 2. Token-indexed and fallback rules
        val gen = matchGen.incrementAndGet()
        val urlTokens = extractTokens(req.url.lowercase())

        fun checkRule(rule: NetworkRule) {
            if (rule.lastMatchGen == gen) return
            rule.lastMatchGen = gen

            if (!matchesRuleOptions(rule, reqHost, srcHost, reqTopDomain, srcTopDomain, is3p, reqTypeMask)) return
            if (!matchesPattern(rule, req.url)) return

            if (rule.isException) {
                if (rule.isImportant && importantAllow == null) importantAllow = rule
                if (firstAllow == null) firstAllow = rule
            } else {
                if (rule.isImportant && importantBlock == null) importantBlock = rule
                if (rule.redirectResource != null) {
                    if (firstRedirect == null) firstRedirect = rule
                } else {
                    hasPlainBlockMatch = true
                    if (firstBlock == null) firstBlock = rule
                }
            }
        }

        for (tok in urlTokens) {
            tokenIndex[tok]?.let { list ->
                for (rule in list) {
                    checkRule(rule)
                }
            }
        }

        // Source-domain-restricted fallback rules
        for (srcDomain in srcParents) {
            fallbackByDomain[srcDomain]?.let { list ->
                for (rule in list) {
                    checkRule(rule)
                }
            }
        }

        // Global fallback rules (only ~15 rules total)
        for (rule in fallbackGlobalRules) {
            checkRule(rule)
        }

        // 3. Resolution order (Section 2.3)
        // If any important block rule matches -> Block (unless an exception is also important)
        if (importantBlock != null) {
            return if (importantAllow != null) {
                MatchResult.Allow(importantAllow.rawRule)
            } else {
                MatchResult.Block(importantBlock.rawRule)
            }
        }

        // If a block or redirect rule matches and an @@ rule matches -> Allow
        val hasBlockOrRedirect = firstBlock != null || firstRedirect != null
        if (hasBlockOrRedirect && firstAllow != null) {
            return MatchResult.Allow(firstAllow.rawRule)
        }

        // If redirect rule matches
        if (firstRedirect != null) {
            return if (firstRedirect.isRedirectRule) {
                if (hasPlainBlockMatch) {
                    MatchResult.Redirect(firstRedirect.redirectResource!!, firstRedirect.rawRule)
                } else {
                    MatchResult.NoMatch
                }
            } else {
                MatchResult.Redirect(firstRedirect.redirectResource!!, firstRedirect.rawRule)
            }
        }

        // If plain block matches
        if (firstBlock != null) {
            return MatchResult.Block(firstBlock.rawRule)
        }

        return MatchResult.NoMatch
    }

    /** Domain-specific hide CSS for the page: "sel1,sel2,...{display:none!important}" chunks of <=500 selectors joined by "\n". */
    fun cosmeticCss(pageUrl: String): String {
        val host = DomainUtils.extractHost(pageUrl) ?: return ""
        if (isPageExceptionMatched(host, pageAllowDocument) ||
            isPageExceptionMatched(host, pageElemhide) ||
            isPageExceptionMatched(host, pageSpecifichide)
        ) {
            return ""
        }

        val topDomain = DomainUtils.getTopPrivateDomain(host)
        val selectors = LinkedHashSet<String>()
        for (r in domainCosmeticRules) {
            if (DomainUtils.matchesDomainConstraints(host, r.domains, topDomain)) {
                selectors.add(r.selector)
            }
        }

        val exceptions = HashSet<String>()
        for (r in domainCosmeticExceptions) {
            if (DomainUtils.matchesDomainConstraints(host, r.domains, topDomain)) {
                exceptions.add(r.selector)
            }
        }

        selectors.removeAll(exceptions)
        if (selectors.isEmpty()) return ""

        return selectors.chunked(500).joinToString("\n") { chunk ->
            "${chunk.joinToString(",")}{display:none!important}"
        }
    }

    /** Generic selectors (##.cls / ###id) that apply given the classes/ids present on the page, minus #@# exceptions for that host. */
    fun genericSelectors(pageUrl: String, classes: Collection<String>, ids: Collection<String>): List<String> {
        val host = DomainUtils.extractHost(pageUrl) ?: ""
        val topDomain = if (host.isNotEmpty()) DomainUtils.getTopPrivateDomain(host) else ""
        if (host.isNotEmpty()) {
            if (isPageExceptionMatched(host, pageAllowDocument) ||
                isPageExceptionMatched(host, pageElemhide) ||
                isPageExceptionMatched(host, pageGenerichide)
            ) {
                return emptyList()
            }
        }

        val result = LinkedHashSet<String>()
        for (cls in classes) {
            genericByClass[cls]?.let { result.addAll(it) }
        }
        for (id in ids) {
            genericById[id]?.let { result.addAll(it) }
        }
        result.addAll(lowlyGenericSelectors)

        val exceptions = HashSet<String>(genericExceptions)
        if (host.isNotEmpty()) {
            for (r in domainCosmeticExceptions) {
                if (DomainUtils.matchesDomainConstraints(host, r.domains, topDomain)) {
                    exceptions.add(r.selector)
                }
            }
        }

        result.removeAll(exceptions)
        return result.toList()
    }

    fun scriptlets(pageUrl: String): List<Scriptlet> {
        val host = DomainUtils.extractHost(pageUrl) ?: return emptyList()
        if (isPageExceptionMatched(host, pageAllowDocument)) {
            return emptyList()
        }

        val topDomain = DomainUtils.getTopPrivateDomain(host)
        var allDisabled = false
        val exceptedNames = HashSet<String>()
        for (e in scriptletExceptions) {
            if (DomainUtils.matchesDomainConstraints(host, e.domains, topDomain)) {
                if (e.name == null) {
                    allDisabled = true
                    break
                } else {
                    exceptedNames.add(e.name)
                }
            }
        }

        if (allDisabled) return emptyList()

        val results = mutableListOf<Scriptlet>()
        for (s in scriptletRules) {
            if (DomainUtils.matchesDomainConstraints(host, s.domains, topDomain)) {
                if (s.name !in exceptedNames) {
                    results.add(Scriptlet(s.name, s.args))
                }
            }
        }
        return results
    }

    private fun isPageExceptionMatched(host: String, set: Set<String>): Boolean {
        if (set.isEmpty()) return false
        val parents = DomainUtils.extractParentDomains(host)
        for (p in parents) {
            if (p in set) return true
        }
        val topDomain = DomainUtils.getTopPrivateDomain(host)
        for (pattern in set) {
            if (pattern.endsWith(".*") && DomainUtils.matchesDomainOrEntity(host, pattern, topDomain)) {
                return true
            }
        }
        return false
    }

    private fun matchesRuleOptions(
        rule: NetworkRule,
        reqHost: String,
        srcHost: String,
        reqTopDomain: String,
        srcTopDomain: String,
        is3p: Boolean,
        reqTypeMask: Int
    ): Boolean {
        // Party check: 0=any, 1=1p, 2=3p
        if (rule.party == 1 && is3p) return false
        if (rule.party == 2 && !is3p) return false

        // Type check
        if ((rule.typesMask and reqTypeMask) == 0) return false

        // Domain constraints (source domain)
        if (rule.domains.isNotEmpty() && !DomainUtils.matchesDomainConstraints(srcHost, rule.domains, srcTopDomain)) {
            return false
        }

        // To constraints (request host)
        if (rule.toDomains.isNotEmpty() && !DomainUtils.matchesDomainConstraints(reqHost, rule.toDomains, reqTopDomain)) {
            return false
        }

        // Denyallow constraints (request host cannot match)
        if (rule.denyAllowDomains.isNotEmpty()) {
            for (da in rule.denyAllowDomains) {
                if (DomainUtils.matchesDomainOrEntity(reqHost, da, reqTopDomain)) {
                    return false
                }
            }
        }

        return true
    }

    private fun matchesPattern(rule: NetworkRule, url: String): Boolean {
        if (rule.pattern == "*" || rule.pattern.isEmpty()) return true

        val regex = rule.compiledRegex
        if (regex != null) {
            return regex.containsMatchIn(url)
        }

        val target = if (rule.caseSensitive) url else url.lowercase()
        val pat = if (rule.caseSensitive) rule.pattern else rule.pattern.lowercase()

        return when {
            pat.startsWith("|") && pat.endsWith("|") -> target == pat.substring(1, pat.length - 1)
            pat.startsWith("|") -> target.startsWith(pat.substring(1))
            pat.endsWith("|") -> target.endsWith(pat.substring(0, pat.length - 1))
            else -> target.contains(pat)
        }
    }

    fun writeTo(out: OutputStream) {
        FilterEngineCodec.write(this, out)
    }

    companion object {
        fun readFrom(input: InputStream): FilterEngine = FilterEngineCodec.read(input)

        internal fun compileRuleRegex(rule: NetworkRule) {
            if (rule.isPureHost) return
            if (rule.isRegex) {
                val p = rule.regexPattern ?: return
                rule.compiledRegex = Regex(p, if (rule.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
            } else if (rule.pattern.contains('*') || rule.pattern.contains('^') || rule.pattern.startsWith("||")) {
                val regexStr = patternToRegexString(rule.pattern)
                rule.compiledRegex = Regex(regexStr, if (rule.caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
            }
        }

        internal fun patternToRegexString(pattern: String): String {
            var p = pattern
            val sb = StringBuilder()

            var startAnchor = false
            var domainAnchor = false

            if (p.startsWith("||")) {
                domainAnchor = true
                p = p.substring(2)
            } else if (p.startsWith("|")) {
                startAnchor = true
                p = p.substring(1)
            }

            var endAnchor = false
            if (p.endsWith("|")) {
                endAnchor = true
                p = p.substring(0, p.length - 1)
            }

            if (domainAnchor) {
                sb.append("^(?:[a-zA-Z]+://)?(?:[a-zA-Z0-9_.%-]+\\.)?")
            } else if (startAnchor) {
                sb.append("^")
            }

            var i = 0
            while (i < p.length) {
                val c = p[i]
                when (c) {
                    '*' -> sb.append(".*")
                    '^' -> sb.append("(?:[^a-zA-Z0-9_.%-]|$)")
                    '\\', '.', '?', '+', '(', ')', '[', ']', '{', '}', '$' -> sb.append('\\').append(c)
                    '|' -> sb.append("\\|")
                    else -> sb.append(c)
                }
                i++
            }

            if (endAnchor) {
                sb.append("$")
            }

            return sb.toString()
        }

        internal fun extractTokens(lowerUrl: String): Set<String> {
            val tokens = HashSet<String>()
            var i = 0
            val n = lowerUrl.length
            while (i < n) {
                val ch = lowerUrl[i]
                if ((ch in 'a'..'z') || (ch in '0'..'9') || ch == '%') {
                    val start = i
                    while (i < n && ((lowerUrl[i] in 'a'..'z') || (lowerUrl[i] in '0'..'9') || lowerUrl[i] == '%')) {
                        i++
                    }
                    if (i - start >= 3) {
                        tokens.add(lowerUrl.substring(start, i))
                    }
                } else {
                    i++
                }
            }
            return tokens
        }

        internal fun assembleEngine(
            stats: EngineStats,
            allNetworkRules: List<NetworkRule>,
            pageAllowDocument: Set<String>,
            pageGenerichide: Set<String>,
            pageElemhide: Set<String>,
            pageSpecifichide: Set<String>,
            domainCosmeticRules: List<CosmeticRule>,
            domainCosmeticExceptions: List<CosmeticRule>,
            genericByClass: Map<String, List<String>>,
            genericById: Map<String, List<String>>,
            lowlyGenericSelectors: List<String>,
            genericExceptions: Set<String>,
            scriptletRules: List<ScriptletRule>,
            scriptletExceptions: List<ScriptletException>
        ): FilterEngine {
            val pureHostBlockRules = HashMap<String, MutableList<NetworkRule>>()
            val pureHostAllowRules = HashMap<String, MutableList<NetworkRule>>()
            val tokenIndex = HashMap<String, MutableList<NetworkRule>>()
            val fallbackGlobalRules = ArrayList<NetworkRule>()
            val fallbackByDomain = HashMap<String, MutableList<NetworkRule>>()

            for (rule in allNetworkRules) {
                if (rule.isPureHost) {
                    val host = rule.pureHost ?: continue
                    val targetMap = if (rule.isException) pureHostAllowRules else pureHostBlockRules
                    targetMap.computeIfAbsent(host) { ArrayList() }.add(rule)
                } else {
                    val tok = rule.token
                    if (tok != null) {
                        tokenIndex.computeIfAbsent(tok) { ArrayList() }.add(rule)
                    } else {
                        // Fallback rule: check if it is restricted by source domains
                        val positiveDomains = rule.domains.filter { !it.startsWith("~") }
                        if (positiveDomains.isNotEmpty()) {
                            for (d in positiveDomains) {
                                fallbackByDomain.computeIfAbsent(d.lowercase()) { ArrayList() }.add(rule)
                            }
                        } else {
                            fallbackGlobalRules.add(rule)
                        }
                    }
                }
            }

            return FilterEngine(
                stats,
                allNetworkRules,
                pureHostBlockRules,
                pureHostAllowRules,
                tokenIndex,
                fallbackGlobalRules,
                fallbackByDomain,
                pageAllowDocument,
                pageGenerichide,
                pageElemhide,
                pageSpecifichide,
                domainCosmeticRules,
                domainCosmeticExceptions,
                genericByClass,
                genericById,
                lowlyGenericSelectors,
                genericExceptions,
                scriptletRules,
                scriptletExceptions
            )
        }
    }
}
