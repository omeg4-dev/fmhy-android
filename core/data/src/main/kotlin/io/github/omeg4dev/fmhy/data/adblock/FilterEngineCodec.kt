package io.github.omeg4dev.fmhy.data.adblock

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

internal object FilterEngineCodec {
    private const val MAGIC = 0x464D4859 // "FMHY"
    private const val VERSION = 1

    fun write(engine: FilterEngine, out: OutputStream) {
        val dos = DataOutputStream(out)
        dos.writeInt(MAGIC)
        dos.writeInt(VERSION)

        // stats
        dos.writeInt(engine.stats.networkRules)
        dos.writeInt(engine.stats.cosmeticRules)
        dos.writeInt(engine.stats.scriptletRules)
        dos.writeInt(engine.stats.ignored.size)
        for ((k, v) in engine.stats.ignored) {
            writeString(dos, k)
            dos.writeInt(v)
        }

        // network rules
        dos.writeInt(engine.allNetworkRules.size)
        for (rule in engine.allNetworkRules) {
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
        writeStringSet(dos, engine.pageAllowDocument)
        writeStringSet(dos, engine.pageGenerichide)
        writeStringSet(dos, engine.pageElemhide)
        writeStringSet(dos, engine.pageSpecifichide)

        // cosmetic rules
        dos.writeInt(engine.domainCosmeticRules.size)
        for (r in engine.domainCosmeticRules) {
            writeString(dos, r.selector)
            writeStringList(dos, r.domains)
        }
        dos.writeInt(engine.domainCosmeticExceptions.size)
        for (r in engine.domainCosmeticExceptions) {
            writeString(dos, r.selector)
            writeStringList(dos, r.domains)
        }

        writeMapOfLists(dos, engine.genericByClass)
        writeMapOfLists(dos, engine.genericById)
        writeStringList(dos, engine.lowlyGenericSelectors)
        writeStringSet(dos, engine.genericExceptions)

        // scriptlets
        dos.writeInt(engine.scriptletRules.size)
        for (s in engine.scriptletRules) {
            writeString(dos, s.name)
            writeStringList(dos, s.args)
            writeStringList(dos, s.domains)
        }
        dos.writeInt(engine.scriptletExceptions.size)
        for (e in engine.scriptletExceptions) {
            writeNullableString(dos, e.name)
            writeStringList(dos, e.domains)
        }

        dos.flush()
    }

    fun read(input: InputStream): FilterEngine {
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
            FilterEngine.compileRuleRegex(rule)
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

        return FilterEngine.assembleEngine(
            stats, allNetworkRules, pageAllowDocument, pageGenerichide, pageElemhide, pageSpecifichide,
            domainCosmeticRules, domainCosmeticExceptions, genericByClass, genericById,
            lowlyGenericSelectors, genericExceptions, scriptletRules, scriptletExceptions
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
