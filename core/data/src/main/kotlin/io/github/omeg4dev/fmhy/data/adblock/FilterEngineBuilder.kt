package io.github.omeg4dev.fmhy.data.adblock

class FilterEngineBuilder {
    internal val rawNetworkRules = ArrayList<RawNetworkRule>()
    internal val badfilteredKeys = HashSet<String>()
    private val ignoredCounts = HashMap<String, Int>()

    internal val domainCosmeticRules = ArrayList<CosmeticRule>()
    internal val domainCosmeticExceptions = ArrayList<CosmeticRule>()
    internal val genericByClass = HashMap<String, MutableList<String>>()
    internal val genericById = HashMap<String, MutableList<String>>()
    internal val lowlyGenericSelectors = ArrayList<String>()
    internal val genericExceptions = HashSet<String>()

    internal val scriptletRules = ArrayList<ScriptletRule>()
    internal val scriptletExceptions = ArrayList<ScriptletException>()

    internal val pageAllowDocument = HashSet<String>()
    internal val pageGenerichide = HashSet<String>()
    internal val pageElemhide = HashSet<String>()
    internal val pageSpecifichide = HashSet<String>()

    internal var nextRuleId = 1

    internal data class RawNetworkRule(
        val rule: NetworkRule,
        val isBadFilter: Boolean
    )

    fun addList(listId: String, text: String) {
        parseText(listId, text)
    }

    fun addCustomRules(text: String) {
        parseText("custom", text)
    }

    internal fun recordIgnored(reason: String) {
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
