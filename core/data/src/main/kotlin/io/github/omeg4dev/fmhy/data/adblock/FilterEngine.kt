package io.github.omeg4dev.fmhy.data.adblock

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class FilterEngine internal constructor(
    val stats: EngineStats,
    private val allNetworkRules: List<NetworkRule>,
    private val pureHostBlockRules: Map<String, List<NetworkRule>>,
    private val pureHostAllowRules: Map<String, List<NetworkRule>>,
    private val tokenIndex: Map<String, List<NetworkRule>>,
    private val fallbackGlobalRules: List<NetworkRule>,
    private val fallbackByDomain: Map<String, List<NetworkRule>>,
    private val pageAllowDocument: Set<String>,
    private val pageGenerichide: Set<String>,
    private val pageElemhide: Set<String>,
    private val pageSpecifichide: Set<String>,
    private val domainCosmeticRules: List<CosmeticRule>,
    private val domainCosmeticExceptions: List<CosmeticRule>,
    private val genericByClass: Map<String, List<String>>,
    private val genericById: Map<String, List<String>>,
    private val lowlyGenericSelectors: List<String>,
    private val genericExceptions: Set<String>,
    private val scriptletRules: List<ScriptletRule>,
    private val scriptletExceptions: List<ScriptletException>
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
        val dos = DataOutputStream(out)
        dos.writeInt(MAGIC)
        dos.writeInt(VERSION)

        // stats
        dos.writeInt(stats.networkRules)
        dos.writeInt(stats.cosmeticRules)
        dos.writeInt(stats.scriptletRules)
        dos.writeInt(stats.ignored.size)
        for ((k, v) in stats.ignored) {
            writeString(dos, k)
            dos.writeInt(v)
        }

        // network rules
        dos.writeInt(allNetworkRules.size)
        for (rule in allNetworkRules) {
            dos.writeInt(rule.id)
            writeString(dos, rule.rawRule)
            dos.writeBoolean(rule.isException)
            dos.writeBoolean(rule.isImportant)
            dos.writeByte(rule.party)
            dos.writeInt(rule.typesMask)
            writeNullableString(dos, rule.redirectResource)
            dos.writeBoolean(rule.isRedirectRule)
            dos.writeBoolean(rule.blocksDocument)
            dos.writeBoolean(rule.caseSensitive)
            writeStringList(dos, rule.domains)
            writeStringList(dos, rule.toDomains)
            writeStringList(dos, rule.denyAllowDomains)
            dos.writeBoolean(rule.isPureHost)
            writeNullableString(dos, rule.pureHost)
            dos.writeBoolean(rule.isRegex)
            writeNullableString(dos, rule.regexPattern)
            writeString(dos, rule.pattern)
            writeNullableString(dos, rule.token)
        }

        // page exceptions
        writeStringSet(dos, pageAllowDocument)
        writeStringSet(dos, pageGenerichide)
        writeStringSet(dos, pageElemhide)
        writeStringSet(dos, pageSpecifichide)

        // cosmetic rules
        dos.writeInt(domainCosmeticRules.size)
        for (r in domainCosmeticRules) {
            writeString(dos, r.selector)
            writeStringList(dos, r.domains)
        }
        dos.writeInt(domainCosmeticExceptions.size)
        for (r in domainCosmeticExceptions) {
            writeString(dos, r.selector)
            writeStringList(dos, r.domains)
        }

        writeMapOfLists(dos, genericByClass)
        writeMapOfLists(dos, genericById)
        writeStringList(dos, lowlyGenericSelectors)
        writeStringSet(dos, genericExceptions)

        // scriptlets
        dos.writeInt(scriptletRules.size)
        for (s in scriptletRules) {
            writeString(dos, s.name)
            writeStringList(dos, s.args)
            writeStringList(dos, s.domains)
        }
        dos.writeInt(scriptletExceptions.size)
        for (e in scriptletExceptions) {
            writeNullableString(dos, e.name)
            writeStringList(dos, e.domains)
        }

        dos.flush()
    }

    companion object {
        private const val MAGIC = 0x464D4859 // "FMHY"
        private const val VERSION = 1

        fun readFrom(input: InputStream): FilterEngine {
            val dis = DataInputStream(input)
            val magic = dis.readInt()
            if (magic != MAGIC) throw IllegalArgumentException("Invalid magic header: $magic")
            val version = dis.readInt()
            if (version != VERSION) throw IllegalArgumentException("Unsupported version: $version")

            val networkRulesCount = dis.readInt()
            val cosmeticRulesCount = dis.readInt()
            val scriptletRulesCount = dis.readInt()
            val ignoredSize = dis.readInt()
            val ignored = HashMap<String, Int>(ignoredSize)
            for (i in 0 until ignoredSize) {
                val k = readString(dis)
                val v = dis.readInt()
                ignored[k] = v
            }
            val stats = EngineStats(networkRulesCount, cosmeticRulesCount, scriptletRulesCount, ignored)

            val netRulesSize = dis.readInt()
            val allNetworkRules = ArrayList<NetworkRule>(netRulesSize)
            for (i in 0 until netRulesSize) {
                val id = dis.readInt()
                val rawRule = readString(dis)
                val isException = dis.readBoolean()
                val isImportant = dis.readBoolean()
                val party = dis.readByte().toInt()
                val typesMask = dis.readInt()
                val redirectResource = readNullableString(dis)
                val isRedirectRule = dis.readBoolean()
                val blocksDocument = dis.readBoolean()
                val caseSensitive = dis.readBoolean()
                val domains = readStringList(dis)
                val toDomains = readStringList(dis)
                val denyAllowDomains = readStringList(dis)
                val isPureHost = dis.readBoolean()
                val pureHost = readNullableString(dis)
                val isRegex = dis.readBoolean()
                val regexPattern = readNullableString(dis)
                val pattern = readString(dis)
                val token = readNullableString(dis)

                val rule = NetworkRule(
                    id, rawRule, isException, isImportant, party, typesMask,
                    redirectResource, isRedirectRule, blocksDocument, caseSensitive,
                    domains, toDomains, denyAllowDomains, isPureHost, pureHost,
                    isRegex, regexPattern, pattern, token
                )
                compileRuleRegex(rule)
                allNetworkRules.add(rule)
            }

            val pageAllowDocument = readStringSet(dis)
            val pageGenerichide = readStringSet(dis)
            val pageElemhide = readStringSet(dis)
            val pageSpecifichide = readStringSet(dis)

            val domCosSize = dis.readInt()
            val domainCosmeticRules = ArrayList<CosmeticRule>(domCosSize)
            for (i in 0 until domCosSize) {
                val sel = readString(dis)
                val doms = readStringList(dis)
                domainCosmeticRules.add(CosmeticRule(doms, sel))
            }
            val domExcSize = dis.readInt()
            val domainCosmeticExceptions = ArrayList<CosmeticRule>(domExcSize)
            for (i in 0 until domExcSize) {
                val sel = readString(dis)
                val doms = readStringList(dis)
                domainCosmeticExceptions.add(CosmeticRule(doms, sel))
            }

            val genericByClass = readMapOfLists(dis)
            val genericById = readMapOfLists(dis)
            val lowlyGenericSelectors = readStringList(dis)
            val genericExceptions = readStringSet(dis)

            val scSize = dis.readInt()
            val scriptletRules = ArrayList<ScriptletRule>(scSize)
            for (i in 0 until scSize) {
                val name = readString(dis)
                val args = readStringList(dis)
                val doms = readStringList(dis)
                scriptletRules.add(ScriptletRule(doms, name, args))
            }
            val scExcSize = dis.readInt()
            val scriptletExceptions = ArrayList<ScriptletException>(scExcSize)
            for (i in 0 until scExcSize) {
                val name = readNullableString(dis)
                val doms = readStringList(dis)
                scriptletExceptions.add(ScriptletException(doms, name))
            }

            return assembleEngine(
                stats, allNetworkRules, pageAllowDocument, pageGenerichide, pageElemhide, pageSpecifichide,
                domainCosmeticRules, domainCosmeticExceptions, genericByClass, genericById,
                lowlyGenericSelectors, genericExceptions, scriptletRules, scriptletExceptions
            )
        }

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

        private fun writeString(dos: DataOutputStream, s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            dos.writeInt(bytes.size)
            dos.write(bytes)
        }

        private fun readString(dis: DataInputStream): String {
            val len = dis.readInt()
            val bytes = ByteArray(len)
            dis.readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        private fun writeNullableString(dos: DataOutputStream, s: String?) {
            if (s == null) {
                dos.writeBoolean(false)
            } else {
                dos.writeBoolean(true)
                writeString(dos, s)
            }
        }

        private fun readNullableString(dis: DataInputStream): String? {
            return if (dis.readBoolean()) readString(dis) else null
        }

        private fun writeStringList(dos: DataOutputStream, list: List<String>) {
            dos.writeInt(list.size)
            for (s in list) {
                writeString(dos, s)
            }
        }

        private fun readStringList(dis: DataInputStream): List<String> {
            val size = dis.readInt()
            val list = ArrayList<String>(size)
            for (i in 0 until size) {
                list.add(readString(dis))
            }
            return list
        }

        private fun writeStringSet(dos: DataOutputStream, set: Set<String>) {
            dos.writeInt(set.size)
            for (s in set) {
                writeString(dos, s)
            }
        }

        private fun readStringSet(dis: DataInputStream): Set<String> {
            val size = dis.readInt()
            val set = HashSet<String>(size)
            for (i in 0 until size) {
                set.add(readString(dis))
            }
            return set
        }

        private fun writeMapOfLists(dos: DataOutputStream, map: Map<String, List<String>>) {
            dos.writeInt(map.size)
            for ((k, list) in map) {
                writeString(dos, k)
                writeStringList(dos, list)
            }
        }

        private fun readMapOfLists(dis: DataInputStream): Map<String, List<String>> {
            val size = dis.readInt()
            val map = HashMap<String, List<String>>(size)
            for (i in 0 until size) {
                val k = readString(dis)
                val list = readStringList(dis)
                map[k] = list
            }
            return map
        }
    }
}

class FilterEngineBuilder {
    private val rawNetworkRules = ArrayList<RawNetworkRule>()
    private val badfilteredKeys = HashSet<String>()
    private val ignoredCounts = HashMap<String, Int>()

    private val domainCosmeticRules = ArrayList<CosmeticRule>()
    private val domainCosmeticExceptions = ArrayList<CosmeticRule>()
    private val genericByClass = HashMap<String, MutableList<String>>()
    private val genericById = HashMap<String, MutableList<String>>()
    private val lowlyGenericSelectors = ArrayList<String>()
    private val genericExceptions = HashSet<String>()

    private val scriptletRules = ArrayList<ScriptletRule>()
    private val scriptletExceptions = ArrayList<ScriptletException>()

    private val pageAllowDocument = HashSet<String>()
    private val pageGenerichide = HashSet<String>()
    private val pageElemhide = HashSet<String>()
    private val pageSpecifichide = HashSet<String>()

    private var nextRuleId = 1

    private data class RawNetworkRule(
        val rule: NetworkRule,
        val isBadFilter: Boolean
    )

    fun addList(listId: String, text: String) {
        parseText(listId, text)
    }

    fun addCustomRules(text: String) {
        parseText("custom", text)
    }

    private fun recordIgnored(reason: String) {
        ignoredCounts[reason] = (ignoredCounts[reason] ?: 0) + 1
    }

    private fun parseText(listId: String, text: String) {
        val isFmhyList = listId.startsWith("fmhy-")

        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("!") || trimmed.startsWith("[Adblock")) continue
            if (trimmed == "#" || trimmed.startsWith("# ")) continue

            // 1. Hosts file line
            if (trimmed.startsWith("0.0.0.0 ") || trimmed.startsWith("127.0.0.1 ")) {
                val parts = trimmed.split(Regex("\\s+"))
                val host = parts.getOrNull(1)?.trim() ?: continue
                if (host.equals("localhost", ignoreCase = true) ||
                    host == "0.0.0.0" || host == "127.0.0.1" ||
                    host.equals("broadcasthost", ignoreCase = true) ||
                    host.isEmpty()
                ) {
                    continue
                }
                addPureHostRule("||$host^", blocksDocument = true, raw = trimmed)
                continue
            }

            // 2. Plain domain line (no /, *, ^, |, $, #, contains a dot)
            if (!trimmed.contains('/') && !trimmed.contains('*') && !trimmed.contains('^') &&
                !trimmed.contains('|') && !trimmed.contains('$') && !trimmed.contains('#') &&
                trimmed.contains('.')
            ) {
                if (trimmed.equals("localhost", ignoreCase = true) ||
                    trimmed == "0.0.0.0" || trimmed == "127.0.0.1"
                ) {
                    continue
                }
                addPureHostRule("||$trimmed^", blocksDocument = true, raw = trimmed)
                continue
            }

            // 3. AdGuard CSS injection (#$#) -> ignored
            if (trimmed.contains("#$#")) {
                recordIgnored("adguard-css")
                continue
            }

            // 4. HTML filters (##^ or ##^responseheader) -> ignored
            if (trimmed.contains("##^")) {
                recordIgnored("html-filter")
                continue
            }

            // 5. Procedural operator check or #?# -> ignored
            if (trimmed.contains("#?#")) {
                recordIgnored("procedural")
                continue
            }
            if (isProcedural(trimmed)) {
                recordIgnored("procedural")
                continue
            }

            // 6. Scriptlets (##+js(...) or #@#+js(...))
            if (trimmed.contains("##+js(") || trimmed.contains("#@#+js(")) {
                parseScriptlet(trimmed)
                continue
            }

            // 7. Cosmetic rules (## or #@#)
            if (trimmed.contains("##") || trimmed.contains("#@#")) {
                parseCosmetic(trimmed)
                continue
            }

            // 8. Network rule
            parseNetworkRule(trimmed, isFmhyList)
        }
    }

    private fun isProcedural(line: String): Boolean {
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

    private fun addPureHostRule(pattern: String, blocksDocument: Boolean, raw: String) {
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
        rawNetworkRules.add(RawNetworkRule(rule, isBadFilter = false))
    }

    private fun parseScriptlet(line: String) {
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

    private fun splitScriptletArgs(content: String): List<String> {
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

    private fun normalizeScriptletName(rawName: String): String {
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

    private fun parseCosmetic(line: String) {
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

    private fun addGenericSelector(selector: String) {
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

    private fun extractCssToken(s: String): String {
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

    private fun parseNetworkRule(line: String, isFmhyList: Boolean) {
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
            rawNetworkRules.add(RawNetworkRule(rule, isBadFilter = false))
        }
    }

    private fun extractPageHosts(pattern: String, domains: List<String>): List<String> {
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

    private fun extractLongestToken(pattern: String): String? {
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

    fun build(): FilterEngine {
        val filteredNetworkRules = ArrayList<NetworkRule>(rawNetworkRules.size)
        for (raw in rawNetworkRules) {
            if (raw.isBadFilter) continue
            if (raw.rule.canonicalKey() in badfilteredKeys) continue
            FilterEngine.compileRuleRegex(raw.rule)
            filteredNetworkRules.add(raw.rule)
        }

        val cosmeticCount = domainCosmeticRules.size + genericByClass.values.sumOf { it.size } +
                genericById.values.sumOf { it.size } + lowlyGenericSelectors.size
        val scriptletCount = scriptletRules.size

        val stats = EngineStats(
            networkRules = filteredNetworkRules.size,
            cosmeticRules = cosmeticCount,
            scriptletRules = scriptletCount,
            ignored = ignoredCounts.toMap()
        )

        return FilterEngine.assembleEngine(
            stats,
            filteredNetworkRules,
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
